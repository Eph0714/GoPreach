package com.emfitsolutions.gopreach.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.emfitsolutions.gopreach.data.model.SupportingImage
import com.emfitsolutions.gopreach.data.model.SupportingImageType
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Full capture / preview-confirm / change / clear UI for one [SupportingImage]
 * (spec's "Interested Person – Supporting Place Image Capture" feature §1-§7,
 * §10). Self-contained: manages the camera permission request, the captured-
 * but-not-yet-confirmed preview state, and the clear-confirmation dialog —
 * the caller only ever sees the two committed outcomes, [onImageConfirmed]
 * and [onClear].
 *
 * Replacement safety (spec §6): [onImageConfirmed] is only called *after* the
 * user taps "Use Photo" on the freshly-captured preview — the existing
 * [currentImage] is never touched until that happens, so a cancelled or
 * abandoned capture can never lose the photo that was already saved.
 */
@Composable
fun SupportingImageSection(
    currentImage: SupportingImage?,
    onImageConfirmed: (SupportingImage) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) pendingBitmap = bitmap
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(null) else permissionDenied = true
    }
    val launchCamera = {
        permissionDenied = false
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) takePicture.launch(null) else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Supporting Place Image", style = MaterialTheme.typography.titleSmall)

        val preview = pendingBitmap
        when {
            // Section 1.3-1.4 / 2.2-2.4: captured, not yet saved — Use Photo / Retake Photo.
            preview != null -> {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Captured supporting image preview",
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val encoded = compressToBase64Jpeg(preview)
                        onImageConfirmed(
                            SupportingImage(
                                type = currentImage?.type ?: SupportingImageType.HOUSE.name,
                                base64Jpeg = encoded,
                                capturedAt = System.currentTimeMillis(),
                            ),
                        )
                        pendingBitmap = null
                    }) { Text("Use Photo") }
                    OutlinedButton(onClick = { pendingBitmap = null; launchCamera() }) { Text("Retake Photo") }
                }
            }

            currentImage != null && currentImage.base64Jpeg.isNotBlank() -> {
                val decoded = remember(currentImage.base64Jpeg) { decodeBase64ToBitmap(currentImage.base64Jpeg) }
                if (decoded != null) {
                    Image(
                        bitmap = decoded.asImageBitmap(),
                        contentDescription = "Supporting image",
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = launchCamera) { Text("Change Image") }
                    TextButton(onClick = { showClearConfirm = true }) { Text("Clear Image") }
                }
            }

            else -> {
                Text("No supporting image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = launchCamera) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Capture Image")
                }
            }
        }

        if (permissionDenied) {
            Text(
                "Camera permission is required to capture a supporting image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Supporting Image?") },
            text = { Text("This will remove the saved image from this Interested Person record.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClear()
                }) { Text("Clear Image") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }
}

/** Downscales to at most 1024px on the long edge and JPEG-compresses at 60%
 * quality — small enough to comfortably fit inside Firestore's 1 MiB
 * per-document limit (typically well under 200KB in practice) while still
 * being a recognizable photo of a house/gate/landmark. */
private fun compressToBase64Jpeg(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 60): String {
    val scale = min(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    } else {
        bitmap
    }
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

private fun decodeBase64ToBitmap(base64Jpeg: String): Bitmap? = runCatching {
    val bytes = Base64.decode(base64Jpeg, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
