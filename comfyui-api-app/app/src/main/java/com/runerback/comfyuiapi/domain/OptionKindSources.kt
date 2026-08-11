package com.runerback.comfyuiapi.domain

val OPTION_KIND_SOURCES: Map<String, Pair<String, String>> = mapOf(
    "lora" to ("LoraLoader" to "lora_name"),
    "checkpoint" to ("CheckpointLoaderSimple" to "ckpt_name"),
    "vae" to ("VAELoader" to "vae_name"),
    "sampler" to ("KSampler" to "sampler_name"),
    "scheduler" to ("KSampler" to "scheduler_name")
)

fun resolveOptionSource(optionKind: String): Pair<String, String>? {
    return OPTION_KIND_SOURCES[optionKind]
}
