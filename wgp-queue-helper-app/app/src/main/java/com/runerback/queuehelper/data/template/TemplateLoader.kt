package com.runerback.queuehelper.data.template

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TemplateLoader(context: Context) {

    private val assetManager = context.assets
    private val json = Json { ignoreUnknownKeys = true }
    private val payloads = mutableMapOf<String, JsonObject>()

    fun load(): Map<String, JsonObject> {
        if (payloads.isNotEmpty()) return payloads

        for (config in SupportedTemplates) {
            val path = "templates/${config.modelType}.template"
            val text = assetManager.open(path).bufferedReader().use { it.readText() }
            val obj = json.parseToJsonElement(text).jsonObject
            payloads[config.modelType] = obj
        }
        return payloads
    }

    fun basePayload(modelType: String): JsonObject {
        return requireNotNull(load()[modelType]) { "Unknown model type: $modelType" }
    }

    fun defaultName(modelType: String): String {
        return SupportedTemplates.find { it.modelType == modelType }?.displayName ?: modelType
    }

    fun config(modelType: String): TemplateConfig {
        return requireNotNull(SupportedTemplates.find { it.modelType == modelType }) {
            "Unknown model type: $modelType"
        }
    }

    companion object {
        fun modelTypeFromPayload(payload: JsonObject): String {
            return payload["params"]
                ?.jsonObject
                ?.get("model_type")
                ?.jsonPrimitive
                ?.content
                ?: ""
        }
    }
}
