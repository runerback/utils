from __future__ import annotations

import json
import uuid
from datetime import datetime
from pathlib import Path

from .schemas import EditState, ProjectPaths, VideoMetadata


class Storage:
    def __init__(
        self,
        root: Path,
        export_dir: Path | None = None,
        work_dir: Path | None = None,
        uploads_dir: Path | None = None,
    ) -> None:
        self.root = root
        if uploads_dir is None:
            self.uploads = root / "uploads"
        elif uploads_dir.is_absolute():
            self.uploads = uploads_dir
        else:
            self.uploads = root / uploads_dir
        if work_dir is None:
            self.work = root / "work"
        elif work_dir.is_absolute():
            self.work = work_dir
        else:
            self.work = root / work_dir
        if export_dir is None:
            self.exports = root / "exports"
        elif export_dir.is_absolute():
            self.exports = export_dir
        else:
            self.exports = root / export_dir
        self.projects = root / "projects"
        for directory in [self.uploads, self.work, self.exports, self.projects]:
            directory.mkdir(parents=True, exist_ok=True)

    def create_project_paths(self, original_name: str) -> ProjectPaths:
        project_id = uuid.uuid4().hex
        original_file = self.build_upload_file_path(project_id, original_name)
        preview_file = self.work / f"{project_id}_preview.mp4"
        export_file = self.build_export_file_path(".mp4")
        player_proxy_file = self.work / f"{project_id}_original_player.mp4"
        project_file = self.projects / f"{project_id}.json"
        return ProjectPaths(
            project_id=project_id,
            project_file=project_file,
            original_file=original_file,
            preview_file=preview_file,
            export_file=export_file,
            player_proxy_file=player_proxy_file,
        )

    def build_upload_file_path(self, project_id: str, original_name: str) -> Path:
        safe_name = Path(original_name).name.strip()
        if not safe_name:
            safe_name = "upload.mp4"
        suffix = Path(safe_name).suffix or ".mp4"
        stem = Path(safe_name).stem.strip() or "upload"
        filtered_stem = "".join(ch if ch.isalnum() or ch in ("-", "_") else "_" for ch in stem)
        filtered_stem = filtered_stem.strip("_") or "upload"
        filtered_stem = filtered_stem[:80]
        filename = f"{project_id}_{filtered_stem}{suffix}"
        return self.uploads / filename

    def create_project_paths_from_source(self, source_file: Path) -> ProjectPaths:
        project_id = uuid.uuid4().hex
        preview_file = self.work / f"{project_id}_preview.mp4"
        export_file = self.build_export_file_path(source_file.suffix or ".mp4")
        player_proxy_file = self.work / f"{project_id}_original_player.mp4"
        project_file = self.projects / f"{project_id}.json"
        return ProjectPaths(
            project_id=project_id,
            project_file=project_file,
            original_file=source_file,
            preview_file=preview_file,
            export_file=export_file,
            player_proxy_file=player_proxy_file,
        )

    def build_export_filename(self, suffix: str = ".mp4") -> str:
        normalized_suffix = suffix.strip() or ".mp4"
        if not normalized_suffix.startswith("."):
            normalized_suffix = f".{normalized_suffix}"
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
        return f"vae_{timestamp}{normalized_suffix}"

    def build_export_file_path(self, suffix: str = ".mp4") -> Path:
        normalized_suffix = suffix.strip() or ".mp4"
        if not normalized_suffix.startswith("."):
            normalized_suffix = f".{normalized_suffix}"
        filename = self.build_export_filename(normalized_suffix)
        export_file = self.exports / filename
        index = 1
        while export_file.exists():
            stem = Path(filename).stem
            export_file = self.exports / f"{stem}_{index}{normalized_suffix}"
            index += 1
        return export_file

    def _effective_export_file(self, stored_export_file: Path) -> Path:
        filename = stored_export_file.name.strip()
        if filename:
            return self.exports / filename
        return self.build_export_file_path(stored_export_file.suffix or ".mp4")

    def _resolve_original_file(
        self,
        project_id: str,
        stored_original_file: Path,
        preview_file: Path,
        payload: dict[str, object],
    ) -> Path:
        if stored_original_file.exists() and stored_original_file.is_file():
            return stored_original_file
        candidates: list[Path] = []
        fallback_value = payload.get("fallback_original_file")
        if isinstance(fallback_value, str) and fallback_value.strip():
            candidates.append(Path(fallback_value))
        suffix = stored_original_file.suffix or ".mp4"
        candidates.append(self.work / f"{project_id}_source_cache{suffix}")
        for upload_match in sorted(self.uploads.glob(f"{project_id}*")):
            candidates.append(upload_match)
        candidates.append(preview_file)
        for candidate in candidates:
            if candidate.exists() and candidate.is_file():
                return candidate
        raise FileNotFoundError(
            f"Original file '{stored_original_file}' is not available and no fallback media was found for project '{project_id}'"
        )

    def project_paths(self, project_id: str) -> ProjectPaths:
        project_file = self.projects / f"{project_id}.json"
        if not project_file.exists():
            raise FileNotFoundError(f"Project '{project_id}' not found")
        payload = json.loads(project_file.read_text(encoding="utf-8"))
        stored_export_file = Path(payload["export_file"])
        stored_original_file = Path(payload["original_file"])
        preview_file = Path(payload["preview_file"])
        player_proxy_file_value = str(payload.get("player_proxy_file") or "").strip()
        player_proxy_file = (
            Path(player_proxy_file_value)
            if player_proxy_file_value
            else self.work / f"{project_id}_original_player.mp4"
        )
        original_file = self._resolve_original_file(project_id, stored_original_file, preview_file, payload)
        return ProjectPaths(
            project_id=project_id,
            project_file=project_file,
            original_file=original_file,
            preview_file=preview_file,
            export_file=self._effective_export_file(stored_export_file),
            player_proxy_file=player_proxy_file,
        )

    def save_project(self, paths: ProjectPaths, metadata: VideoMetadata, state: EditState) -> None:
        payload = {
            "project_id": paths.project_id,
            "metadata": metadata.model_dump(),
            "state": state.model_dump(),
            "original_file": str(paths.original_file),
            "preview_file": str(paths.preview_file),
            "export_file": str(paths.export_file),
            "player_proxy_file": str(paths.player_proxy_file),
        }
        paths.project_file.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    def load_project(self, project_id: str) -> tuple[ProjectPaths, VideoMetadata, EditState]:
        paths = self.project_paths(project_id)
        payload = json.loads(paths.project_file.read_text(encoding="utf-8"))
        metadata = VideoMetadata.model_validate(payload["metadata"])
        state = EditState.model_validate(payload["state"])
        return paths, metadata, state

    def list_projects(self) -> list[dict[str, object]]:
        projects: list[dict[str, object]] = []
        for file in sorted(self.projects.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
            payload = json.loads(file.read_text(encoding="utf-8"))
            project_id = str(payload["project_id"])
            preview_file = Path(payload["preview_file"])
            export_file = self._effective_export_file(Path(payload["export_file"]))
            projects.append(
                {
                    "project_id": project_id,
                    "original_url": f"/api/projects/{project_id}/original",
                    "has_preview": preview_file.exists(),
                    "has_export": export_file.exists(),
                }
            )
        return projects

