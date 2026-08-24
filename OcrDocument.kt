package com.example.ocrsheettoword.domain.model

data class OcrDocument(
    val text: String,
    val blocks: List<OcrBlock> = emptyList()
)

data class OcrBlock(
    val text: String,
    val lines: List<String> = emptyList()
)
