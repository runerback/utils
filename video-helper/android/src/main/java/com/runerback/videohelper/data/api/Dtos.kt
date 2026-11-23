package com.runerback.videohelper.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadataDto(
    val width: Int,
    val height: Int,
    val duration: Double,
    val fps: Double,
    @SerialName("frame_count") val frameCount: Int,
    @SerialName("video_codec") val videoCodec: String = "unknown",
    @SerialName("audio_codec") val audioCodec: String? = null,
    @SerialName("container_format") val containerFormat: String = "unknown",
)

@Serializable
data class TrimStateDto(
    val start: Double = 0.0,
    val end: Double = 0.0,
)

@Serializable
data class CropStateDto(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val preset: String? = null,
)

@Serializable
data class RotationStateDto(
    @SerialName("quarter_turns") val quarterTurns: Int = 0,
)

@Serializable
data class FreezeFrameStateDto(
    val enabled: Boolean = false,
    val timestamp: Double = 0.0,
    val duration: Double = 1.0,
)

@Serializable
data class SceneSplitStateDto(
    val enabled: Boolean = false,
    val detector: String = "ffmpeg", // "ffmpeg" | "ai"
    val threshold: Double = 0.4,
    @SerialName("ai_sensitivity") val aiSensitivity: Double = 0.5,
    @SerialName("min_clip_length") val minClipLength: Double = 2.0,
    @SerialName("max_clip_length") val maxClipLength: Double = 12.0,
    @SerialName("fixed_length_enabled") val fixedLengthEnabled: Boolean = false,
    @SerialName("fixed_clip_length") val fixedClipLength: Double = 12.0,
    @SerialName("selected_clip_indexes") val selectedClipIndexes: List<Int> = emptyList(),
)

@Serializable
data class EditStateDto(
    val trim: TrimStateDto = TrimStateDto(),
    val crop: CropStateDto = CropStateDto(),
    val rotation: RotationStateDto = RotationStateDto(),
    @SerialName("scene_split") val sceneSplit: SceneSplitStateDto = SceneSplitStateDto(),
    @SerialName("freeze_frame") val freezeFrame: FreezeFrameStateDto = FreezeFrameStateDto(),
    @SerialName("crop_enabled") val cropEnabled: Boolean = false,
    @SerialName("resize_max") val resizeMax: Int? = null,
    val speed: Double = 1.0,
)

@Serializable
data class ProjectCreateResponseDto(
    @SerialName("project_id") val projectId: String,
    val metadata: VideoMetadataDto,
    val state: EditStateDto,
    @SerialName("original_url") val originalUrl: String,
    @SerialName("original_uses_proxy") val originalUsesProxy: Boolean = false,
)

@Serializable
data class ProjectStateResponseDto(
    @SerialName("project_id") val projectId: String,
    val metadata: VideoMetadataDto,
    val state: EditStateDto,
    @SerialName("original_url") val originalUrl: String,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("original_uses_proxy") val originalUsesProxy: Boolean = false,
)

@Serializable
data class ProjectListItemDto(
    @SerialName("project_id") val projectId: String,
    @SerialName("original_url") val originalUrl: String,
    @SerialName("has_preview") val hasPreview: Boolean,
    @SerialName("has_export") val hasExport: Boolean,
)

@Serializable
data class StateUpdateRequestDto(
    val state: EditStateDto,
)

@Serializable
data class RenderPartDto(
    val index: Int,
    val start: Double,
    val end: Double,
    @SerialName("output_url") val outputUrl: String,
    @SerialName("output_path") val outputPath: String? = null,
    @SerialName("output_size_bytes") val outputSizeBytes: Long? = null,
)

@Serializable
data class RenderResponseDto(
    @SerialName("project_id") val projectId: String,
    val format: String = "mp4",
    @SerialName("output_url") val outputUrl: String? = null,
    @SerialName("output_path") val outputPath: String? = null,
    @SerialName("output_size_bytes") val outputSizeBytes: Long? = null,
    @SerialName("total_output_size_bytes") val totalOutputSizeBytes: Long? = null,
    val parts: List<RenderPartDto> = emptyList(),
)

@Serializable
data class ExportEstimatePartDto(
    val index: Int,
    val start: Double,
    val end: Double,
    @SerialName("estimated_size_bytes") val estimatedSizeBytes: Long,
)

@Serializable
data class ExportEstimateResponseDto(
    @SerialName("project_id") val projectId: String,
    val format: String = "gif",
    @SerialName("estimated_size_bytes") val estimatedSizeBytes: Long,
    val parts: List<ExportEstimatePartDto> = emptyList(),
)

@Serializable
data class JobCreateResponseDto(
    @SerialName("job_id") val jobId: String,
    val status: String = "queued",
)

@Serializable
data class JobResponseDto(
    @SerialName("job_id") val jobId: String,
    val kind: String,
    @SerialName("project_id") val projectId: String,
    val status: String, // queued | running | done | error
    val result: RenderResponseDto? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: Double,
    @SerialName("updated_at") val updatedAt: Double,
)
