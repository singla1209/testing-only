package com.example.ocrsheettoword.feature.ocr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ocrsheettoword.core.common.UiState
import com.example.ocrsheettoword.data.ocr.MlKitOcrEngine
import com.example.ocrsheettoword.domain.model.OcrDocument
import com.example.ocrsheettoword.domain.model.OcrLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OcrViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val engine = MlKitOcrEngine(application)

    private val _state =
        MutableStateFlow<UiState<OcrDocument>>(UiState.Idle)

    val state: StateFlow<UiState<OcrDocument>> =
        _state.asStateFlow()

    private var ocrJob: Job? = null
    private var lastUri: Uri? = null
    private var lastLanguage: OcrLanguage = OcrLanguage.ENGLISH

    fun startOcr(
        uri: Uri,
        language: OcrLanguage
    ) {
        lastUri = uri
        lastLanguage = language

        ocrJob?.cancel()

        ocrJob = viewModelScope.launch {
            _state.value = UiState.Loading

            val result = engine.recognize(uri, language)

            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = {
                    UiState.Error(
                        message = it.message ?: "OCR failed.",
                        canRetry = true
                    )
                }
            )
        }
    }
	
	fun setText(text: String) {

    val current = _state.value

    if (current is UiState.Success) {

        _state.value = UiState.Success(
            current.data.copy(
                text = text
            )
        )

    } else {

        _state.value = UiState.Success(
            OcrDocument(
                text = text
            )
        )
    }
}

    fun retry() {
        val uri = lastUri ?: return
        startOcr(uri, lastLanguage)
    }

    fun updateText(text: String) {
        val current = _state.value

        if (current is UiState.Success) {
            _state.value = UiState.Success(
                current.data.copy(text = text)
            )
        }
    }

    fun clear() {
        ocrJob?.cancel()
        _state.value = UiState.Idle
    }

fun showError(message: String) {
    _state.value = UiState.Error(
        message = message,
        canRetry = false
    )
}

    override fun onCleared() {
        ocrJob?.cancel()
        super.onCleared()
    }
}
