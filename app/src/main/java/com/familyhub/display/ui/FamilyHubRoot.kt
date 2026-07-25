package com.familyhub.display.ui

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyhub.display.data.AppContainer
import com.familyhub.display.ui.calendar.CalendarScreen
import com.familyhub.display.ui.photos.PhotoSlideshowScreen
import com.familyhub.display.ui.settings.SettingsScreen
import com.familyhub.display.ui.viewmodel.DisplayMode
import com.familyhub.display.ui.viewmodel.MainViewModel
import com.familyhub.display.ui.viewmodel.CalendarViewModel
import com.familyhub.display.ui.viewmodel.PhotoViewModel
import com.familyhub.display.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay

private enum class OverlayScreen {
    NONE,
    SETTINGS,
}

@Composable
fun FamilyHubRoot(container: AppContainer) {
    val factory = remember(container) { ViewModelFactory(container) }
    val mainViewModel: MainViewModel = viewModel(factory = factory)
    val calendarViewModel: CalendarViewModel = viewModel(factory = factory)
    val photoViewModel: PhotoViewModel = viewModel(factory = factory)

    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf(OverlayScreen.NONE) }
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        mainViewModel.handleGoogleSignInResult(result.data)
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        mainViewModel.onConsentResult()
    }

    LaunchedEffect(mainState.pendingConsentIntent) {
        mainState.pendingConsentIntent?.let { intent ->
            mainViewModel.clearPendingConsent()
            consentLauncher.launch(intent)
        }
    }

    DisposableEffect(mainState.settings.keepScreenOn) {
        val activity = context as? android.app.Activity
        if (mainState.settings.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            mainViewModel.checkIdleTimeout()
        }
    }

    when (overlay) {
        OverlayScreen.SETTINGS -> {
            SettingsScreen(
                settings = mainState.settings,
                googleAccount = mainState.googleAccount,
                onBack = { overlay = OverlayScreen.NONE },
                onSave = { updated ->
                    mainViewModel.updateSettings { updated }
                    overlay = OverlayScreen.NONE
                },
                onSync = mainViewModel::syncNow,
                onSwitchToPhotosNow = {
                    overlay = OverlayScreen.NONE
                    mainViewModel.switchToPhotos()
                },
                onSignInWithGoogle = {
                    googleSignInLauncher.launch(mainViewModel.signInWithGoogle())
                },
                onSignOutGoogle = mainViewModel::signOutGoogle,
            )
        }
        OverlayScreen.NONE -> {
            when (mainState.mode) {
                DisplayMode.CALENDAR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { mainViewModel.onUserInteraction() })
                            },
                    ) {
                        CalendarScreen(
                            viewModel = calendarViewModel,
                            onOpenSettings = { overlay = OverlayScreen.SETTINGS },
                            onSync = mainViewModel::syncNow,
                            onStartSlideshow = mainViewModel::switchToPhotos,
                            onUserInteraction = mainViewModel::onUserInteraction,
                            syncMessage = mainState.syncMessage,
                            onDismissSyncMessage = mainViewModel::clearSyncMessage,
                            isSyncing = mainState.isSyncing,
                        )
                    }
                }
                DisplayMode.PHOTOS -> {
                    PhotoSlideshowScreen(
                        viewModel = photoViewModel,
                        defaultDurationSeconds = mainState.settings.defaultPhotoDurationSeconds,
                        onDoubleTapToCalendar = mainViewModel::switchToCalendarFromDoubleTap,
                    )
                }
            }
        }
    }
}
