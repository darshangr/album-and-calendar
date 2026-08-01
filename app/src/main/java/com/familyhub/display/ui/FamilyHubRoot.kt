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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyhub.display.data.AppContainer
import com.familyhub.display.ui.calendar.CalendarScreen
import com.familyhub.display.ui.photos.PhotoSlideshowScreen
import com.familyhub.display.ui.settings.SettingsScreen
import com.familyhub.display.ui.sleep.SleepScreen
import com.familyhub.display.ui.sleep.isWithinSleepWindow
import com.familyhub.display.ui.viewmodel.DisplayMode
import com.familyhub.display.ui.viewmodel.MainViewModel
import com.familyhub.display.ui.viewmodel.CalendarViewModel
import com.familyhub.display.ui.viewmodel.PhotoViewModel
import com.familyhub.display.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import java.time.LocalTime

private enum class OverlayScreen {
    NONE,
    SETTINGS,
}

private const val SLEEP_OVERRIDE_MS = 5 * 60 * 1000L

@Composable
fun FamilyHubRoot(container: AppContainer) {
    val factory = remember(container) { ViewModelFactory(container) }
    val mainViewModel: MainViewModel = viewModel(factory = factory)
    val calendarViewModel: CalendarViewModel = viewModel(factory = factory)
    val photoViewModel: PhotoViewModel = viewModel(factory = factory)

    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf(OverlayScreen.NONE) }
    var calendarModalOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    // Suppress the idle->photos timer while Settings or any calendar dialog is open.
    LaunchedEffect(overlay, calendarModalOpen) {
        mainViewModel.setModalOpen(overlay == OverlayScreen.SETTINGS || calendarModalOpen)
    }

    // Night sleep state: recompute the current time on a ticker; a tap during
    // sleep temporarily wakes the screen until `sleepOverrideUntil`.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sleepOverrideUntil by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(20_000L)
        }
    }

    val settings = mainState.settings
    val isSleeping = settings.nightSleepEnabled &&
        nowMillis >= sleepOverrideUntil &&
        isWithinSleepWindow(LocalTime.now(), settings.sleepStartHour, settings.wakeHour)

    // Immersive fullscreen: hide status + navigation bars when enabled.
    LaunchedEffect(settings.immersiveFullscreen) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (settings.immersiveFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Dim the backlight to near-off during sleep and restore afterwards.
    DisposableEffect(isSleeping) {
        val window = (context as? android.app.Activity)?.window
        window?.let {
            val attrs = it.attributes
            attrs.screenBrightness = if (isSleeping) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            it.attributes = attrs
        }
        onDispose {
            window?.let {
                val attrs = it.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = attrs
            }
        }
    }

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

    if (isSleeping) {
        SleepScreen(
            wakeHour = settings.wakeHour,
            onWake = { sleepOverrideUntil = System.currentTimeMillis() + SLEEP_OVERRIDE_MS },
        )
        return
    }

    when (overlay) {
        OverlayScreen.SETTINGS -> {
            SettingsScreen(
                settings = mainState.settings,
                googleAccount = mainState.googleAccount,
                members = mainState.members,
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
                onLockToApp = {
                    runCatching { (context as? android.app.Activity)?.startLockTask() }
                },
                onAddMember = mainViewModel::addMember,
                onDeleteMember = mainViewModel::deleteMember,
                onRecolorMember = mainViewModel::updateMemberColor,
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
                            onModalChanged = { calendarModalOpen = it },
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
