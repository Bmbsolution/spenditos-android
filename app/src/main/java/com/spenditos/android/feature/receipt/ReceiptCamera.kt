package com.spenditos.android.feature.receipt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BoundReceiptCamera(
    val imageCapture: ImageCapture,
    val provider: ProcessCameraProvider,
)

/**
 * iOS `cameraScreen` / `cameraView` / `permissionDeniedView` / `previewView` (ScanReceiptView.swift): a top bar,
 * the live camera in a rounded card with the gallery shortcut, shutter and hint on top of it, and the Upload
 * card below ([uploadSection]). A captured photo is confirmed on a full-screen Retake / Use Photo preview.
 */
@Composable
internal fun ReceiptCamera(
    onBack: () -> Unit,
    onPickLibrary: () -> Unit,
    onUsePhoto: (File) -> Unit,
    onCaptureError: () -> Unit,
    uploadSection: @Composable () -> Unit,
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
    // Set in onDispose: a JPEG that lands after the user left has no owner and is deleted on arrival.
    val disposed = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            if (!handedOver) capturedFile?.delete()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) permissionGranted = true else permission.launch(Manifest.permission.CAMERA)
    }

    // "Open Settings" -> grant -> return must clear the denied screen: re-check on every ON_RESUME.
    // Only ever promote to granted: ON_RESUME also fires before the system dialog is answered,
    // and the launcher result callback already records a denial.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) {
                permissionGranted = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val photo = capturedFile
    val showDenied = permissionGranted == false || noCamera
    val showLive = permissionGranted == true && !noCamera && photo == null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (photo != null) {
            ReceiptPhotoPreview(
                source = photo,
                loadBitmap = { decodePreviewBitmap(context, photo)?.asImageBitmap() },
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
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                ScanTopBar(onBack)
                // iOS cameraView: a radiusL card inside the screen padding, black while the camera is live.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen)
                        .padding(top = Spacing.s)
                        .clip(RoundedCornerShape(Radius.l))
                        .background(if (bound != null && !showDenied) Color.Black else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        showDenied -> ReceiptPermissionDenied(
                            onOpenSettings = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                                context.startActivity(intent)
                            },
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
                                    modifier = Modifier.size(28.8.dp),
                                    color = IosColors.neutral,
                                    strokeWidth = 3.dp,
                                )
                            } else {
                                ReceiptCameraChrome(
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
                                                    capturing = false
                                                    if (disposed.get()) {
                                                        // Back was pressed while the JPEG was being written:
                                                        // nobody is left to own the file, so delete it now.
                                                        created.delete()
                                                        return
                                                    }
                                                    camera.provider.unbindAll()
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
                uploadSection()
            }
        }
    }
}

/** iOS `topBar`: a centered "Scan" title and a Back button in the primary label color (44pt tall). */
@Composable
private fun ScanTopBar(onBack: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s).padding(top = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Scan",
            style = IosText.headline,
            color = IosColors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            Modifier
                .align(Alignment.CenterStart)
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
                tint = IosColors.textPrimary,
                modifier = Modifier.size(19.2.dp),
            )
            Text("Back", style = IosText.callout.w(FontWeight.Medium), color = IosColors.textPrimary)
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

/** The hint capsule and the gallery shortcut / shutter row on top of the live camera (iOS `cameraView`). */
@Composable
private fun ReceiptCameraChrome(
    onPickLibrary: () -> Unit,
    onShutter: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Text(
            "Point at a receipt",
            style = IosText.callout,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = Spacing.m)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(bottom = Spacing.m),
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

/** The gallery shortcut opens the same multi-select picker as Upload > Photo Library. */
@Composable
private fun ReceiptGalleryButton(onPickLibrary: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(role = Role.Button, onClick = onPickLibrary)
            .testTag("receipt.chooseFromLibrary")
            .semantics { contentDescription = "Choose from photo library" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SfSymbols.icon("photo.on.rectangle"),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** iOS `permissionDeniedView` (with Upload on offer): the Upload card below still works without the camera. */
@Composable
private fun ReceiptPermissionDenied(onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            SfSymbols.icon("camera.fill"),
            contentDescription = null,
            tint = IosColors.neutral,
            modifier = Modifier.size(52.8.dp),
        )
        Text(
            "Camera Access Required",
            style = IosText.title3.w(FontWeight.SemiBold),
            color = IosColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            "Allow camera access in Settings to scan receipts. You can still upload below.",
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
                .padding(horizontal = 32.dp, vertical = Spacing.m)
                .testTag("receipt.openSettings"),
        )
    }
}

@Composable
private fun ReceiptPhotoPreview(
    source: Any,
    loadBitmap: suspend () -> ImageBitmap?,
    onRetake: () -> Unit,
    onUse: () -> Unit,
) {
    var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source) {
        bitmap = loadBitmap()
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

/** Long-edge target of the Retake / Use Photo preview; a 48 MP pick decodes to at most this. */
internal const val PREVIEW_MAX_LONG_SIDE = 2048

/** inSampleSize for the preview decode: halves until the long edge is near [PREVIEW_MAX_LONG_SIDE]. */
internal fun previewSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= PREVIEW_MAX_LONG_SIDE) sample *= 2
    return sample
}

/**
 * Preview decode for the Retake / Use Photo screen: bounds first, then sampled to about
 * [PREVIEW_MAX_LONG_SIDE], EXIF-rotated, on Dispatchers.IO; null on any failure (the screen then
 * shows no image) instead of a crash or an OOM from a full-size decode on the main thread.
 */
internal suspend fun decodePreviewBitmap(context: Context, source: Any): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        when (source) {
            is File -> BitmapFactory.decodeFile(source.absolutePath, bounds)
            is Uri -> context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Preview image could not be decoded" }
        val orientation = previewExifOrientation(context, source)
        val options = BitmapFactory.Options().apply { inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = when (source) {
            is File -> BitmapFactory.decodeFile(source.absolutePath, options)
            is Uri -> context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            else -> null
        } ?: throw IllegalArgumentException("Preview image could not be decoded")
        val oriented = orientPreview(decoded, orientation)
        val fitted = fitPreview(oriented)
        if (oriented !== fitted) oriented.recycle()
        if (decoded !== oriented && decoded !== fitted) decoded.recycle()
        fitted
    }.getOrNull()
}

private fun previewExifOrientation(context: Context, source: Any): Int = runCatching {
    when (source) {
        is Uri -> context.contentResolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        is File -> ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        else -> null
    } ?: ExifInterface.ORIENTATION_NORMAL
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/** Same orientation mapping as ReceiptImageProcessor: portrait receipts must not show sideways. */
private fun orientPreview(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    val rotate = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { matrix.setScale(-1f, 1f); false }
        ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.setRotate(180f); false }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setScale(1f, -1f); false }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f); false }
        ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.setRotate(90f); false }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f); false }
        ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.setRotate(-90f); false }
        else -> true
    }
    if (rotate) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun fitPreview(bitmap: Bitmap): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= PREVIEW_MAX_LONG_SIDE) return bitmap
    val ratio = PREVIEW_MAX_LONG_SIDE.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
        (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
}

