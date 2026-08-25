package com.example.ocrsheettoword.feature.ocr

import android.app.Application
import android.net.Uri

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import com.example.ocrsheettoword.core.common.UiState
import com.example.ocrsheettoword.data.ocr.MlKitOcrEngine
import com.example.ocrsheettoword.data.ocr.OcrTableDetector

import com.example.ocrsheettoword.domain.model.OcrDocument
import com.example.ocrsheettoword.domain.model.OcrLanguage
import com.example.ocrsheettoword.domain.model.OcrTable

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class OcrViewModel(
    application: Application
) : AndroidViewModel(application) {

    // =========================================================
    // EXISTING OCR ENGINE
    // =========================================================

    private val engine = MlKitOcrEngine(application)

    // =========================================================
    // NEW TABLE DETECTOR
    // =========================================================

    private val tableDetector = OcrTableDetector()


    // =========================================================
    // EXISTING OCR STATE
    // =========================================================

    private val _state =
        MutableStateFlow<UiState<OcrDocument>>(UiState.Idle)

    val state: StateFlow<UiState<OcrDocument>> =
        _state.asStateFlow()


    // =========================================================
    // NEW TABLE STATE
    // =========================================================

    private val _tableState =
        MutableStateFlow<UiState<OcrTable>>(UiState.Idle)

    val tableState: StateFlow<UiState<OcrTable>> =
        _tableState.asStateFlow()


    // =========================================================
    // JOBS
    // =========================================================

    private var ocrJob: Job? = null

    private var tableJob: Job? = null


    // =========================================================
    // LAST IMAGE / LANGUAGE
    // =========================================================

    private var lastUri: Uri? = null
	private var tableImageUri: Uri? = null

    private var lastLanguage: OcrLanguage =
        OcrLanguage.ENGLISH


    // =========================================================
    // EXISTING OCR
    // =========================================================

    fun startOcr(
        uri: Uri,
        language: OcrLanguage
    ) {

        lastUri = uri
        lastLanguage = language

        ocrJob?.cancel()

        ocrJob = viewModelScope.launch {

            _state.value = UiState.Loading

            val result =
                engine.recognize(
                    uri,
                    language
                )

            _state.value = result.fold(

                onSuccess = {
                    UiState.Success(it)
                },

                onFailure = {
                    UiState.Error(
                        message =
                            it.message
                                ?: "OCR failed.",

                        canRetry = true
                    )
                }
            )
        }
    }
fun setTableImageUri(uri: Uri) {
    tableImageUri = uri
}

    // =========================================================
    // NEW TABLE OCR
    // =========================================================
    //
    // This uses the same image that was selected for normal OCR.
    //
    // It does NOT replace the normal OCR state.
    //
    // =========================================================

    fun detectTable() {

       val uri = tableImageUri ?: lastUri

        if (uri == null) {

            _tableState.value =
                UiState.Error(
                    message =
                        "No image is available for table detection.",

                    canRetry = false
                )

            return
        }

        tableJob?.cancel()

        tableJob = viewModelScope.launch {

            _tableState.value =
                UiState.Loading

            val result =
                engine.recognizeTable(
                    uri,
                    lastLanguage
                )

            _tableState.value =
                result.fold(

                    onSuccess = { detectedTable ->

                        // -------------------------------------------------
                        // The ML Kit table OCR currently gives us cells
                        // with their bounding boxes.
                        //
                        // OcrTableDetector then organizes those cells
                        // into rows and columns.
                        // -------------------------------------------------

                        val allCells =
                            detectedTable.rows.flatMap {
                                it.cells
                            }

                        val structuredTable =
                            tableDetector.detect(
                                allCells
                            )

                        UiState.Success(
                            structuredTable
                        )
                    },

                    onFailure = {

                        UiState.Error(
                            message =
                                it.message
                                    ?: "Table detection failed.",

                            canRetry = true
                        )
                    }
                )
        }
    }


    // =========================================================
    // TABLE RETRY
    // =========================================================

    fun retryTableDetection() {

        if (lastUri == null) {
            return
        }

        detectTable()
    }


    // =========================================================
    // EXISTING SET TEXT
    // =========================================================

    fun setText(text: String) {

        val current =
            _state.value

        if (current is UiState.Success) {

            _state.value =
                UiState.Success(

                    current.data.copy(
                        text = text
                    )
                )

        } else {

            _state.value =
                UiState.Success(

                    OcrDocument(
                        text = text
                    )
                )
        }
    }


    // =========================================================
    // EXISTING UPDATE TEXT
    // =========================================================

    fun updateText(text: String) {

        val current =
            _state.value

        if (current is UiState.Success) {

            _state.value =
                UiState.Success(

                    current.data.copy(
                        text = text
                    )
                )
        }
    }


    // =========================================================
    // EXISTING RETRY
    // =========================================================

    fun retry() {

        val uri =
            lastUri
                ?: return

        startOcr(
            uri,
            lastLanguage
        )
    }


    // =========================================================
    // NEW CLEAR TABLE
    // =========================================================

    fun clearTable() {

        tableJob?.cancel()

        _tableState.value =
            UiState.Idle
    }


    // =========================================================
    // EXISTING CLEAR
    // =========================================================

    fun clear() {

        ocrJob?.cancel()

        tableJob?.cancel()

        _state.value =
            UiState.Idle

        _tableState.value =
            UiState.Idle
    }


    // =========================================================
    // EXISTING SHOW ERROR
    // =========================================================

    fun showError(
        message: String
    ) {

        _state.value =
            UiState.Error(
                message = message,
                canRetry = false
            )
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onCleared() {

        ocrJob?.cancel()

        tableJob?.cancel()

        super.onCleared()
    }
}