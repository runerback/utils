from __future__ import annotations

import logging
import queue
import threading
import time
import uuid
from collections import OrderedDict
from dataclasses import dataclass, field
from typing import Callable, Optional

logger = logging.getLogger(__name__)

MAX_RETAINED_JOBS = 200


@dataclass
class Job:
    job_id: str
    kind: str
    project_id: str
    status: str = "queued"
    result: Optional[dict] = None
    error: Optional[str] = None
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)


class JobRunner:
    """Serial background runner for long ffmpeg renders.

    A single worker thread keeps renders sequential so concurrent requests do
    not oversubscribe CPU on the host.
    """

    def __init__(self) -> None:
        self._jobs: OrderedDict[str, Job] = OrderedDict()
        self._lock = threading.Lock()
        self._queue: queue.Queue[tuple[Job, Callable[[], object]]] = queue.Queue()
        self._worker = threading.Thread(target=self._run, name="vae-job-runner", daemon=True)
        self._worker.start()

    def submit(self, kind: str, project_id: str, fn: Callable[[], object]) -> Job:
        job = Job(job_id=uuid.uuid4().hex, kind=kind, project_id=project_id)
        with self._lock:
            self._jobs[job.job_id] = job
            self._trim_locked()
        self._queue.put((job, fn))
        logger.info("Queued job %s (kind=%s, project=%s)", job.job_id, kind, project_id)
        return job

    def get(self, job_id: str) -> Optional[Job]:
        with self._lock:
            return self._jobs.get(job_id)

    def _trim_locked(self) -> None:
        while len(self._jobs) > MAX_RETAINED_JOBS:
            evicted_id, _ = self._jobs.popitem(last=False)
            logger.debug("Evicted job %s from history", evicted_id)

    def _update(self, job: Job, status: str, result: object = None, error: Optional[str] = None) -> None:
        with self._lock:
            job.status = status
            if result is not None:
                job.result = result  # type: ignore[assignment]
            if error is not None:
                job.error = error
            job.updated_at = time.time()

    def _run(self) -> None:
        while True:
            job, fn = self._queue.get()
            self._update(job, "running")
            try:
                result = fn()
            except Exception as exc:  # record any failure on the job
                logger.exception("Job %s failed", job.job_id)
                self._update(job, "error", error=str(exc))
            else:
                payload = result.model_dump(mode="json") if hasattr(result, "model_dump") else result
                self._update(job, "done", result=payload)
