package com.spenditos.android.feature.receipt

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.io.File

internal class BoundReceiptCamera(
    val imageCapture: ImageCapture,
    val provider: ProcessCameraProvider,
)

/**
 * iOS `cameraView` / `permissionDeniedView` / `previewView` (ScanReceiptView.swift).
 * Live preview is edge to edge on black; controls sit in the safe area.
 */
@Composable
internal fun ReceiptCamera(
    onBack: () -> Unit,
    onPickLibrary: () -> Unit,
    onUsePhoto: (File) -> Unit,
    onCaptureError: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf<Boolean?>(null) }
    var noCamera by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf<BoundReceiptCamera?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }
    val onCaptureErrorState by rememberUpdatedState(onCaptureError)
    // A photo taken but not used (Back from the preview) must not stay in the cache.
    var handedOver by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (!handedOver) capturedFile?.delete() }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) permissionGranted = true else permission.launch(Manifest.permission.CAMERA)
    }

    val photo = capturedFile
    val showDenied = permissionGranted == false || noCamera
    val showLive = permissionGranted == true && !noCamera && photo == null

    Box(
        Modifier
            .fillMaxSize()
            .background(if (photo == null && !showDenied) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        when {
            photo != null -> ReceiptPhotoPreview(
                file = photo,
                onRetake = {
                    photo.delete()
                    capturedFile = null
                    bound = null
                },
                onUse = {
                    handedOver = true
                    onUsePhoto(photo)
                },
            )
            showDenied -> ReceiptPermissionDenied(
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
                onCancel = onBack,
                onPickLibrary = if (noCamera) onPickLibrary else null,
            )
            else -> {
                if (showLive) {
                    ReceiptViewfinder(
                        onBound = { bound = it },
                        onUnavailable = { noCamera = true },
                    )
                }
                if (bound == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        color = Color.White,
                        strokeWidth = 4.dp,
                    )
                } else {
                    ReceiptCameraChrome(
                        onBack = onBack,
                        onPickLibrary = onPickLibrary,
                        onShutter = {
                            val camera = bound ?: return@ReceiptCameraChrome
                            if (capturing) return@ReceiptCameraChrome
                            val created = try {
                                val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
                                File.createTempFile("receipt-camera-", ".jpg", directory)
                            } catch (_: Exception) {
                                onCaptureErrorState()
                                return@ReceiptCameraChrome
                            }
                            capturing = true
                            val executor = ContextCompat.getMainExecutor(context)
                            camera.imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(created).build(),
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        camera.provider.unbindAll()
                                        capturing = false
                                        bound = null
                                        capturedFile = created
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        created.delete()
                                        capturing = false
                                        onCaptureErrorState()
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptViewfinder(
    onBound: (BoundReceiptCamera) -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onBoundState by rememberUpdatedState(onBound)
    val onUnavailableState by rememberUpdatedState(onUnavailable)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var disposed = false
        future.addListener(
            {
                val provider = try {
                    future.get()
                } catch (_: Exception) {
                    if (!disposed) onUnavailableState()
                    return@addListener
                }
                val preview = Preview.Builder().build()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                    .build()
                try {
                    if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        if (!disposed) onUnavailableState()
                        return@addListener
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                } catch (_: Exception) {
                    if (!disposed) onUnavailableState()
                    return@addListener
                }
                if (disposed) {
                    provider.unbindAll()
                    return@addListener
                }
                onBoundState(BoundReceiptCamera(capture, provider))
                preview.setSurfaceProvider { request ->
                    if (disposed) request.willNotProvideSurface() else surfaceRequest = request
                }
            },
            executor,
        )
        onDispose {
            disposed = true
            surfaceRequest = null
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ReceiptCameraChrome(
    onBack: () -> Unit,
    onPickLibrary: () -> Unit,
    onShutter: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.s),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = Spacing.m)
                    .heightIn(min = 44.dp)
                    .testTag("receipt.cancel")
                    .semantics { contentDescription = "Back" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    SfSymbols.icon("chevron.left"),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.2.dp),
                )
                Text("Back", style = IosText.callout.w(FontWeight.Medium), color = Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Position receipt within frame",
            style = IosText.callout,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = Spacing.l)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ReceiptGalleryButton(onPickLibrary)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(72.dp)
                        .testTag("receipt.takePhoto")
                        .semantics { contentDescription = "Take photo" }
                        .clickable(role = Role.Button, onClick = onShutter),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(72.dp).border(4.dp, Color.White, CircleShape))
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun ReceiptGalleryButton(onPickLibrary: () -> Unit, onDark: Boolean = true) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (onDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onPickLibrary)
            .testTag("receipt.chooseFromLibrary")
            .semantics { contentDescription = "Choose from photo library" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SfSymbols.icon("photo.on.rectangle"),
            contentDescription = null,
            tint = if (onDark) Color.White else IosColors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ReceiptPermissionDenied(
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
    onPickLibrary: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Icon(
                SfSymbols.icon("camera.fill"),
                contentDescription = null,
                tint = IosColors.neutral,
                modifier = Modifier.size(67.2.dp),
            )
            Text(
                "Camera Access Required",
                style = IosText.title3.w(FontWeight.SemiBold),
                color = IosColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                "Allow camera access in Settings to scan receipts.",
                style = IosText.callout,
                color = IosColors.neutral,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                "Open Settings",
                style = IosText.headline,
                color = Color.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.m))
                    .background(IosColors.spendOrange)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .padding(horizontal = 32.dp, vertical = Spacing.m),
            )
            Text(
                "Cancel",
                style = IosText.body,
                color = IosColors.neutral,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(vertical = Spacing.s)
                    .testTag("receipt.cancel"),
            )
            if (onPickLibrary != null) {
                ReceiptGalleryButton(onPickLibrary, onDark = false)
            }
        }
    }
}

@Composable
private fun ReceiptPhotoPreview(
    file: File,
    onRetake: () -> Unit,
    onUse: () -> Unit,
) {
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        bitmap = decoded?.asImageBitmap()
    }
    Box(Modifier.fillMaxSize()) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            val shape = RoundedCornerShape(Radius.m)
            Box(
                Modifier
                    .weight(1f)
                    .then(subtleGlassModifier(shape))
                    .clickable(role = Role.Button, onClick = onRetake)
                    .testTag("receipt.retake"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Retake",
                    style = IosText.headline,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m),
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(IosColors.spendOrange)
                    .clickable(role = Role.Button, onClick = onUse)
                    .testTag("receipt.usePhoto"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Use Photo",
                    style = IosText.headline,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

