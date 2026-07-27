package com.familyhub.display.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.familyhub.display.data.google.GoogleAccountState
import com.familyhub.display.data.settings.AppSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    googleAccount: GoogleAccountState,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onSync: () -> Unit,
    onSwitchToPhotosNow: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onLockToApp: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SectionTitle("Google account")
            if (googleAccount.isSignedIn) {
                Text(
                    text = "Signed in as ${googleAccount.email ?: googleAccount.displayName ?: "Google account"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Calendar syncs from this account. Photos come from shared Google Drive folders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft.driveFolderId,
                    onValueChange = { draft = draft.copy(driveFolderId = it) },
                    label = { Text("Google Drive photo folders (one link/ID per line)") },
                    placeholder = { Text("https://drive.google.com/drive/folders/…") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Paste one or more Drive folder links (one per line). Subfolders are " +
                        "included automatically, so you can also point at a single parent folder. " +
                        "Share each folder with this account. Images are downloaded and cached for " +
                        "the slideshow; new photos appear on the next sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onSignOutGoogle, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            } else {
                Text(
                    text = "Sign in with your family Gmail account to sync Google Calendar events and " +
                        "photos from a shared Google Drive folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSignInWithGoogle, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
            }

            SectionTitle("Display behavior")
            Text("Switch to photos after inactivity: ${draft.calendarIdleTimeoutMinutes} min")
            Slider(
                value = draft.calendarIdleTimeoutMinutes.toFloat(),
                onValueChange = { draft = draft.copy(calendarIdleTimeoutMinutes = it.toInt()) },
                valueRange = 1f..60f,
                steps = 58,
            )

            Text("Default photo duration: ${draft.defaultPhotoDurationSeconds} sec")
            Slider(
                value = draft.defaultPhotoDurationSeconds.toFloat(),
                onValueChange = { draft = draft.copy(defaultPhotoDurationSeconds = it.toInt()) },
                valueRange = 3f..60f,
                steps = 57,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Keep screen on while plugged in")
                Switch(
                    checked = draft.keepScreenOn,
                    onCheckedChange = { draft = draft.copy(keepScreenOn = it) },
                )
            }

            SectionTitle("Fullscreen (kiosk)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hide status & navigation bars")
                Switch(
                    checked = draft.immersiveFullscreen,
                    onCheckedChange = { draft = draft.copy(immersiveFullscreen = it) },
                )
            }
            Text(
                text = "Hides the top notification bar and bottom buttons for a clean wall display. " +
                    "To fully lock the tablet to this app, use \"Lock to this app\" below (Android " +
                    "screen pinning — you can exit by holding Back + Recents).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onLockToApp, modifier = Modifier.fillMaxWidth()) {
                Text("Lock to this app (screen pinning)")
            }

            SectionTitle("Night sleep")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sleep the screen at night")
                Switch(
                    checked = draft.nightSleepEnabled,
                    onCheckedChange = { draft = draft.copy(nightSleepEnabled = it) },
                )
            }
            if (draft.nightSleepEnabled) {
                Text("Sleep at: %02d:00".format(draft.sleepStartHour % 24))
                Slider(
                    value = draft.sleepStartHour.toFloat(),
                    onValueChange = { draft = draft.copy(sleepStartHour = it.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                )
                Text("Wake at: %02d:00".format(draft.wakeHour % 24))
                Slider(
                    value = draft.wakeHour.toFloat(),
                    onValueChange = { draft = draft.copy(wakeHour = it.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                )
                Text(
                    text = "The screen goes dark during these hours. Tap it to wake for 5 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Custom cloud sync (optional)")
            Text(
                text = "Used only when Google is not signed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.cloudBaseUrl,
                onValueChange = { draft = draft.copy(cloudBaseUrl = it) },
                label = { Text("Cloud API base URL") },
                placeholder = { Text("https://api.example.com/v1/") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.cloudApiKey,
                onValueChange = { draft = draft.copy(cloudApiKey = it) },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Background sync interval: every ${draft.syncIntervalHours} hours")
            Slider(
                value = draft.syncIntervalHours.toFloat(),
                onValueChange = { draft = draft.copy(syncIntervalHours = it.toInt()) },
                valueRange = 1f..24f,
                steps = 22,
            )

            if (draft.lastSyncEpochMillis > 0L) {
                val formatted = Instant.ofEpochMilli(draft.lastSyncEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                Text(
                    text = "Last sync: $formatted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                Text("Sync now")
            }

            Button(
                onClick = onSwitchToPhotosNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Switch to photos now")
            }

            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save settings")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
