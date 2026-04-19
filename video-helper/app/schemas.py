from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from pydantic import BaseModel, Field, model_validator


class VideoMetadata(BaseModel):
    width: int = Field(gt=0)
    height: int = Field(gt=0)
    duration: float = Field(gt=0)
    fps: float = Field(gt=0)
    frame_count: int = Field(gt=0)
    video_codec: str = Field(default="unknown", min_length=1)
    audio_codec: Optional[str] = None
    container_format: str = Field(default="unknown", min_length=1)


class TrimState(BaseModel):
    start: float = Field(default=0, ge=0)
    end: float = Field(default=0, ge=0)

    @model_validator(mode="after")
    def validate_window(self) -> "TrimState":
        if self.end and self.end <= self.start:
            raise ValueError("trim.end must be greater than trim.start")
        return self


class CropState(BaseModel):
    x: int = Field(default=0, ge=0)
    y: int = Field(default=0, ge=0)
    width: int = Field(default=0, ge=0)
    height: int = Field(default=0, ge=0)
    preset: Optional[str] = None

    @model_validator(mode="after")
    def validate_dimensions(self) -> "CropState":
        if self.width and self.height == 0:
            raise ValueError("crop.height is required when crop.width is set")
        if self.height and self.width == 0:
            raise ValueError("crop.width is required when crop.height is set")
        return self


class EditState(BaseModel):
    trim: TrimState = Field(default_factory=TrimState)
    crop: CropState = Field(default_factory=CropState)
    crop_enabled: bool = False
    resize_max: Optional[int] = Field(default=None, gt=0)
    fps: Optional[float] = Field(default=None, gt=0)


class ProjectCreateResponse(BaseModel):
    project_id: str
    metadata: VideoMetadata
    state: EditState
    original_url: str
    original_uses_proxy: bool = False


class LocalProjectCreateRequest(BaseModel):
    source_path: str = Field(min_length=1)


class ProjectStateResponse(BaseModel):
    project_id: str
    metadata: VideoMetadata
    state: EditState
    original_url: str
    preview_url: Optional[str] = None
    original_uses_proxy: bool = False


class StateUpdateRequest(BaseModel):
    state: EditState


class RenderResponse(BaseModel):
    project_id: str
    output_url: str
    output_path: Optional[str] = None


class ProjectListItem(BaseModel):
    project_id: str
    original_url: str
    has_preview: bool
    has_export: bool


class TrimFramesResponse(BaseModel):
    start_url: str
    end_url: str


@dataclass
class ProjectPaths:
    project_id: str
    project_file: Path
    original_file: Path
    preview_file: Path
    export_file: Path
    player_proxy_file: Path

