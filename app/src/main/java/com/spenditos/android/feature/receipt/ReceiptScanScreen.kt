package com.spenditos.android.feature.receipt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
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

    IosScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            LargeTitle("Scan a receipt")
            when (state.step) {
                ReceiptScanStep.CHOOSE -> {
                    GlassCard(
                        modifier = Modifier.padding(horizontal = Spacing.screen).clickable(role = Role.Button, onClick = {
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
                        }).testTag("receipt.takePhoto"),
                        shape = RoundedCornerShape(Radius.l),
                    ) {
                        ReceiptAction(icon = Icons.Filled.CameraAlt, title = "Take photo", subtitle = "Use your camera to capture a receipt")
                    }
                    GlassCard(
                        modifier = Modifier.padding(horizontal = Spacing.screen).clickable(role = Role.Button, onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }).testTag("receipt.chooseFromLibrary"),
                        shape = RoundedCornerShape(Radius.l),
                    ) {
                        ReceiptAction(icon = Icons.Filled.Collections, title = "Choose from library", subtitle = "Select a photo from your device")
                    }
                    ReceiptTextButton(label = "Cancel", tag = "receipt.cancel", onClick = {
                        viewModel.cancel()
                        onBack()
                    })
                }
                ReceiptScanStep.PREPARING -> ProgressState("Preparing photo...", onCancel = { viewModel.cancel(); onBack() })
                ReceiptScanStep.UPLOADING -> ProgressState("Uploading receipt...", onCancel = { viewModel.cancel(); onBack() })
                ReceiptScanStep.ANALYZING -> ProgressState("Analyzing receipt...", "This may take a few seconds") { viewModel.cancel(); onBack() }
                ReceiptScanStep.DONE -> Unit
                ReceiptScanStep.FAILURE -> {
                    val finance = LocalFinanceColors.current
                    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            if (state.offline) Text("You're offline. Check your connection and try again.", color = finance.expense)
                            Text(state.error ?: "Couldn't read that receipt. You can enter it yourself.", modifier = Modifier.testTag("receipt.error"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    PillButton("Retry", onClick = viewModel::retry, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("receipt.retry"))
                    PillButton("Cancel", onClick = { viewModel.cancel(); onBack() }, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("receipt.cancel"), filled = false)
                }
            }
        }
    }
}

@Composable
private fun ReceiptAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(52.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ProgressState(text: String, detail: String? = null, onCancel: () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CircularProgressIndicator(modifier = Modifier.testTag("receipt.loading"), color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            PillButton("Cancel", onClick = onCancel, filled = false)
        }
    }
}

@Composable
private fun ReceiptTextButton(label: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(label) }
}
