from __future__ import annotations

import json
import logging
import threading
import uuid
from pathlib import Path

from .audio_split import AudioSplitCancelled, AudioSplitService
from .schemas import (
    AudioClipResult,
    AudioSplitJobStatus,
    AudioSplitResult,
    AudioSplitSettings,
)
from .storage import Storage

logger = logging.getLogger(__name__)

_STAGE_WEIGHTS: dict[str, tuple[float, float]] = {
    "extract": (0.0, 5.0),
    "separate": (5.0, 55.0),
    "vad": (55.0, 65.0),
    "split": (65.0, 75.0),
    "beats": (75.0, 85.0),
    "export": (85.0, 100.0),
}


class _Job:
    def __init__(self, job_id: str, project_id: str) -> None:
        self.job_id = job_id
        self.project_id = project_id
        self.status: str = "queued"
        self.stage: str | None = None
        self.progress: float = 0.0
        self.message: str = ""
        self.error: str | None = None
        self.clips: list[AudioClipResult] = []
        self.cancel_event = threading.Event()
        self.lock = threading.Lock()

    def snapshot(self) -> AudioSplitJobStatus:
        with self.lock:
            return AudioSplitJobStatus(
                project_id=self.project_id,
                job_id=self.job_id,
                status=self.status,  # type: ignore[arg-type]
                stage=self.stage,  # type: ignore[arg-type]
                progress=round(self.progress, 1),
                message=self.message,
                error=self.error,
                clips=list(self.clips),
            )


class AudioJobManager:
    def __init__(self, service: AudioSplitService, storage: Storage) -> None:
        self.service = service
        self.storage = storage
        self._jobs: dict[str, _Job] = {}
        self._jobs_lock = threading.Lock()
        self._active_by_project: dict[str, str] = {}

    def _result_file(self, project_id: str) -> Path:
        return self.storage.projects / f"{project_id}_audio.json"

    def start(self, project_id: str, settings: AudioSplitSettings) -> _Job:
        paths, metadata, _ = self.storage.load_project(project_id)
        if not metadata.audio_codec:
            raise ValueError("This project's media has no audio stream; audio split is not available.")
        with self._jobs_lock:
            active_id = self._active_by_project.get(project_id)
            if active_id:
                active = self._jobs.get(active_id)
                if active and active.status in {"queued", "running"}:
                    raise ValueError("An audio split job is already running for this project.")
            job = _Job(uuid.uuid4().hex, project_id)
            self._jobs[job.job_id] = job
            self._active_by_project[project_id] = job.job_id
        thread = threading.Thread(
            target=self._run,
            args=(job, str(paths.original_file), settings),
            name=f"audio-split-{job.job_id[:8]}",
            daemon=True,
        )
        thread.start()
        return job

    def status(self, project_id: str, job_id: str) -> AudioSplitJobStatus:
        job = self._jobs.get(job_id)
        if job is None or job.project_id != project_id:
            raise FileNotFoundError(f"Audio split job '{job_id}' not found for project '{project_id}'")
        return job.snapshot()

    def cancel(self, project_id: str, job_id: str) -> AudioSplitJobStatus:
        job = self._jobs.get(job_id)
        if job is None or job.project_id != project_id:
            raise FileNotFoundError(f"Audio split job '{job_id}' not found for project '{project_id}'")
        job.cancel_event.set()
        return job.snapshot()

    def latest_result(self, project_id: str) -> AudioSplitResult | None:
        with self._jobs_lock:
            active_id = self._active_by_project.get(project_id)
        if active_id:
            job = self._jobs.get(active_id)
            if job and job.status == "done":
                snapshot = job.snapshot()
                return AudioSplitResult(project_id=project_id, clips=snapshot.clips)
        result_file = self._result_file(project_id)
        if not result_file.exists():
            return None
        payload = json.loads(result_file.read_text(encoding="utf-8"))
        return AudioSplitResult.model_validate(payload)

    def _persist_result(self, job: _Job) -> None:
        result = AudioSplitResult(project_id=job.project_id, clips=list(job.clips))
        self._result_file(job.project_id).write_text(
            json.dumps(result.model_dump(), indent=2),
            encoding="utf-8",
        )

    def _run(self, job: _Job, source_file: str, settings: AudioSplitSettings) -> None:
        def on_stage(stage: str, stage_progress: float, message: str) -> None:
            low, high = _STAGE_WEIGHTS.get(stage, (0.0, 100.0))
            overall = low + (high - low) * min(100.0, max(0.0, stage_progress)) / 100.0
            with job.lock:
                job.stage = stage  # type: ignore[assignment]
                job.progress = overall
                if message:
                    job.message = message

        with job.lock:
            job.status = "running"
        try:
            clips = self.service.run_pipeline(
                Path(source_file),
                job.project_id,
                settings,
                on_stage=on_stage,
                cancel_event=job.cancel_event,
            )
        except AudioSplitCancelled:
            with job.lock:
                job.status = "cancelled"
                job.message = "Audio split cancelled"
            logger.info("Audio split job %s cancelled", job.job_id)
            return
        except Exception as exc:
            logger.exception("Audio split job %s failed", job.job_id)
            with job.lock:
                job.status = "error"
                job.error = str(exc)
            return
        with job.lock:
            job.status = "done"
            job.progress = 100.0
            job.message = f"Exported {len(clips)} audio clips"
            job.clips = list(clips)
        try:
            self._persist_result(job)
        except OSError:
            logger.exception("Failed to persist audio split result for job %s", job.job_id)
