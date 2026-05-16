package com.anantva.tether.ui_elements.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val MAX_IMAGE_DIMENSION = 1080

private fun downsampleBitmap(input: InputStream): Bitmap? {
    val bytes = input.readBytes()
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    val (origW, origH) = opts.outWidth to opts.outHeight
    var sampleSize = 1
    while (origW / sampleSize > MAX_IMAGE_DIMENSION || origH / sampleSize > MAX_IMAGE_DIMENSION) {
        sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
}

@Composable
fun ReceiptImportScreen(
    imageUri: Uri,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Starting...") }

    LaunchedEffect(imageUri) {
        Log.d("TetherOCR", "=== URI RECEIVED: $imageUri ===")
        status = "Opening stream..."

        launch(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(imageUri)
                Log.d("TetherOCR", "=== STREAM OPENED ===")
                if (stream == null) {
                    withContext(Dispatchers.Main) {
                        Log.e("TetherOCR", "Stream is null")
                        status = "Error: null stream"
                    }
                    return@launch
                }

                status = "Decoding bitmap..."
                val bitmap = downsampleBitmap(stream)
                stream.close()
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Log.e("TetherOCR", "Bitmap decode failed")
                        status = "Error: bitmap null"
                    }
                    return@launch
                }
                Log.d("TetherOCR", "=== BITMAP: ${bitmap.width}x${bitmap.height} ===")

                withContext(Dispatchers.Main) {
                    status = "Creating input image..."
                }
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                Log.d("TetherOCR", "=== INPUT IMAGE CREATED ===")

                withContext(Dispatchers.Main) {
                    status = "Initializing OCR..."
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                Log.d("TetherOCR", "=== OCR CLIENT CREATED ===")

                withContext(Dispatchers.Main) {
                    status = "Running OCR..."
                }
                Log.d("TetherOCR", "=== OCR STARTED ===")

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        Log.d("TetherOCR", "=== OCR SUCCESS ===")
                        Log.d("TetherOCR", "Text length: ${text.length}")
                        Log.d("TetherOCR", "Full text:\n$text")
                        status = "OCR complete"
                        Toast.makeText(context, text.take(150), Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("TetherOCR", "=== OCR FAILED ===", e)
                        status = "OCR error: ${e.message}"
                        Toast.makeText(context, "OCR: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                Log.e("TetherOCR", "=== PIPELINE ERROR ===", e)
                withContext(Dispatchers.Main) {
                    status = "Error: ${e.message}"
                    Toast.makeText(context, "Pipeline error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = status, color = Color.White)
    }
}
