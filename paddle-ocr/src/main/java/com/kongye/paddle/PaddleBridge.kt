package com.kongye.paddle

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.engine.OCREngine
import com.paddle.ocr.util.OpenCVUtils
import java.util.function.BooleanSupplier

/** Synchronous, worker-thread API for the Java application. Models stay entirely on device. */
class PaddleBridge(context: Context) : AutoCloseable {
    data class Line(val text: String, val confidence: Float, val left: Int, val top: Int, val right: Int, val bottom: Int)
    private val engine: OCREngine
    init {
        check(OpenCVUtils.init(context.applicationContext)) { "OpenCV initialization failed" }
        engine = OCREngine(context.applicationContext,
            PaddleOCRConfig(detLimitSideLen = 1280, detLimitType = "max", detMaxSideLimit = 1280,
                detBoxThresh = .5f, recScoreThresh = .65f), EngineConfig(numThreads = 2),
            "models/det/inference.onnx", "models/rec/inference.onnx", "models/rec/inference.yml")
    }
    fun recognize(image: Bitmap, cancelled: BooleanSupplier): List<Line> {
        if (cancelled.asBoolean) throw java.io.InterruptedIOException()
        val result = engine.run(image, cancelled)
        if (cancelled.asBoolean) throw java.io.InterruptedIOException()
        return result.results.map {
            Line(it.text, it.confidence,
                kotlin.math.floor(it.box.points.minOf { p -> p.x }).toInt().coerceIn(0, image.width),
                kotlin.math.floor(it.box.points.minOf { p -> p.y }).toInt().coerceIn(0, image.height),
                kotlin.math.ceil(it.box.points.maxOf { p -> p.x }).toInt().coerceIn(0, image.width),
                kotlin.math.ceil(it.box.points.maxOf { p -> p.y }).toInt().coerceIn(0, image.height))
        }
    }
    override fun close() { engine.release() }
}
