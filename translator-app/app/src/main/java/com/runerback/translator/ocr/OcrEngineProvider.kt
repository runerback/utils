package com.runerback.translator.ocr

import android.content.Context
import com.runerback.translator.util.LogManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OcrEngineProvider {

    private val mutex = Mutex()
    private var instance: PaddleOcrEngine? = null

    /**
     * Returns the shared OCR engine, creating it on first call.
     * Concurrent callers wait; creation happens exactly once per process.
     */
    suspend fun get(context: Context): PaddleOcrEngine? {
        instance?.let { return it }
        return mutex.withLock {
            instance?.let { return@withLock it }
            PaddleOcrEngine.create(context.applicationContext)
                .onSuccess {
                    instance = it
                    LogManager.d("OcrEngineProvider", "OCR engine created")
                }
                .onFailure {
                    LogManager.e("OcrEngineProvider", "OCR engine init failed", it)
                }
                .getOrNull()
        }
    }
}
