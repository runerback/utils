package com.runerback.comfyuiapi.data.repository

import android.net.Uri
import com.runerback.comfyuiapi.data.datasource.ComfyApiDataSource
import com.runerback.comfyuiapi.data.datasource.ComfyEvent
import com.runerback.comfyuiapi.data.datasource.FileDataSource
import com.runerback.comfyuiapi.data.datasource.ObjectInfoCacheDataSource
import com.runerback.comfyuiapi.data.datasource.SettingsDataSource
import com.runerback.comfyuiapi.data.datasource.collectOutputRefs
import com.runerback.comfyuiapi.data.model.OutputKind
import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.GeneratedOutput
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.domain.SchemaParser
import com.runerback.comfyuiapi.domain.WorkflowPatcher
import com.runerback.comfyuiapi.domain.applyDefaultsToSchema
import com.runerback.comfyuiapi.domain.resolveValue
import com.runerback.comfyuiapi.ui.components.LogBuffer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class LoadResult<out T> {
    data class Success<T>(val value: T, val name: String) : LoadResult<T>()
    data class Error(val message: String) : LoadResult<Nothing>()
}

data class SchemaParseResult(
    val parameters: List<EditableParameter>,
    val schemaJson: JsonObject
)

sealed class GenerationResult {
    data object Connecting : GenerationResult()
    data class Running(val currentNode: String?, val progress: Pair<Int, Int>?) : GenerationResult()
    data class Preview(val image: androidx.compose.ui.graphics.ImageBitmap) : GenerationResult()
    data class Completed(val outputs: List<GeneratedOutput>) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

@Singleton
class ComfyRepository @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val apiDataSource: ComfyApiDataSource,
    private val settingsDataSource: SettingsDataSource,
    private val objectInfoCache: ObjectInfoCacheDataSource,
    private val schemaParser: SchemaParser,
    private val workflowPatcher: WorkflowPatcher,
    private val json: Json
) {

    val serverUrl = settingsDataSource.serverUrl
    val clientId = settingsDataSource.clientId
    val serverUrlHistory = settingsDataSource.serverUrlHistory
    val generationTimeoutMs = settingsDataSource.generationTimeoutMs

    suspend fun loadWorkflow(uri: Uri): LoadResult<Workflow> {
        LogBuffer.add("repository.loadWorkflow: $uri")
        val result = fileDataSource.loadJsonObject(uri)
        return if (result.isSuccess) {
            LogBuffer.add("repository.loadWorkflow: parsed json object")
            val workflowResult = result.getOrThrow().toWorkflow(json)
            val workflow = workflowResult.getOrNull()
            if (workflow != null) {
                LoadResult.Success(workflow, fileDataSource.displayName(uri))
            } else {
                val msg = workflowResult.exceptionOrNull()?.message ?: "Failed to parse workflow"
                LogBuffer.add("repository.loadWorkflow: $msg")
                LoadResult.Error(msg)
            }
        } else {
            LogBuffer.add("repository.loadWorkflow: ${result.exceptionOrNull()?.message}")
            LoadResult.Error(result.exceptionOrNull()?.message ?: "Failed to load workflow")
        }
    }

    fun parseWorkflow(jsonObject: JsonObject): Workflow {
        return jsonObject.toWorkflow(json).getOrThrow()
    }

    suspend fun loadSchema(uri: Uri, workflow: Workflow): LoadResult<SchemaParseResult> {
        LogBuffer.add("repository.loadSchema: $uri")
        val result = fileDataSource.loadJsonObject(uri)
        return if (result.isSuccess) {
            val schemaObj = result.getOrThrow()
            LogBuffer.add("repository.loadSchema: parsed schema, root keys=${schemaObj.keys.joinToString()}")
            val params = schemaParser.parse(schemaObj, workflow)
            LogBuffer.add("repository.loadSchema: ${params.size} parameters")
            params.forEach { LogBuffer.add("  param: ${it.nodeId}/${it.fieldName} path=${it.path.joinToString("/")} type=${it.type}") }
            LoadResult.Success(SchemaParseResult(params, schemaObj), fileDataSource.displayName(uri))
        } else {
            LogBuffer.add("repository.loadSchema: ${result.exceptionOrNull()?.message}")
            LoadResult.Error(result.exceptionOrNull()?.message ?: "Failed to load schema")
        }
    }

    suspend fun saveSchemaWithDefaults(
        uri: Uri,
        schemaJson: JsonObject,
        workflow: Workflow,
        parameters: List<EditableParameter>,
        currentValues: Map<ParameterKey, JsonElement>
    ): Result<Unit> {
        LogBuffer.add("repository.saveSchemaWithDefaults: $uri")
        val updated = applyDefaultsToSchema(schemaJson, parameters, workflow, currentValues)
        return fileDataSource.saveJsonObject(uri, updated)
    }

    suspend fun saveSchema(uri: Uri, schema: JsonObject): Result<Unit> {
        LogBuffer.add("repository.saveSchema: $uri")
        return fileDataSource.saveJsonObject(uri, schema)
    }

    suspend fun fetchObjectInfo(serverUrl: String, nodeClass: String): Result<JsonObject> {
        LogBuffer.add("repository.fetchObjectInfo: $serverUrl/object_info/$nodeClass")
        return apiDataSource.fetchObjectInfo(serverUrl, nodeClass)
    }

    suspend fun fetchObjectInfos(
        serverUrl: String,
        classTypes: Set<String>,
        forceRefresh: Boolean = false
    ): Result<Map<String, JsonObject>> {
        LogBuffer.add("repository.fetchObjectInfos: $serverUrl classes=${classTypes.size} forceRefresh=$forceRefresh")
        if (classTypes.isEmpty()) return Result.success(emptyMap())

        if (forceRefresh) {
            classTypes.forEach { objectInfoCache.clear(serverUrl, it) }
        }

        return try {
            val cached = mutableMapOf<String, JsonObject>()
            val missing = mutableSetOf<String>()
            for (classType in classTypes) {
                val fromCache = objectInfoCache.get(serverUrl, classType)
                if (fromCache != null) {
                    cached[classType] = fromCache
                } else {
                    missing.add(classType)
                }
            }

            if (missing.isEmpty()) {
                LogBuffer.add("repository.fetchObjectInfos: all ${cached.size} from cache")
                return Result.success(cached)
            }

            coroutineScope {
                val deferreds = missing.map { classType ->
                    async { classType to apiDataSource.fetchObjectInfo(serverUrl, classType) }
                }
                val results = deferreds.awaitAll()
                var failures = 0
                for ((classType, result) in results) {
                    if (result.isSuccess) {
                        val data = result.getOrThrow()
                        cached[classType] = data
                        objectInfoCache.put(serverUrl, classType, data)
                    } else {
                        failures++
                        LogBuffer.add("repository.fetchObjectInfos: failed $classType: ${result.exceptionOrNull()?.message}")
                    }
                }
                LogBuffer.add("repository.fetchObjectInfos: ${cached.size} total, $failures failures")
                Result.success(cached)
            }
        } catch (e: Exception) {
            LogBuffer.add("repository.fetchObjectInfos: exception ${e.message}")
            Result.failure(e)
        }
    }

    fun initialValues(workflow: Workflow, parameters: List<EditableParameter>): Map<ParameterKey, JsonElement> {
        return parameters.associate { param ->
            ParameterKey(param.nodeId, param.path) to (
                param.default ?: resolveValue(workflow, param.nodeId, param.path) ?: kotlinx.serialization.json.JsonNull
            )
        }
    }

    fun patchWorkflow(workflow: Workflow, values: Map<ParameterKey, JsonElement>): Workflow {
        return workflowPatcher.patch(workflow, values)
    }

    suspend fun saveServerUrl(url: String) {
        settingsDataSource.setServerUrl(url)
    }

    suspend fun addServerUrlToHistory(url: String) {
        settingsDataSource.addServerUrlToHistory(url)
    }

    suspend fun saveGenerationTimeoutMs(ms: Long) {
        settingsDataSource.setGenerationTimeoutMs(ms)
    }

    suspend fun uploadImage(serverUrl: String, uri: Uri, uploadType: String): Result<String> {
        LogBuffer.add("repository.uploadImage: $serverUrl type=$uploadType uri=$uri")
        val (name, bytes) = fileDataSource.loadBytes(uri).getOrElse {
            LogBuffer.add("repository.uploadImage: load bytes failed: ${it.message}")
            return Result.failure(it)
        }
        LogBuffer.add("repository.uploadImage: loaded $name, ${bytes.size} bytes")
        return apiDataSource.uploadImage(serverUrl, name, bytes, uploadType)
    }

    suspend fun cancelGeneration(serverUrl: String): Result<Unit> {
        LogBuffer.add("repository.cancelGeneration: $serverUrl")
        return apiDataSource.interrupt(serverUrl)
    }

    fun generate(serverUrl: String, workflow: Workflow): Flow<GenerationResult> = channelFlow {
        val clientId = settingsDataSource.ensureClientId()
        val promptId = UUID.randomUUID().toString()
        val timeoutMs = settingsDataSource.generationTimeoutMs.first()
        LogBuffer.add("repository.generate: clientId=$clientId promptId=$promptId timeoutMs=$timeoutMs")

        var lastProgress: Pair<Int, Int>? = null
        var lastNode: String? = null
        var completed = false
        var executionStarted = false

        coroutineScope {
            val listenJob = launch {
                try {
                    withTimeout(timeoutMs) {
                        apiDataSource.listen(serverUrl, clientId, promptId).collect { event ->
                            LogBuffer.add("repository.generate: event=$event")
                            when (event) {
                                is ComfyEvent.Connected -> send(GenerationResult.Connecting)
                                is ComfyEvent.Executing -> {
                                    executionStarted = true
                                    lastNode = event.nodeId
                                    send(GenerationResult.Running(lastNode, lastProgress))
                                }
                                is ComfyEvent.Progress -> {
                                    executionStarted = true
                                    lastProgress = event.value to event.max
                                    send(GenerationResult.Running(lastNode, lastProgress))
                                }
                                is ComfyEvent.Preview -> send(GenerationResult.Preview(event.bitmap))
                                is ComfyEvent.Executed -> Unit
                                is ComfyEvent.Success -> {
                                    completed = true
                                    val outputs = fetchOutputs(serverUrl, promptId)
                                    send(GenerationResult.Completed(outputs))
                                }
                                is ComfyEvent.Error -> send(GenerationResult.Error(event.message))
                                is ComfyEvent.Interrupted -> send(GenerationResult.Error("Interrupted"))
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    LogBuffer.add("repository.generate: listen timed out")
                } catch (e: Exception) {
                    LogBuffer.add("repository.generate: listen exception ${e.message}")
                    send(GenerationResult.Error(e.message ?: "Listen error"))
                }
            }

            delay(300)

            val queueResult = apiDataSource.queuePrompt(serverUrl, workflow, clientId, promptId)
            if (queueResult.isFailure) {
                val msg = queueResult.exceptionOrNull()?.message ?: "Failed to queue prompt"
                LogBuffer.add("repository.generate: queue failed: $msg")
                listenJob.cancel()
                send(GenerationResult.Error(msg))
                return@coroutineScope
            }
            LogBuffer.add("repository.generate: queued prompt")

            listenJob.join()

            when {
                completed -> Unit
                !executionStarted -> {
                    LogBuffer.add("repository.generate: timeout waiting for execution start")
                    val outputs = fetchOutputs(serverUrl, promptId)
                    if (outputs.isNotEmpty()) {
                        LogBuffer.add("repository.generate: timeout but found ${outputs.size} outputs in history")
                        send(GenerationResult.Completed(outputs))
                    } else {
                        send(GenerationResult.Error("Timed out waiting for ComfyUI to start execution. Check server logs."))
                    }
                }
                else -> {
                    LogBuffer.add("repository.generate: websocket ended without success, fetching history fallback")
                    val outputs = fetchOutputs(serverUrl, promptId)
                    send(GenerationResult.Completed(outputs))
                }
            }
        }
    }

    fun saveOutputToDownloads(output: GeneratedOutput): Result<Unit> {
        return when (output.kind) {
            OutputKind.Image -> {
                val bitmap = output.bitmap ?: return Result.failure(IllegalStateException("No bitmap"))
                fileDataSource.saveToDownloads(output.filename, bitmap)
            }
            OutputKind.Audio -> {
                val uri = output.audioUri ?: return Result.failure(IllegalStateException("No audio uri"))
                fileDataSource.saveToDownloads(output.filename, uri)
            }
        }
    }

    private suspend fun fetchOutputs(serverUrl: String, promptId: String): List<GeneratedOutput> {
        return try {
            LogBuffer.add("repository.fetchOutputs: $promptId")
            val historyResult = apiDataSource.fetchHistory(serverUrl, promptId)
            if (historyResult.isFailure) {
                LogBuffer.add("repository.fetchOutputs: history failed ${historyResult.exceptionOrNull()?.message}")
                return emptyList()
            }

            val history = historyResult.getOrNull() ?: return emptyList()
            val refs = history.collectOutputRefs(promptId)
            LogBuffer.add("repository.fetchOutputs: ${refs.size} output refs")

            val outputs = mutableListOf<GeneratedOutput>()
            for (ref in refs) {
                LogBuffer.add("repository.fetchOutputs: fetching ${ref.filename} kind=${ref.kind}")
                when (ref.kind) {
                    OutputKind.Image -> {
                        val imageResult = apiDataSource.fetchImage(serverUrl, ref)
                        imageResult.getOrNull()?.let { bitmap ->
                            outputs.add(GeneratedOutput(filename = ref.filename, kind = OutputKind.Image, bitmap = bitmap))
                        } ?: LogBuffer.add("repository.fetchOutputs: failed to fetch/decode ${ref.filename}")
                    }
                    OutputKind.Audio -> {
                        val bytesResult = apiDataSource.fetchOutputFile(serverUrl, ref)
                        val bytes = bytesResult.getOrNull()
                        if (bytes == null) {
                            LogBuffer.add("repository.fetchOutputs: failed to fetch audio ${ref.filename}")
                        } else {
                            val uriResult = fileDataSource.saveCachedOutput(ref.filename, bytes)
                            uriResult.getOrNull()?.let { uri ->
                                outputs.add(GeneratedOutput(filename = ref.filename, kind = OutputKind.Audio, audioUri = uri))
                            } ?: LogBuffer.add("repository.fetchOutputs: failed to cache audio ${ref.filename}")
                        }
                    }
                }
            }
            outputs
        } catch (e: Exception) {
            LogBuffer.add("repository.fetchOutputs: exception ${e.message}")
            emptyList()
        }
    }
}

private fun kotlinx.serialization.json.JsonObject.toWorkflow(json: Json): Result<Workflow> {
    val map = mutableMapOf<String, com.runerback.comfyuiapi.data.model.WorkflowNode>()
    val skipped = mutableListOf<String>()
    for ((key, value) in this) {
        if (value !is kotlinx.serialization.json.JsonObject) {
            LogBuffer.add("toWorkflow: skipping non-object key '$key'")
            skipped.add(key)
            continue
        }
        try {
            map[key] = json.decodeFromJsonElement(
                com.runerback.comfyuiapi.data.model.WorkflowNode.serializer(),
                value
            )
        } catch (e: SerializationException) {
            LogBuffer.add("toWorkflow: skipping invalid node '$key': ${e.message}")
            skipped.add(key)
        }
    }
    return if (map.isEmpty()) {
        val reason = buildString {
            append("No valid workflow nodes found. ")
            append("Make sure the file is a ComfyUI API/prompt JSON (not the graph editor format).")
            if (skipped.isNotEmpty()) {
                append(" Skipped keys: ${skipped.joinToString()}.")
            }
        }
        Result.failure(IllegalArgumentException(reason))
    } else {
        if (skipped.isNotEmpty()) {
            LogBuffer.add("toWorkflow: loaded ${map.size} nodes, skipped ${skipped.size} keys: ${skipped.joinToString()}")
        }
        Result.success(map)
    }
}
