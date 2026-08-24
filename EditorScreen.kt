package com.example.ocrsheettoword.feature.editor
import androidx.compose.material.icons.filled.Description
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ocrsheettoword.core.common.UiState
import com.example.ocrsheettoword.data.document.TxtExporter
import com.example.ocrsheettoword.feature.ocr.OcrViewModel
import kotlinx.coroutines.launch
import com.example.ocrsheettoword.data.document.DocumentFormatting
import com.example.ocrsheettoword.data.document.PoiDocxExporter
import com.example.ocrsheettoword.feature.Pages.PageManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: OcrViewModel,
	 pageManagerViewModel: PageManagerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by viewModel.state.collectAsStateWithLifecycle()

    var editableText by remember {
        mutableStateOf(TextFieldValue(""))
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->

        if (uri == null) {
            isSaving = false
            return@rememberLauncherForActivityResult
        }

        val current = viewModel.state.value

        if (current is UiState.Success) {

            scope.launch {

                val document = current.data.copy(
                    text = editableText.text
                )

                val result = TxtExporter(context).export(
                    document = document,
                    outputUri = uri
                )

                isSaving = false

                message = result.fold(
                    onSuccess = {
						pageManagerViewModel.clearSession()
                        "TXT file saved successfully."
						
                    },
                    onFailure = {
                        it.message ?: "Unable to save TXT file."
                    }
                )
            }

        } else {
            isSaving = false
        }
    }
val docxLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
) { uri: Uri? ->

    if (uri == null) {
        isSaving = false
        return@rememberLauncherForActivityResult
    }

    val current = viewModel.state.value

    if (current is UiState.Success) {

        scope.launch {

            val document = current.data.copy(
                text = editableText.text
            )

            val result = PoiDocxExporter(context).exportDocx(
                document = document,
                outputUri = uri,
                formatting = DocumentFormatting()
            )

            isSaving = false

            message = result.fold(
                onSuccess = {
					pageManagerViewModel.clearSession()
                    "Word DOCX file saved successfully."
                },
                onFailure = {
                    it.message ?: "Unable to create Word DOCX file."
                }
            )
        }

    } else {
        isSaving = false
    }
}


    LaunchedEffect(state) {

        if (state is UiState.Success) {

            val text =
                (state as UiState.Success).data.text

            editableText = TextFieldValue(text)
        }
    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text("OCR Editor")
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Back"
                        }
                    ) {

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },

                actions = {

                    if (state is UiState.Success) {

                        IconButton(
                            onClick = {

                                val clipboard =
                                    context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager

                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "OCR Text",
                                        editableText.text
                                    )
                                )

                                message = "Text copied."
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Copy text"
                            }
                        ) {

                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy text"
                            )
                        }

                        IconButton(
                            onClick = {
                                editableText =
                                    TextFieldValue("")

                                viewModel.updateText("")
                                message = "Text cleared."
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Clear text"
                            }
                        ) {

                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear text"
                            )
                        }
						
								IconButton(
    onClick = {

        if (editableText.text.isNotBlank()) {
            isSaving = true
            message = null

            docxLauncher.launch(
                "ocr_document.docx"
            )
        } else {
            message = "There is no text to save."
        }
    },
    enabled = !isSaving,
    modifier = Modifier.semantics {
        contentDescription = "Save Word DOCX"
    }
) {
    Icon(
        imageVector = Icons.Default.Description,
        contentDescription = "Save Word DOCX"
    )
}

                        IconButton(
                            onClick = {

                                if (editableText.text.isNotBlank()) {

                                    isSaving = true
                                    message = null

                                    saveLauncher.launch(
                                        "ocr_document.txt"
                                    )
                                } else {

                                    message =
                                        "There is no text to save."
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.semantics {
                                contentDescription = "Save TXT file"
                            }
                        ) {

                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save TXT"
                            )
                        }
                    }
                }
            )
			
	
        }

    ) { padding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            when (val current = state) {

                UiState.Idle -> {

                    Text(
                        text = "No document selected.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                UiState.Loading -> {

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Text(
                        text = "Reading text from the image...",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Please wait while OCR processes the document."
                    )
                }

                is UiState.Error -> {

                    Text(
                        text = "OCR could not be completed.",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    if (current.canRetry) {

                        Button(
                            onClick = {
                                message = null
                                viewModel.retry()
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }

                is UiState.Success -> {

                    Text(
                        text = "Extracted text",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    OutlinedTextField(

                        value = editableText,

                        onValueChange = {

                            editableText = it
                            viewModel.updateText(it.text)
                            message = null
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),

                        label = {
                            Text("OCR text")
                        },

                        placeholder = {
                            Text("Extracted text will appear here...")
                        },

                        minLines = 12
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "${editableText.text.length} characters",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            message?.let { text ->

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = text,
                    color = if (
                        text.contains("successfully") ||
                        text.contains("copied") ||
                        text.contains("cleared")
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}