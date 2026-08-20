package com.runerback.queuehelper.ui.pack

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.data.model.formatSubjectDefinitions
import com.runerback.queuehelper.data.model.parseSubjectDefinitions
import com.runerback.queuehelper.data.model.removeDescriptionSegment
import com.runerback.queuehelper.data.model.replacePictureToken
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.data.template.VideoLengthRule
import com.runerback.queuehelper.domain.AudioTrimmer
import com.runerback.queuehelper.ui.components.LogBuffer
import com.runerback.queuehelper.ui.navigation.TaskEditorRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val MAX_IMAGES = 6

class TaskEditorViewModel(
    private val context: Context,
    private val repository: TaskRepository,
    private val templateLoader: TemplateLoader,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: Int = savedStateHandle[TaskEditorRoute::taskId.name]
        ?: throw IllegalArgumentException("Missing taskId")

    var task by mutableStateOf<Task?>(null)
        private set

    var prompt by mutableStateOf(MiniMaxH3Ref2VaPrompt())
        private set

    val subjects = mutableStateListOf<SubjectDefinition>()

    var audioDefinitionLine by mutableStateOf<String?>(null)
        private set

    private var nextSubjectId = 1
    private var otherSubjectLines = ""

    private var subjectDefaults = emptyList<SubjectDefault>()
    private var audioDefault = SubjectDefinition.defaultAudioDefinition()

    var resolution by mutableStateOf("480x832")
        private set

    val imageUris = mutableStateListOf<Uri>()

    var audioUri by mutableStateOf<Uri?>(null)
        private set

    var audioDurationSeconds by mutableFloatStateOf(0f)
        private set

    var trimStart by mutableFloatStateOf(0f)
        private set

    var trimEnd by mutableFloatStateOf(0f)
        private set

    val maxAudioDurationSeconds: Float
        get() = task?.payload?.get("params")?.jsonObject
            ?.get("model_type")?.jsonPrimitive?.contentOrNull
            ?.let { templateLoader.config(it).maxAudioDurationSeconds }
            ?: Float.MAX_VALUE

    var isPacking by mutableStateOf(false)
        private set

    var packResult by mutableStateOf<String?>(null)
        private set

    private val json = Json { prettyPrint = true }
    private val saveMutex = Mutex()

    init {
        loadTask()
    }

    private fun loadTask() {
        viewModelScope.launch {
            val loaded = repository.loadTask(taskId)
            task = loaded
            loaded?.let {
                val params = it.payload["params"]?.jsonObject ?: JsonObject(emptyMap())
                resolution = params["resolution"]?.jsonPrimitive?.contentOrNull ?: "480x832"
                prompt = MiniMaxH3Ref2VaPrompt.parse(
                    params["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                )
                val parsed = parseSubjectDefinitions(prompt.subjectDefinitions)
                subjects.clear()
                subjects.addAll(parsed.first)
                audioDefinitionLine = parsed.second
                otherSubjectLines = parsed.third
                nextSubjectId = (subjects.maxOfOrNull { it.id } ?: 0) + 1

                val defaultsJson = it.payload["subject_defaults"]
                if (defaultsJson != null) {
                    try {
                        val defaults = json.decodeFromJsonElement(
                            SubjectDefaults.serializer(),
                            defaultsJson
                        )
                        subjectDefaults = defaults.subjects
                        audioDefault = defaults.audio
                    } catch (_: Exception) {
                        subjectDefaults = parsed.first.map { s -> SubjectDefault(s.number, s.description) }
                        audioDefault = parsed.second ?: SubjectDefinition.defaultAudioDefinition()
                    }
                } else {
                    subjectDefaults = parsed.first.map { s -> SubjectDefault(s.number, s.description) }
                    audioDefault = parsed.second ?: SubjectDefinition.defaultAudioDefinition()
                }

                val packSettings = it.payload["pack_settings"]?.jsonObject
                packSettings?.let { settings ->
                    imageUris.clear()
                    imageUris.addAll(
                        settings["image_uris"]?.jsonArray?.mapNotNull { element ->
                            element.jsonPrimitive.contentOrNull?.let { uriString ->
                                runCatching { Uri.parse(uriString) }.getOrNull()
                            }
                        } ?: emptyList()
                    )
                    audioUri = settings["audio_uri"]?.jsonPrimitive?.contentOrNull?.let { uriString ->
                        runCatching { Uri.parse(uriString) }.getOrNull()
                    }
                    trimStart = settings["trim_start"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                    trimEnd = settings["trim_end"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                    audioUri?.let { uri -> readAudioDuration(uri) }
                }
            }
        }
    }

    fun updatePrompt(value: MiniMaxH3Ref2VaPrompt) {
        prompt = value
    }

    fun updateResolution(value: String) {
        resolution = value
        viewModelScope.launch { savePackSettings() }
    }

    fun addSubject(description: String? = null) {
        val number = (subjects.maxOfOrNull { it.number } ?: 0) + 1
        val defaultDescription = subjectDefaults.find { it.number == number }?.description ?: ""
        subjects.add(
            SubjectDefinition(
                id = nextSubjectId++,
                number = number,
                description = description ?: defaultDescription
            )
        )
        rebuildSubjectDefinitions()
    }

    fun updateSubject(id: Int, description: String) {
        val index = subjects.indexOfFirst { it.id == id }
        if (index == -1) return
        subjects[index] = subjects[index].copy(description = description)
        rebuildSubjectDefinitions()
    }

    fun replaceSubjectPictureToken(subjectId: Int, oldNumber: Int, newNumber: Int) {
        val index = subjects.indexOfFirst { it.id == subjectId }
        if (index == -1) return
        val current = subjects[index]
        val updated = replacePictureToken(current.description, oldNumber, newNumber)
        if (updated != current.description) {
            subjects[index] = current.copy(description = updated)
            rebuildSubjectDefinitions()
        }
    }

    fun removeSubjectPictureToken(subjectId: Int, segmentIndex: Int) {
        val index = subjects.indexOfFirst { it.id == subjectId }
        if (index == -1) return
        val current = subjects[index]
        val updated = removeDescriptionSegment(current.description, segmentIndex)
        if (updated != current.description) {
            subjects[index] = current.copy(description = updated)
            rebuildSubjectDefinitions()
        }
    }

    fun removeSubject(id: Int) {
        subjects.removeAll { it.id == id }
        rebuildSubjectDefinitions()
    }

    fun addAudioDefinition() {
        audioDefinitionLine = audioDefault
        rebuildSubjectDefinitions()
    }

    fun removeAudioDefinition() {
        audioDefinitionLine = null
        rebuildSubjectDefinitions()
    }

    private fun rebuildSubjectDefinitions() {
        val text = formatSubjectDefinitions(subjects, audioDefinitionLine, otherSubjectLines)
        prompt = prompt.copy(subjectDefinitions = text)
    }

    fun addImages(uris: List<Uri>) {
        val remaining = MAX_IMAGES - imageUris.size
        if (remaining <= 0) return
        imageUris.addAll(uris.take(remaining))
        viewModelScope.launch { savePackSettings() }
    }

    fun removeImage(index: Int) {
        if (index !in imageUris.indices) return
        imageUris.removeAt(index)
        viewModelScope.launch { savePackSettings() }
    }

    fun setAudio(uri: Uri?) {
        audioUri = uri
        if (uri == null) {
            audioDefinitionLine = null
            rebuildSubjectDefinitions()
            viewModelScope.launch { savePackSettings() }
        } else {
            viewModelScope.launch {
                readAudioDuration(uri)
                savePackSettings()
            }
        }
    }

    fun updateTrimRange(start: Float, end: Float) {
        val maxDuration = maxAudioDurationSeconds
        var newStart = start.coerceIn(0f, audioDurationSeconds)
        var newEnd = end.coerceIn(0f, audioDurationSeconds)

        if (newEnd < newStart) {
            val tmp = newStart
            newStart = newEnd
            newEnd = tmp
        }

        if (maxDuration.isFinite()) {
            val maxEnd = (newStart + maxDuration).coerceAtMost(audioDurationSeconds)
            if (newEnd > maxEnd) {
                newEnd = maxEnd
            }
        }

        trimStart = newStart
        trimEnd = newEnd
        viewModelScope.launch { savePackSettings() }
    }

    private suspend fun readAudioDuration(uri: Uri) {
        val durationMs = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                LogBuffer.add("TaskEditorViewModel.readAudioDuration($uri): ${e.stackTraceToString()}")
                0L
            } finally {
                retriever.release()
            }
        }
        audioDurationSeconds = durationMs / 1000f
        trimStart = 0f
        trimEnd = audioDurationSeconds.coerceAtMost(maxAudioDurationSeconds)
    }

    fun computedVideoLength(): Int {
        val modelType = task?.payload?.get("params")?.jsonObject?.get("model_type")?.jsonPrimitive?.contentOrNull
        val rule = modelType?.let { templateLoader.config(it).videoLengthRule }
            ?: VideoLengthRule.AudioDurationMultiplier()
        if (rule !is VideoLengthRule.AudioDurationMultiplier) return 0
        val trimmedDuration = (trimEnd - trimStart).coerceAtLeast(0f)
        return (trimmedDuration * rule.multiplier).toInt().coerceAtMost(rule.max)
    }

    fun pack() {
        viewModelScope.launch {
            isPacking = true
            packResult = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val currentTask = task ?: throw IllegalStateException("Task not loaded")
                    val (outputStream, savedPath) = openDownloadsOutputStream("queue.zip")
                    val imageNames = imageUris.mapIndexed { index, _ ->
                        "task${currentTask.id}_image_refs_$index.png"
                    }

                    ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                        imageUris.forEachIndexed { index, uri ->
                            zip.addFile(imageNames[index], uri)
                        }

                        audioUri?.let { uri ->
                            val start = trimStart
                            val end = if (trimEnd > trimStart) trimEnd else Float.MAX_VALUE
                            val tempAudio = File.createTempFile("audio_${currentTask.id}_", ".wav", context.cacheDir)
                            AudioTrimmer.trimToWav(context, uri, tempAudio, start, end).getOrThrow()
                            val name = "task${currentTask.id}_audio_guide_0.wav"
                            zip.addFile(name, tempAudio)
                            tempAudio.delete()
                        }

                        val queueJson = buildQueueJson(currentTask, imageNames)
                        zip.putNextEntry(ZipEntry("queue.json"))
                        zip.write(queueJson.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }

                    persistPackSettings(currentTask, imageNames)
                    "Saved to $savedPath"
                }.getOrElse {
                    LogBuffer.add("TaskEditorViewModel.pack: ${it.stackTraceToString()}")
                    "Pack failed: ${it.message}"
                }
            }
            packResult = result
            isPacking = false
        }
    }

    private suspend fun persistPackSettings(currentTask: Task, imageNames: List<String>) {
        val params = currentTask.payload["params"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        params["prompt"] = JsonPrimitive(prompt.toPromptString())
        params["resolution"] = JsonPrimitive(resolution)
        params["video_length"] = JsonPrimitive(computedVideoLength())
        params["image_refs"] = buildJsonArray {
            imageNames.forEach { add(JsonPrimitive(it)) }
        }
        params["audio_guide"] = audioUri?.let {
            JsonPrimitive("task${currentTask.id}_audio_guide_0.wav")
        } ?: JsonNull

        val packSettings = buildJsonObject {
            put("image_uris", buildJsonArray {
                imageUris.forEach { add(JsonPrimitive(it.toString())) }
            })
            put("audio_uri", audioUri?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
            put("trim_start", JsonPrimitive(trimStart))
            put("trim_end", JsonPrimitive(trimEnd))
        }

        val updatedPayload = JsonObject(
            currentTask.payload.toMutableMap().apply {
                put("params", JsonObject(params))
                put("pack_settings", packSettings)
            }
        )
        val updated = currentTask.copy(payload = updatedPayload)
        repository.saveTask(updated)
        task = updated
    }

    private suspend fun savePackSettings() {
        saveMutex.withLock {
            val currentTask = task ?: return
            val imageNames = imageUris.mapIndexed { index, _ ->
                "task${currentTask.id}_image_refs_$index.png"
            }
            persistPackSettings(currentTask, imageNames)
        }
    }

    private fun openDownloadsOutputStream(fileName: String): Pair<OutputStream, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create download entry")
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open download output stream")
            outputStream to "Download/$fileName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file) to file.absolutePath
        }
    }

    private fun buildQueueJson(task: Task, imageNames: List<String>): String {
        val baseParams = task.payload["params"]?.jsonObject
            ?.toMutableMap()
            ?: mutableMapOf()

        baseParams["prompt"] = JsonPrimitive(prompt.toPromptString())
        baseParams["resolution"] = JsonPrimitive(resolution)
        baseParams["video_length"] = JsonPrimitive(computedVideoLength())
        baseParams["image_refs"] = buildJsonArray {
            imageNames.forEach { add(JsonPrimitive(it)) }
        }
        baseParams["audio_guide"] = JsonPrimitive("task${task.id}_audio_guide_0.wav")

        val taskObject = buildJsonObject {
            put("id", JsonPrimitive(task.id))
            put("params", JsonObject(baseParams))
        }
        val array = buildJsonArray { add(taskObject) }
        return json.encodeToString(array)
    }

    private fun ZipOutputStream.addFile(entryName: String, uri: Uri) {
        putNextEntry(ZipEntry(entryName))
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedInputStream(input).copyTo(this)
        }
        closeEntry()
    }

    private fun ZipOutputStream.addFile(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        BufferedInputStream(file.inputStream()).use { input ->
            input.copyTo(this)
        }
        closeEntry()
    }

    fun dismissResult() {
        packResult = null
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val context: Context,
        private val repository: TaskRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return TaskEditorViewModel(
                context,
                repository,
                templateLoader,
                extras.createSavedStateHandle()
            ) as T
        }
    }
}
