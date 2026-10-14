package com.spenditos.android.feature.receipt

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.feature.scan.ScanToast
import com.spenditos.android.feature.scan.ScanToastStyle
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/** Types the Files picker offers: PDF, CSV, Excel and images (iOS `fileTypes`). */
private val FILE_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "image/*",
)

/**
 * iOS `ScanReceiptView`: the Scan screen. The camera and one Upload button (Photo Library | Files); everything is read
 * by the Scan engine, which decides whether it is one receipt ([onResult] opens the prefilled add form) or a list
 * ([onOpenReview] opens its review). A scan that is still being read can be left running; Home picks it up.
 */
@Composable
fun ReceiptScanScreen(
    onResult: (AddTransactionPrefill) -> Unit,
    onOpenReview: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ReceiptScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openPro = com.spenditos.android.feature.pro.LocalOpenPro.current

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.onPhotosPicked(uris)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onFilesPicked(uris)
    }
    val pickPhotos = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReceiptScanEvent.Result -> onResult(event.prefill)
                is ReceiptScanEvent.OpenReview -> onOpenReview(event.scanId)
                ReceiptScanEvent.Close -> onBack()
            }
        }
    }
    BackHandler { viewModel.onBack() }

    Box(Modifier.fillMaxSize()) {
        when (state.step) {
            ReceiptScanStep.CHOOSE -> ReceiptCamera(
                onBack = viewModel::onBack,
                onPickLibrary = pickPhotos,
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
                uploadSection = {
                    ScanUploadSection(
                        onPhotoLibrary = pickPhotos,
                        onFiles = { filePicker.launch(FILE_MIME_TYPES) },
                    )
                },
            )
            ReceiptScanStep.PREPARING -> IosScreen {
                Column(
                    Modifier.fillMaxSize().padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp).testTag("receipt.loading"), color = IosColors.spendOrange, strokeWidth = 3.dp)
                    Spacer(Modifier.height(Spacing.l))
                    Text("Preparing photo…", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
                }
            }
            ReceiptScanStep.READING -> IosScreen {
                ReadingContent(
                    reading = state.reading,
                    onNotifyMe = viewModel::leaveInBackground,
                    onCancel = viewModel::cancelReading,
                )
            }
            ReceiptScanStep.FAILURE -> IosScreen {
                ReceiptFailure(
                    offline = state.offline,
                    message = state.error ?: ReceiptScanViewModel.MANUAL_FALLBACK,
                    onRetry = viewModel::retry,
                    // iOS "Fill Manually" opens the empty add form rather than just leaving.
                    onManual = { viewModel.cancel(); onResult(AddTransactionPrefill()) },
                )
            }
            ReceiptScanStep.QUOTA -> IosScreen {
                ReceiptQuotaExceeded(
                    message = state.error ?: ReceiptScanViewModel.QUOTA_MESSAGE,
                    onUpgrade = { openPro?.invoke() },
                    onManual = { viewModel.cancel(); onResult(AddTransactionPrefill()) },
                )
            }
            ReceiptScanStep.DONE -> Box(Modifier.fillMaxSize())
        }
        ScanToast(
            text = state.toast,
            style = ScanToastStyle.INFO,
            onDismiss = viewModel::dismissToast,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** iOS `uploadSection`: the Upload card (menu: Photo Library | Files) and the line under it. */
@Composable
private fun ScanUploadSection(onPhotoLibrary: () -> Unit, onFiles: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.m, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(glassCardModifier())
                    .clickable(role = Role.Button) { menuOpen = true }
                    .padding(Spacing.m)
                    .heightIn(min = 44.dp)
                    .semantics { contentDescription = "Upload. Choose from Photo Library or Files" }
                    .testTag("scan.upload"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                IconChip(SfSymbols.icon("square.and.arrow.up"), IosColors.spendOrange, size = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Upload", style = IosText.headline, color = IosColors.textPrimary)
                    Text("Screenshots, receipts, PDF, CSV, Excel", style = IosText.subheadline, color = IosColors.textSecondary)
                }
                Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.4.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Photo Library", style = IosText.body) },
                    onClick = {
                        menuOpen = false
                        onPhotoLibrary()
                    },
                    modifier = Modifier.testTag("scan.upload.photos"),
                )
                DropdownMenuItem(
                    text = { Text("Files", style = IosText.body) },
                    onClick = {
                        menuOpen = false
                        onFiles()
                    },
                    modifier = Modifier.testTag("scan.upload.files"),
                )
            }
        }
        Text(
            "Any amount. Spenditos works out what's in it and shows you everything before saving.",
            style = IosText.caption,
            color = IosColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        )
    }
}

/** iOS `readingView`: upload progress, then documents read by the server; leave it running or cancel it. */
@Composable
private fun ReadingContent(reading: ScanReadingState, onNotifyMe: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            reading.title,
            style = IosText.title3.w(FontWeight.SemiBold),
            color = IosColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.screen).testTag("scan.reading.title"),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            val fraction = reading.fraction
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().testTag("scan.reading.bar"),
                    color = IosColors.spendOrange,
                )
            } else {
                CircularProgressIndicator(Modifier.size(28.dp).testTag("scan.reading.spinner"), color = IosColors.spendOrange, strokeWidth = 3.dp)
            }
            Text(
                reading.progressText,
                style = IosText.subheadline,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("scan.reading.progress"),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (reading.offersBackground && reading.scanId != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(Radius.m))
                        .background(IosColors.spendOrange)
                        .clickable(role = Role.Button, onClick = onNotifyMe)
                        .testTag("scan.notifyMe"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Notify me when it's ready", style = IosText.headline, color = Color.Black)
                }
            }
            Text(
                "Cancel",
                style = IosText.body,
                color = IosColors.neutral,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(horizontal = Spacing.m, vertical = 10.dp)
                    .testTag("scan.reading.cancel"),
            )
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
        Text("Couldn't read your scan", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
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
            Text(message, style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Spacing.xl).testTag("receipt.quota.message"))
            Text("Upgrade to Pro to keep scanning.", style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center)
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
