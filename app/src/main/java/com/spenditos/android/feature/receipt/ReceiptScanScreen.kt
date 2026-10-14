package com.spenditos.android.feature.receipt

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.quota.QuotaCopy
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.w
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReceiptScanScreen(
    onResult: (AddTransactionPrefill) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ReceiptScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openPro = com.spenditos.android.feature.pro.LocalOpenPro.current
    // A library pick lands in the camera's Retake / Use Photo preview, like iOS ScanReceiptView.
    var libraryPhoto by remember { mutableStateOf<Uri?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) libraryPhoto = uri
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

    if (state.step == ReceiptScanStep.CHOOSE) {
        ReceiptCamera(
            onBack = {
                viewModel.cancel()
                onBack()
            },
            onPickLibrary = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            libraryPhoto = libraryPhoto,
            onLibraryPhotoConfirmed = { uri ->
                libraryPhoto = null
                viewModel.onLibraryPhotoSelected(uri)
            },
            onLibraryPhotoDismissed = { libraryPhoto = null },
            onUsePhoto = { file ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    viewModel.setPendingCameraUri(uri.toString())
                    viewModel.onCameraCaptureResult(true)
                } catch (_: Exception) {
                    file.delete()
                    viewModel.imagePreparationFailed()
                }
            },
            onCaptureError = { viewModel.imagePreparationFailed() },
        )
        return
    }

    IosScreen {
        val progressTitle = when (state.step) {
            ReceiptScanStep.PREPARING -> "Preparing photo..."
            ReceiptScanStep.UPLOADING -> "Uploading receipt..."
            ReceiptScanStep.ANALYZING -> "Analyzing receipt..."
            else -> null
        }
        if (progressTitle != null) {
            ProgressState(
                title = progressTitle,
                detail = if (state.step == ReceiptScanStep.ANALYZING) "This may take a few seconds" else null,
                backdropPath = state.backdropPath,
                onCancel = { viewModel.cancel(); onBack() },
            )
            return@IosScreen
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                Text("Scan receipt", style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center)
            }
            when (state.step) {
                ReceiptScanStep.FAILURE -> {
                    ReceiptFailure(
                        offline = state.offline,
                        message = state.error ?: "Couldn't read that receipt. You can enter it yourself.",
                        onRetry = viewModel::retry,
                        // iOS "Fill Manually" opens the empty add form rather than just leaving.
                        onManual = { viewModel.cancel(); onResult(AddTransactionPrefill()) },
                    )
                }
                ReceiptScanStep.QUOTA -> {
                    ReceiptQuotaExceeded(
                        message = state.error ?: QuotaCopy.SCAN_EXHAUSTED,
                        onUpgrade = { openPro?.invoke() },
                        onManual = { viewModel.cancel(); onResult(AddTransactionPrefill()) },
                    )
                }
                else -> Unit
            }
        }
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

/** iOS quotaExceededView (ScanReceiptView.swift): lock, copy, upgrade and manual fallback. */
@Composable
private fun ReceiptQuotaExceeded(message: String, onUpgrade: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            SfSymbols.icon("lock.fill"),
            contentDescription = null,
            tint = IosColors.spendOrange.copy(alpha = 0.8f),
            modifier = Modifier.size(67.2.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Monthly Limit Reached", style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text(message, style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Spacing.xl))
            Text(QuotaCopy.SCAN_UPGRADE, style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radius.l))
                    .background(IosColors.spendOrange)
                    .clickable(role = Role.Button, onClick = onUpgrade)
                    .testTag("receipt.upgrade")
                    .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.4.dp))
                Text("Upgrade to Pro", style = IosText.headline, color = Color.White)
            }
            Box(
                Modifier
                    .then(subtleGlassModifier())
                    .clickable(role = Role.Button, onClick = onManual)
                    .testTag("receipt.fillManually")
                    .padding(horizontal = Spacing.xl, vertical = Spacing.m),
            ) {
                Text("Fill Manually", style = IosText.headline, color = IosColors.textPrimary)
            }
        }
        Spacer(Modifier.weight(1f))
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

/** iOS analyzingView: the photo behind the progress card, blurred under a scrim, card centered. */
@Composable
private fun ProgressState(title: String, detail: String?, backdropPath: String?, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (backdropPath != null) ReceiptBackdrop(backdropPath)
        Column(
            Modifier.align(Alignment.Center).then(glassCardModifier(RoundedCornerShape(Radius.l))).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp).testTag("receipt.loading"), color = IosColors.spendOrange, strokeWidth = 3.dp)
            Text(title, style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            if (detail != null) Text(detail, color = IosColors.neutral, style = IosText.body, textAlign = TextAlign.Center)
            PillButton("Cancel", onClick = onCancel, filled = false)
        }
    }
}

/** Decodes the scan's in-flight image for the backdrop; the ViewModel still owns and deletes the file. */
@Composable
private fun ReceiptBackdrop(path: String) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }?.asImageBitmap()
    }
    Box(Modifier.fillMaxSize()) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(6.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
    }
}
