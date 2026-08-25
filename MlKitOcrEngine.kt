package com.example.ocrsheettoword.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.example.ocrsheettoword.domain.model.OcrBlock
import com.example.ocrsheettoword.domain.model.OcrDocument
import com.example.ocrsheettoword.domain.model.OcrLanguage
import com.example.ocrsheettoword.domain.model.OcrTable
import com.example.ocrsheettoword.domain.model.OcrTableCell
import com.example.ocrsheettoword.domain.model.OcrTableRow
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max

class MlKitOcrEngine(
    private val context: Context
) : OcrEngine {

    override suspend fun recognize(
        imageUri: Uri,
        language: OcrLanguage
    ): Result<OcrDocument> = runCatching {

        // ---------------------------------------------------------
        // STEP 1
        // Copy the selected URI into our private app cache.
        // This makes the image independent of Gallery/File Picker
        // provider behaviour.
        // ---------------------------------------------------------

        val cachedImage = withContext(Dispatchers.IO) {
            copyUriToCache(imageUri)
        }

        try {

            // -----------------------------------------------------
            // STEP 2
            // Decode the cached image safely.
            // -----------------------------------------------------

            val bitmap = withContext(Dispatchers.IO) {
                decodeBitmapSafely(cachedImage)
            }

            try {

                // -------------------------------------------------
                // STEP 3
                // Give bitmap to ML Kit.
                // -------------------------------------------------

                val inputImage = InputImage.fromBitmap(
                    bitmap,
                    0
                )

                val recognizer = when (language) {

                    OcrLanguage.ENGLISH -> {
                        TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                        )
                    }

                    OcrLanguage.DEVANAGARI -> {
                        TextRecognition.getClient(
                            DevanagariTextRecognizerOptions.Builder()
                                .build()
                        )
                    }
                }

                try {

                    val result = recognizer
                        .process(inputImage)
                        .await()

                    if (result.text.isBlank()) {
                        throw IOException(
                            "No readable text was found in the selected image."
                        )
                    }

                    val blocks = result.textBlocks.map { block ->

                        OcrBlock(
                            text = block.text,
                            lines = block.lines.map { line ->
                                line.text
                            }
                        )
                    }

                    OcrDocument(
                        text = result.text,
                        blocks = blocks
                    )

                } finally {

                    recognizer.close()
                }

            } finally {

                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

        } finally {

            // -----------------------------------------------------
            // STEP 4
            // Delete temporary image after OCR.
            // -----------------------------------------------------

            try {
                cachedImage.delete()
            } catch (_: Exception) {
                // Nothing to do.
            }
        }
    }


    // =============================================================
    // TABLE OCR
    // =============================================================
    //
    // This is a separate method.
    //
    // The existing recognize() method above is NOT changed.
    //
    // This method extracts OCR text together with the bounding
    // boxes supplied by ML Kit.
    //
    // =============================================================

    suspend fun recognizeTable(
        imageUri: Uri,
        language: OcrLanguage
    ): Result<OcrTable> = runCatching {

        // ---------------------------------------------------------
        // STEP 1
        // Copy selected image into our private cache.
        // ---------------------------------------------------------

        val cachedImage = withContext(Dispatchers.IO) {
            copyUriToCache(imageUri)
        }

        try {

            // -----------------------------------------------------
            // STEP 2
            // Decode image using the same safe decoder as normal OCR.
            // -----------------------------------------------------

            val bitmap = withContext(Dispatchers.IO) {
                decodeBitmapSafely(cachedImage)
            }

            try {

                // -------------------------------------------------
                // STEP 3
                // Create ML Kit input image.
                // -------------------------------------------------

                val inputImage = InputImage.fromBitmap(
                    bitmap,
                    0
                )

                // -------------------------------------------------
                // STEP 4
                // Select the same OCR language as normal OCR.
                // -------------------------------------------------

                val recognizer = when (language) {

                    OcrLanguage.ENGLISH -> {
                        TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                        )
                    }

                    OcrLanguage.DEVANAGARI -> {
                        TextRecognition.getClient(
                            DevanagariTextRecognizerOptions.Builder()
                                .build()
                        )
                    }
                }

                try {

                    // -------------------------------------------------
                    // STEP 5
                    // Run ML Kit OCR.
                    // -------------------------------------------------

                    val result = recognizer
                        .process(inputImage)
                        .await()

                    if (result.text.isBlank()) {
                        throw IOException(
                            "No readable text was found in the selected image."
                        )
                    }

                    // -------------------------------------------------
                    // STEP 6
                    // Convert ML Kit elements into table cells.
                    //
                    // Each ML Kit text element has:
                    // - text
                    // - boundingBox
                    //
                    // For now each OCR line becomes a row and each
                    // element in that line becomes a cell.
                    // -------------------------------------------------

                    val rows = mutableListOf<OcrTableRow>()

                    var rowIndex = 0

                    for (block in result.textBlocks) {

                        for (line in block.lines) {

                            val cells = mutableListOf<OcrTableCell>()

                            var columnIndex = 0

                            for (element in line.elements) {

                                val boundingBox: Rect? =
                                    element.boundingBox

                                cells.add(
                                    OcrTableCell(
                                        text = element.text,
                                        rowIndex = rowIndex,
                                        columnIndex = columnIndex,
                                        boundingBox = boundingBox
                                    )
                                )

                                columnIndex++
                            }

                            if (cells.isNotEmpty()) {

                                rows.add(
                                    OcrTableRow(
                                        cells = cells
                                    )
                                )

                                rowIndex++
                            }
                        }
                    }

                    // -------------------------------------------------
                    // STEP 7
                    // Determine the largest number of columns.
                    // -------------------------------------------------

                    val columnCount = rows
                        .maxOfOrNull { row ->
                            row.cells.size
                        } ?: 0

                    // -------------------------------------------------
                    // STEP 8
                    // Create our new OcrTable object.
                    // -------------------------------------------------

                    OcrTable(
                        rows = rows,
                        columnCount = columnCount
                    )

                } finally {

                    recognizer.close()
                }

            } finally {

                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

        } finally {

            // -----------------------------------------------------
            // Delete temporary image after table OCR.
            // -----------------------------------------------------

            try {
                cachedImage.delete()
            } catch (_: Exception) {
                // Nothing to do.
            }
        }
    }


    // =============================================================
    // EXISTING CACHE FUNCTION
    // =============================================================

    private fun copyUriToCache(
        uri: Uri
    ): File {

        val resolver = context.contentResolver

        val extension =
            when (resolver.getType(uri)?.lowercase()) {

                "image/png" -> ".png"

                "image/webp" -> ".webp"

                "image/heic" -> ".heic"

                "image/heif" -> ".heif"

                else -> ".jpg"
            }

        val cacheFile = File(
            context.cacheDir,
            "ocr_input_${System.currentTimeMillis()}$extension"
        )

        val inputStream =
            resolver.openInputStream(uri)
                ?: throw IOException(
                    "Android could not read the selected image."
                )

        try {

            inputStream.use { input ->

                cacheFile.outputStream().use { output ->

                    input.copyTo(output)
                }
            }

        } catch (exception: Exception) {

            cacheFile.delete()

            throw IOException(
                "Unable to copy the selected image into the app.",
                exception
            )
        }

        if (!cacheFile.exists() || cacheFile.length() == 0L) {

            cacheFile.delete()

            throw IOException(
                "The selected image is empty or could not be read."
            )
        }

        return cacheFile
    }


    // =============================================================
    // EXISTING BITMAP DECODER
    // =============================================================

    private fun decodeBitmapSafely(
        file: File
    ): Bitmap {

        // ---------------------------------------------------------
        // First pass: determine image dimensions.
        // ---------------------------------------------------------

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(
            file.absolutePath,
            bounds
        )

        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0
        ) {
            throw IOException(
                "The selected file is not a valid JPG or PNG image."
            )
        }

        // ---------------------------------------------------------
        // Limit the largest dimension to approximately 2500 px.
        // This prevents very large phone photos from consuming
        // excessive memory.
        // ---------------------------------------------------------

        val maxDimension = 2500

        val largestDimension = max(
            bounds.outWidth,
            bounds.outHeight
        )

        var sampleSize = 1

        while (
            largestDimension / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        // ---------------------------------------------------------
        // Second pass: decode actual bitmap.
        // ---------------------------------------------------------

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            options
        ) ?: throw IOException(
            "Unable to decode the selected image."
        )
    }
}