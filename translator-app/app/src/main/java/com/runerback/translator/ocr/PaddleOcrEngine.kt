package com.runerback.translator.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaddleOcrEngine private constructor(
    private val ocr: PaddleOCR,
) {

    suspend fun run(bitmap: Bitmap): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = ocr.recognize(bitmap)
            result.results.map { it.text }
        }
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        runCatching { ocr.release() }
    }

    companion object {

        suspend fun create(context: Context): Result<PaddleOcrEngine> = withContext(Dispatchers.IO) {
            runCatching {
                val initialized = OpenCVUtils.init(context)
                if (!initialized) {
                    throw IllegalStateException("OpenCV initialization failed")
                }
                val ocr = PaddleOCR.create(
                    context = context,
                    config = PaddleOCRConfig(
                        recScoreThresh = 0.0f,
                        recBatchSize = 1,
                    ),
                    engineConfig = EngineConfig(numThreads = 4),
                    detModelAssetPath = "models/det/inference.onnx",
                    recModelAssetPath = "models/rec/inference.onnx",
                    recConfigAssetPath = "models/rec/inference.yml",
                )
                PaddleOcrEngine(ocr)
            }
        }
    }
}
