from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Annotated, Literal, Optional

from pydantic import BaseModel, Field, model_validator


PositiveClipIndex = Annotated[int, Field(ge=1)]
QuarterTurnCount = Annotated[int, Field(ge=0, le=3)]


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


class SceneSplitState(BaseModel):
    enabled: bool = False
    detector: Literal["ffmpeg", "ai"] = "ffmpeg"
    # FFmpeg scene scores are normalized to [0, 1]; common defaults are around 0.3-0.5.
    threshold: float = Field(default=0.4, gt=0, le=1)
    ai_sensitivity: float = Field(default=0.5, gt=0, le=1)
    min_clip_length: float = Field(default=2.0, gt=0)
    max_clip_length: float = Field(default=12.0, gt=0)
    selected_clip_indexes: list[PositiveClipIndex] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_lengths(self) -> "SceneSplitState":
        if self.min_clip_length > self.max_clip_length:
            raise ValueError("scene_split.min_clip_length must be less than or equal to scene_split.max_clip_length")
        self.selected_clip_indexes = sorted(set(self.selected_clip_indexes))
        return self


class RotationState(BaseModel):
    quarter_turns: QuarterTurnCount = 0


class EditState(BaseModel):
    trim: TrimState = Field(default_factory=TrimState)
    crop: CropState = Field(default_factory=CropState)
    rotation: RotationState = Field(default_factory=RotationState)
    scene_split: SceneSplitState = Field(default_factory=SceneSplitState)
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


class RenderPart(BaseModel):
    index: int = Field(ge=1)
    start: float = Field(ge=0)
    end: float = Field(gt=0)
    output_url: str
    output_path: Optional[str] = None


class RenderResponse(BaseModel):
    project_id: str
    output_url: Optional[str] = None
    output_path: Optional[str] = None
    parts: list[RenderPart] = Field(default_factory=list)


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

