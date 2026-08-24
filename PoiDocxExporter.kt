package com.example.ocrsheettoword.data.document

import android.content.Context
import android.net.Uri
import com.example.ocrsheettoword.domain.model.OcrDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.IOException

class PoiDocxExporter(
    private val context: Context
) : DocumentExporter {

    override suspend fun exportDocx(
        document: OcrDocument,
        outputUri: Uri,
        formatting: DocumentFormatting
    ): Result<Unit> = runCatching {

        withContext(Dispatchers.IO) {

            if (document.text.isBlank()) {
                throw IOException(
                    "There is no text to export."
                )
            }

            context.contentResolver
                .openOutputStream(outputUri)
                ?.use { outputStream ->

                    XWPFDocument().use { wordDocument ->

                        configureDocument(
                            wordDocument,
                            formatting
                        )

                        addDocumentText(
                            wordDocument,
                            document.text,
                            formatting
                        )

                        wordDocument.write(outputStream)
                        outputStream.flush()
                    }

                }
                ?: throw IOException(
                    "Unable to create the DOCX output file."
                )
        }
    }

    private fun configureDocument(
    document: XWPFDocument,
    formatting: DocumentFormatting
) {

    val body = document.document.body

    val section =
        if (body.isSetSectPr) {
            body.sectPr
        } else {
            body.addNewSectPr()
        }

    val pageSize =
        if (section.isSetPgSz) {
            section.pgSz
        } else {
            section.addNewPgSz()
        }

    // A4 page size in twentieths of a point.
    pageSize.w = 11906
    pageSize.h = 16838

    val margins =
        if (section.isSetPgMar) {
            section.pgMar
        } else {
            section.addNewPgMar()
        }

    // Approximately 1 inch margins.
    margins.top = 1440
    margins.bottom = 1440
    margins.left = 1440
    margins.right = 1440
}

    private fun addDocumentText(
        document: XWPFDocument,
        text: String,
        formatting: DocumentFormatting
    ) {

        val paragraphs = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")

        for (paragraphText in paragraphs) {

            val paragraph =
                document.createParagraph()

            configureParagraph(
                paragraph,
                formatting
            )

            val run =
                paragraph.createRun()

            configureRun(
                run,
                formatting
            )

            run.setText(
                paragraphText,
                0
            )
        }
    }

    private fun configureParagraph(
        paragraph: XWPFParagraph,
        formatting: DocumentFormatting
    ) {

        paragraph.alignment =
            when (formatting.alignment) {

                DocumentAlignment.LEFT ->
                    ParagraphAlignment.LEFT

                DocumentAlignment.CENTER ->
                    ParagraphAlignment.CENTER

                DocumentAlignment.RIGHT ->
                    ParagraphAlignment.RIGHT

                DocumentAlignment.JUSTIFY ->
                    ParagraphAlignment.BOTH
            }

        paragraph.spacingAfter =
            formatting.paragraphSpacingAfter
    }

    private fun configureRun(
        run: XWPFRun,
        formatting: DocumentFormatting
    ) {

        run.fontFamily =
            formatting.fontFamily

        run.fontSize =
    formatting.fontSize.toInt()

        run.isBold =
            formatting.bold

        run.isItalic =
            formatting.italic

        run.underline =
            if (formatting.underline) {
                org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
            } else {
                org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
            }
    }
}