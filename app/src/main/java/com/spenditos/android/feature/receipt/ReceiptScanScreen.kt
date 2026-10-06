package com.spenditos.android.feature.receipt

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.ui.theme.Spacing
import java.io.File

@Composable
fun ReceiptScanScreen(
    onResult: (AddTransactionPrefill) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ReceiptScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        viewModel.onCameraCaptureResult(captured)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onLibraryPhotoSelected(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ReceiptScanEvent.Result) onResult(event.prefill)
        }
    }
    BackHandler {
        viewModel.cancel()
        onBack()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Scan a receipt", style = MaterialTheme.typography.titleLarge)
        when (state.step) {
            ReceiptScanStep.CHOOSE -> {
                Button(
                    onClick = {
                        var cameraFile: File? = null
                        try {
                            val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
                            val createdFile = File.createTempFile("receipt-camera-", ".jpg", directory)
                            cameraFile = createdFile
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", createdFile)
                            viewModel.setPendingCameraUri(uri.toString())
                            camera.launch(uri)
                        } catch (_: Exception) {
                            cameraFile?.delete()
                            viewModel.imagePreparationFailed()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("receipt.takePhoto"),
                ) { Text("Take photo") }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().testTag("receipt.chooseFromLibrary"),
                ) { Text("Choose from library") }
                TextButtonAction(label = "Cancel", tag = "receipt.cancel", onClick = {
                    viewModel.cancel()
                    onBack()
                })
            }
            ReceiptScanStep.PREPARING -> ProgressState("Preparing photo...") { viewModel.cancel(); onBack() }
            ReceiptScanStep.UPLOADING -> ProgressState("Uploading receipt...") { viewModel.cancel(); onBack() }
            ReceiptScanStep.ANALYZING -> ProgressState("Analyzing receipt...", "This may take a few seconds") { viewModel.cancel(); onBack() }
            ReceiptScanStep.DONE -> Spacer(Modifier.height(1.dp))
            ReceiptScanStep.FAILURE -> {
                if (state.offline) Text("You're offline. Check your connection and try again.", color = MaterialTheme.colorScheme.error)
                Text(state.error ?: "Couldn't read that receipt. You can enter it yourself.", modifier = Modifier.testTag("receipt.error"))
                Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth().testTag("receipt.retry")) { Text("Retry") }
                OutlinedButton(
                    onClick = { viewModel.cancel(); onBack() },
                    modifier = Modifier.fillMaxWidth().testTag("receipt.cancel"),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ProgressState(text: String, detail: String? = null, onCancel: () -> Unit) {
    CircularProgressIndicator(modifier = Modifier.testTag("receipt.loading"))
    Text(text)
    if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("receipt.cancel")) { Text("Cancel") }
}

@Composable
private fun TextButtonAction(label: String, tag: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(label) }
}
