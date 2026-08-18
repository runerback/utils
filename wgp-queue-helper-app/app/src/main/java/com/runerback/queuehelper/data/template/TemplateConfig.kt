package com.runerback.queuehelper.data.template

sealed class VideoLengthRule {
    /**
     * video_length = min(audioTrimDurationSeconds * [multiplier], [max])
     */
    data class AudioDurationMultiplier(
        val multiplier: Int = 24,
        val max: Int = 360
    ) : VideoLengthRule()
}

data class TemplateConfig(
    val modelType: String,
    val displayName: String,
    val videoLengthRule: VideoLengthRule = VideoLengthRule.AudioDurationMultiplier()
)

val SupportedTemplates: List<TemplateConfig> = listOf(
    TemplateConfig(
        modelType = "minimax_h3_ref2va_pruned",
        displayName = "minimax h3 ref2va pruned",
        videoLengthRule = VideoLengthRule.AudioDurationMultiplier(multiplier = 24, max = 360)
    )
)
