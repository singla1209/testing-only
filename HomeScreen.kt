package com.example.ocrsheettoword.feature.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ocrsheettoword.domain.model.OcrLanguage
import com.example.ocrsheettoword.feature.editor.EditorScreen
import com.example.ocrsheettoword.feature.ocr.OcrViewModel
import com.example.ocrsheettoword.feature.ocr.rememberOcrViewModel
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Camera
import com.example.ocrsheettoword.feature.scanner.CameraScannerScreen
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ocrsheettoword.feature.Pages.PageManagerScreen
import com.example.ocrsheettoword.feature.Pages.PageManagerViewModel
import androidx.compose.runtime.collectAsState
import com.example.ocrsheettoword.domain.model.OcrPage
import com.example.ocrsheettoword.feature.Pages.PagePreviewScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {

	val pageManagerViewModel: PageManagerViewModel =
    viewModel()
	
	var showScanner by remember {
    mutableStateOf(false)
	}
	var multiPageScanMode by remember {
    mutableStateOf(false)
}
	var showPageManager by remember {
    mutableStateOf(false)
}

	var selectedPage by remember {
    mutableStateOf<OcrPage?>(null)
}

    val context = LocalContext.current

    var showEditor by remember { mutableStateOf(false) }

    var language by remember {
        mutableStateOf(OcrLanguage.ENGLISH)
    }

    val viewModel: OcrViewModel = rememberOcrViewModel()

    val picker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
) { uri ->
    if (uri != null) {
        try {
            viewModel.clear()

            viewModel.startOcr(
                uri = uri,
                language = language
            )

            showEditor = true
        } catch (_: SecurityException) {
            // Temporary read permission is still usable.
        }
    }
}

if (selectedPage != null) {

    PagePreviewScreen(
        page = selectedPage!!,

        onBack = {
            selectedPage = null
        },

        onDelete = {

            pageManagerViewModel.deletePage(
                selectedPage!!.id
            )

            selectedPage = null
        },

        onRescan = {

    pageManagerViewModel.deletePage(
        selectedPage!!.id
    )

    selectedPage = null

    // Close Page Manager before opening CameraX
    showPageManager = false

    multiPageScanMode = true
    showScanner = true
	}
    )

    return
}

if (showPageManager) {

    PageManagerScreen(
        session = pageManagerViewModel.session.collectAsState().value,

        onBack = {
            showPageManager = false
        },

        onAddPage = {

			multiPageScanMode = true

			showPageManager = false
			showScanner = true
		},

        onDeletePage = { pageId ->
            pageManagerViewModel.deletePage(pageId)
        },
		
		onMovePageUp = { pageId ->

    pageManagerViewModel.movePageUp(
        pageId
    )
},

onMovePageDown = { pageId ->

    pageManagerViewModel.movePageDown(
        pageId
    )
},

        onOpenPage = { page ->

		selectedPage = page
		},

        onProcessDocument = {

            pageManagerViewModel.processDocument(
                language = language
            ) { combinedText ->

                viewModel.clear()

                viewModel.setText(
                    combinedText
                )

                showPageManager = false
                showEditor = true
            }
        },
    )

    return
}
if (showScanner) {

    CameraScannerScreen(
        onBack = {
            showScanner = false

            // If we came from the multi-page manager,
            // return to it.
            if (showPageManager) {
                showPageManager = true
            }
        },

        onImageCaptured = { file ->

            // -------------------------------------------------
            // If this capture came from Multi-Page Document,
            // add the image as a new page.
            // -------------------------------------------------

           if (multiPageScanMode) {

                pageManagerViewModel.addPage(file)
				 multiPageScanMode = false
                showScanner = false
                showPageManager = true

            } else {

                // -------------------------------------------------
                // Normal single-page Scan Document workflow.
                // Keep the existing working OCR behaviour.
                // -------------------------------------------------

                try {

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    viewModel.clear()

                    viewModel.startOcr(
                        uri = uri,
                        language = language
                    )

                    showScanner = false
                    showEditor = true

                } catch (exception: Exception) {

                    showScanner = false

                    viewModel.showError(
                        exception.message
                            ?: "Unable to open captured image."
                    )

                    showEditor = true
                }
            }
        }
    )

    return
}
if (showEditor) {
    EditorScreen(
        viewModel = viewModel,
        onBack = {
            showEditor = false
            viewModel.clear()
        }
    )

    return
}

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text("OCR Sheet to Word")
                },

                actions = {

                    IconButton(
                        onClick = { },
                        modifier = Modifier.semantics {
                            contentDescription = "Settings"
                        }
                    ) {

                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }

    ) { padding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "Convert documents to editable text",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Select a clear photo or scanned document and OCR will extract its text on your device."
            )

            Spacer(Modifier.height(24.dp))

            LanguageSelector(
                selected = language,
                onSelected = { language = it }
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Select an image",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Choose JPG, PNG, or another supported image from your device."
                    )

                    Spacer(Modifier.height(18.dp))
					
					Button(
    onClick = {
        showPageManager = true
    },
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
) {

    Icon(
        imageVector = Icons.Default.Description,
        contentDescription = null
    )

    Spacer(
        modifier = Modifier.width(8.dp)
    )

    Text("Multi-Page Document")
}

Spacer(
    modifier = Modifier.height(12.dp)
)
					Button(
    onClick = {
        showScanner = true
    },
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
) {

    Icon(
        imageVector = Icons.Default.Camera,
        contentDescription = null
    )

    Spacer(
        modifier = Modifier.width(8.dp)
    )

    Text("Scan Document")
}

Spacer(
    modifier = Modifier.height(16.dp)
)

                    Button(

                        onClick = {
                            picker.launch(
    PickVisualMediaRequest(
        ActivityResultContracts.PickVisualMedia.ImageOnly
    )
)
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {

                        Text("Select Image")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(14.dp))

                    Column {

                        Text(
                            "Phase 1",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Image → OCR → editable text → TXT"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    selected: OcrLanguage,
    onSelected: (OcrLanguage) -> Unit
) {

    Column {

        Text(
            "OCR language",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        OcrLanguage.entries.forEach { option ->

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {

                RadioButton(
                    selected = selected == option,
                    onClick = {
                        onSelected(option)
                    }
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = option.displayName,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}