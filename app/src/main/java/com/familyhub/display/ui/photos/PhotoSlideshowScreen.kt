package com.familyhub.display.ui.photos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.PhotoItem
import com.familyhub.display.ui.viewmodel.PhotoViewModel
import com.familyhub.display.util.detectDoubleTap
import kotlinx.coroutines.delay

@Composable
fun PhotoSlideshowScreen(
    viewModel: PhotoViewModel,
    defaultDurationSeconds: Int,
    onDoubleTapToCalendar: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photos = uiState.photos
    val currentPhoto = photos.getOrNull(uiState.currentIndex)

    LaunchedEffect(uiState.currentIndex, photos.size) {
        if (photos.isEmpty()) return@LaunchedEffect
        val durationSeconds = currentPhoto?.displayDurationSeconds ?: defaultDurationSeconds
        delay(durationSeconds * 1000L)
        viewModel.advancePhoto()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .detectDoubleTap(onDoubleTapToCalendar)
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (photos.isEmpty()) {
            EmptyPhotosState(onAddPhoto = viewModel::showAddDialog)
        } else {
            Crossfade(
                targetState = uiState.currentIndex,
                animationSpec = tween(durationMillis = 700),
                label = "photo_crossfade",
            ) { index ->
                val photo = photos[index]
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.caption.ifBlank { "Family photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (!currentPhoto?.caption.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .fillMaxWidth(0.7f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    ),
                ) {
                    Text(
                        text = currentPhoto?.caption.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Text(
                text = "Double tap to open calendar",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (uiState.showAddDialog) {
        PhotoEditorDialog(
            initialPhoto = uiState.editingPhoto,
            defaultDurationSeconds = defaultDurationSeconds,
            onDismiss = viewModel::dismissDialog,
            onSave = viewModel::savePhoto,
            onDelete = viewModel::deletePhoto,
        )
    }
}

@Composable
private fun EmptyPhotosState(onAddPhoto: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Add photos to start the slideshow",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAddPhoto) {
            Text("Add photo")
        }
    }
}

@Composable
private fun PhotoEditorDialog(
    initialPhoto: PhotoItem?,
    defaultDurationSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (PhotoItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var uri by remember(initialPhoto) { mutableStateOf(initialPhoto?.uri.orEmpty()) }
    var caption by remember(initialPhoto) { mutableStateOf(initialPhoto?.caption.orEmpty()) }
    var duration by remember(initialPhoto) {
        mutableIntStateOf(initialPhoto?.displayDurationSeconds ?: defaultDurationSeconds)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { pickedUri: Uri? ->
        pickedUri?.let { uri = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPhoto == null) "Add photo" else "Edit photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Image URI or URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Display duration: ${duration}s")
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = it.toInt() },
                    valueRange = 3f..60f,
                    steps = 57,
                )
                FilledTonalButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) {
                    Text("Pick from device")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uri.isBlank()) return@Button
                    onSave(
                        PhotoItem(
                            id = initialPhoto?.id ?: 0L,
                            uri = uri.trim(),
                            caption = caption.trim(),
                            displayDurationSeconds = duration,
                            sortOrder = initialPhoto?.sortOrder ?: 0,
                            source = initialPhoto?.source ?: ContentSource.LOCAL,
                            remoteId = initialPhoto?.remoteId,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (initialPhoto != null) {
                    TextButton(onClick = { onDelete(initialPhoto.id) }) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
