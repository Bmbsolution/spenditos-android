package com.spenditos.android.feature.receipt

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.w
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
                    ReceiptActionCard(
                        icon = SfSymbols.icon("camera"),
                        title = "Take photo",
                        subtitle = "Use your camera to capture a receipt",
                        tint = IosColors.income,
                        modifier = Modifier.padding(horizontal = Spacing.screen).testTag("receipt.takePhoto"),
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
                    )
                    ReceiptActionCard(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Choose from library",
                        subtitle = "Select a photo from your device",
                        tint = IosColors.info,
                        modifier = Modifier.padding(horizontal = Spacing.screen).testTag("receipt.chooseFromLibrary"),
                        onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    TextButton(onClick = {
                        viewModel.cancel()
                        onBack()
                    }, modifier = Modifier.fillMaxWidth().testTag("receipt.cancel")) {
                        Text("Cancel", style = IosText.body, color = IosColors.neutral)
                    }
                }
                ReceiptScanStep.PREPARING -> ProgressState("Preparing photo...", onCancel = { viewModel.cancel(); onBack() })
                ReceiptScanStep.UPLOADING -> ProgressState("Uploading receipt...", onCancel = { viewModel.cancel(); onBack() })
                ReceiptScanStep.ANALYZING -> ProgressState("Analyzing receipt...", "This may take a few seconds") { viewModel.cancel(); onBack() }
                ReceiptScanStep.DONE -> Unit
                ReceiptScanStep.FAILURE -> {
                    ReceiptFailure(
                        offline = state.offline,
                        message = state.error ?: "Couldn't read that receipt. You can enter it yourself.",
                        onRetry = viewModel::retry,
                        onManual = { viewModel.cancel(); onBack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.l)
    Box(modifier.clickable(role = Role.Button, onClick = onClick)) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(
                    Modifier.size(56.dp)
                        .dropShadow(RoundedCornerShape(Radius.m), Shadow(radius = 16.dp, color = tint, offset = DpOffset(0.dp, 4.dp), alpha = 0.3f))
                        .clip(RoundedCornerShape(Radius.m))
                        .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(title, style = IosText.body.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                    Text(subtitle, style = IosText.subheadline, color = IosColors.textSecondary)
                }
                Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.matchParentSize().border(1.dp, tint.copy(alpha = 0.2f), shape))
    }
}

@Composable
private fun ReceiptFailure(offline: Boolean, message: String, onRetry: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(SfSymbols.icon("exclamationmark.triangle.fill"), contentDescription = null, tint = IosColors.expense, modifier = Modifier.size(48.dp))
        Text("Couldn't read receipt", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
        if (offline) {
            Text("You're offline. Check your connection and try again.", style = IosText.body, color = IosColors.warning, textAlign = TextAlign.Center)
        }
        Text(message, modifier = Modifier.testTag("receipt.error"), style = IosText.body, color = IosColors.neutral, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ReceiptPrimaryAction(
                "Try Again",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag("receipt.retry"),
            )
            ReceiptSecondaryAction(
                "Fill Manually",
                onClick = onManual,
                modifier = Modifier.fillMaxWidth().testTag("receipt.cancel"),
            )
        }
    }
}

@Composable
private fun ReceiptPrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    Box(
        modifier.fillMaxWidth().clip(shape).background(IosColors.spendOrange)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosText.headline, color = Color.Black, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReceiptSecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    Box(
        modifier.fillMaxWidth().then(subtleGlassModifier(shape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProgressState(text: String, detail: String? = null, onCancel: () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp).testTag("receipt.loading"), color = IosColors.spendOrange, strokeWidth = 3.dp)
            Text(text, style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            if (detail != null) Text(detail, color = IosColors.neutral, style = IosText.body, textAlign = TextAlign.Center)
            PillButton("Cancel", onClick = onCancel, filled = false)
        }
    }
}
