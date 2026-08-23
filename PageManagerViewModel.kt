package com.example.ocrsheettoword.feature.Pages

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ocrsheettoword.data.ocr.MlKitOcrEngine
import com.example.ocrsheettoword.domain.model.DocumentSession
import com.example.ocrsheettoword.domain.model.OcrPage
import com.example.ocrsheettoword.domain.model.OcrLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PageManagerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val engine =
        MlKitOcrEngine(application)

    private val _session =
        MutableStateFlow(DocumentSession())

    val session: StateFlow<DocumentSession> =
        _session.asStateFlow()

    private val _processing =
        MutableStateFlow(false)

    val processing: StateFlow<Boolean> =
        _processing.asStateFlow()

    private val _error =
        MutableStateFlow<String?>(null)

    val error: StateFlow<String?> =
        _error.asStateFlow()

    fun addPage(file: File) {

        _session.value =
            _session.value.addPage(file)
    }
	fun movePageUp(pageId: Long) {

    _session.value =
        _session.value.movePageUp(pageId)
}

fun movePageDown(pageId: Long) {

    _session.value =
        _session.value.movePageDown(pageId)
}

    fun deletePage(pageId: Long) {

        _session.value =
            _session.value.deletePage(pageId)
    }

    fun updatePageText(
        pageId: Long,
        text: String
    ) {

        _session.value =
            _session.value.updatePageText(
                pageId = pageId,
                text = text
            )
    }

    fun getPage(
        pageId: Long
    ): OcrPage? {

        return _session.value.pages
            .firstOrNull {
                it.id == pageId
            }
    }
	
	

    fun clearSession() {

        _session.value =
            DocumentSession()

        _error.value = null
    }

    fun combinedText(): String {

        return _session.value.combinedText()
    }

    fun processDocument(
        language: OcrLanguage,
        onComplete: (String) -> Unit
    ) {

        if (_processing.value) {
            return
        }

        val pages =
            _session.value.pages
                .sortedBy {
                    it.pageNumber
                }

        if (pages.isEmpty()) {

            _error.value =
                "No pages to process."

            return
        }

        viewModelScope.launch {

            _processing.value = true
            _error.value = null

            try {

                for (page in pages) {

                    val uri =
                        Uri.fromFile(page.imageFile)

                    val result =
    engine.recognize(
        imageUri = uri,
        language = language
    )

                    result.fold(

                        onSuccess = { document ->

                            _session.value =
                                _session.value.updatePageText(
                                    pageId = page.id,
                                    text = document.text
                                )
                        },

                        onFailure = { exception ->

                            throw exception
                        }
                    )
                }

                val combined =
                    _session.value.combinedText()

                onComplete(combined)

            } catch (exception: Exception) {

                _error.value =
                    exception.message
                        ?: "Unable to process document."

            } finally {

                _processing.value = false
            }
        }
    }

    fun clearError() {

        _error.value = null
    }
}