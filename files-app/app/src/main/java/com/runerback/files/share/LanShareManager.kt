package com.runerback.files.share

import android.content.Context
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.repository.FileRepositoryFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanShareManager @Inject constructor(
    private val repositoryFactory: FileRepositoryFactory
) {

    private var server: LanShareServer? = null
    private var _baseUrl: String? = null
    private val _sharedFiles = MutableStateFlow<Map<String, LanShareServer.SharedFile>>(emptyMap())

    val baseUrl: String? get() = _baseUrl
    val sharedFiles: StateFlow<Map<String, LanShareServer.SharedFile>> = _sharedFiles.asStateFlow()

    fun isSharing(): Boolean = server?.isRunning() == true

    suspend fun startShare(context: Context, files: List<FileNode>, source: FileSource): String? {
        stopShare(context)
        val repository = repositoryFactory.create(source)
        val newServer = LanShareServer(repository)
        val url = newServer.start(files) ?: return null
        server = newServer
        _baseUrl = url
        _sharedFiles.value = newServer.sharedFiles
        LanShareService.start(context)
        return url
    }

    fun stopShare(context: Context) {
        stopServer()
        LanShareService.stop(context)
    }

    fun onServiceDestroyed() {
        stopServer()
    }

    private fun stopServer() {
        server?.stop()
        server = null
        _baseUrl = null
        _sharedFiles.value = emptyMap()
    }
}
