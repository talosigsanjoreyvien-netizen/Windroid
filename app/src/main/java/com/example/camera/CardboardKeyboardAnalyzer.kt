package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CardboardKeyboardAnalyzer(
    private val onImageCaptured: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val executor = Executors.newSingleThreadExecutor()
    private var lastAnalysisTime = 0L
    private val analysisInterval = 2000L // Analyze every 2 seconds

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime >= analysisInterval) {
            val bitmap = image.toBitmap()
            val rotatedBitmap = bitmap.rotate(image.imageInfo.rotationDegrees.toFloat())
            val base64Image = rotatedBitmap.toBase64()
            
            onImageCaptured(base64Image)
            lastAnalysisTime = currentTime
        }
        image.close()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
