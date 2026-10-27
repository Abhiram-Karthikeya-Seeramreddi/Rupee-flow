package com.rupeeflow.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Receipt attach flow:
 *  - Gallery: Android's system photo picker. No storage permission is needed; the picker
 *    grants access to just the chosen image.
 *  - Camera: the phone's camera app writes into our private cache via a FileProvider URI,
 *    so no CAMERA permission is needed either.
 * The image is copied into private app storage (downscaled, rotated upright) on a
 * background thread, so the UI never freezes and a bad image can't crash the app.
 */
class ReceiptPicker internal constructor(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
    val busy: () -> Boolean,
)

@Composable
fun rememberReceiptPicker(txId: String, onSaved: (String) -> Unit, onError: (String) -> Unit): ReceiptPicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<String?>(null) }

    fun process(uri: Uri, deleteAfter: File? = null) {
        busy = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { saveReceipt(ctx, uri, txId) }
            deleteAfter?.delete()
            busy = false
            if (path != null) onSaved(path) else onError("Couldn't attach that image. Try another one.")
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) process(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCameraFile?.let { File(it) }
        pendingCameraFile = null
        if (ok && f != null && f.length() > 0) process(Uri.fromFile(f), deleteAfter = f) else f?.delete()
    }

    return remember(txId) {
        ReceiptPicker(
            pickFromGallery = {
                try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (e: Exception) { onError("No photo picker found on this phone.") }
            },
            takePhoto = {
                try {
                    val dir = File(ctx.cacheDir, "receipts-tmp").apply { mkdirs() }
                    val f = File(dir, "capture-${System.currentTimeMillis()}.jpg")
                    pendingCameraFile = f.absolutePath
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                    camera.launch(uri)
                } catch (e: Exception) {
                    pendingCameraFile = null
                    onError("No camera app found on this phone.")
                }
            },
            busy = { busy },
        )
    }
}

/** Decodes, rotates upright, downsizes to ≤1600px and stores as JPEG in app storage. */
fun saveReceipt(ctx: Context, uri: Uri, txId: String): String? = try {
    val resolver = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 || bounds.outHeight / (sample * 2) >= 1600) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (decoded == null) null else {
            val rotation = runCatching {
                resolver.openInputStream(uri)?.use { s ->
                    when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)
            val upright = if (rotation == 0f) decoded else
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
            val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
            val out = File(dir, "$txId-${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out.absolutePath
        }
    }
} catch (t: Throwable) {
    null
}

fun decodeSampled(path: String, target: Int): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var sample = 1
    while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (t: Throwable) {
    null
}

/** Small thumbnail, decoded off the main thread. Tap opens the full receipt. */
@Composable
fun ReceiptThumb(path: String, sizeDp: Int = 56) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(path) { img = withContext(Dispatchers.IO) { decodeSampled(path, 200)?.asImageBitmap() } }
    img?.let {
        Image(it, "Receipt", Modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp)).clickable { open = true }, contentScale = ContentScale.Crop)
    }
    if (open) ReceiptViewer(path) { open = false }
}

@Composable
fun ReceiptViewer(path: String, onClose: () -> Unit) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { img = withContext(Dispatchers.IO) { decodeSampled(path, 1600)?.asImageBitmap() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            img?.let { Image(it, "Receipt", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}
