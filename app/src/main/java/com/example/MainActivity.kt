package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CaptureOrientationMode
import com.example.ui.AppScreenDestination
import com.example.ui.CineCameraViewModel
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.CinemaVideoPlayerScreen
import com.example.ui.screens.VideoGalleryScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CineProCameraApp()
            }
        }
    }
}

@Composable
fun CineProCameraApp(
    viewModel: CineCameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Detect physical 90-degree device/emulator rotation via OrientationEventListener
    // so the UI layout stays anchored in place (never blocking the preview) while buttons rotate 90° in-place!
    DisposableEffect(context) {
        var lastDetectedMode: CaptureOrientationMode? = null
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val newMode = when (orientation) {
                    in 60..120, in 240..300 -> CaptureOrientationMode.LANDSCAPE
                    in 0..30, in 150..210, in 330..360 -> CaptureOrientationMode.PORTRAIT
                    else -> null
                }
                if (newMode != null && newMode != lastDetectedMode) {
                    lastDetectedMode = newMode
                    viewModel.onDevicePhysicalOrientationChanged(newMode)
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    when (uiState.currentScreen) {
        AppScreenDestination.CAMERA -> {
            CameraScreen(
                uiState = uiState,
                hasCameraPermission = hasCameraPermission,
                hasAudioPermission = hasAudioPermission,
                onRequestPermissions = {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                },
                onBindCamera = { lifecycleOwner, previewView ->
                    viewModel.bindCameraToPreview(lifecycleOwner, previewView)
                },
                onViewfinderTap = { previewView, x, y ->
                    viewModel.onViewfinderTapFocus(previewView, x, y)
                },
                onSelectTab = viewModel::selectControlTab,
                onSelectOrientationMode = viewModel::selectOrientationMode,
                onToggleOrientationMode = viewModel::toggleOrientationMode,
                onToggleRotateScreenWithMode = viewModel::setRotateScreenWithMode,
                onSelectFps = viewModel::selectFps,
                onApply180ShutterRule = viewModel::apply180DegreeShutterForCurrentFps,
                onSetExposureMode = viewModel::setExposureMode,
                onSelectShutterSpeed = viewModel::selectShutterSpeed,
                onSelectIso = viewModel::selectIso,
                onSetFocusMode = viewModel::setFocusMode,
                onManualFocusDistanceChange = viewModel::setManualFocusDistance,
                onSelectResolution = viewModel::selectResolution,
                onSelectAspectRatio = viewModel::selectAspectRatio,
                onToggleMasterGuides = viewModel::toggleMasterGuides,
                onToggleGuide = viewModel::toggleGuide,
                onEnableAllGuides = viewModel::enableAllGuides,
                onDisableAllGuides = viewModel::disableAllGuides,
                onToggleLens = viewModel::toggleLensFacing,
                onToggleTorch = viewModel::toggleTorch,
                onToggleAudio = viewModel::toggleAudioEnabled,
                onSetZoom = viewModel::setZoom,
                onToggleRecording = {
                    viewModel.toggleRecording(hasAudioPermission)
                },
                onOpenGallery = {
                    viewModel.navigateToScreen(AppScreenDestination.GALLERY)
                },
                onOpenLatestVideo = {
                    val latest = uiState.recordedVideos.firstOrNull()
                    if (latest != null) {
                        viewModel.openVideoPlayer(latest)
                    } else {
                        viewModel.navigateToScreen(AppScreenDestination.GALLERY)
                    }
                }
            )
        }

        AppScreenDestination.GALLERY -> {
            VideoGalleryScreen(
                uiState = uiState,
                onBackToCamera = {
                    viewModel.navigateToScreen(AppScreenDestination.CAMERA)
                },
                onPlayVideo = { video ->
                    viewModel.openVideoPlayer(video)
                },
                onDeleteVideo = { video ->
                    viewModel.deleteVideo(video)
                },
                onShareVideo = { video ->
                    viewModel.shareVideo(video)
                },
                onGenerateTestClip = {
                    viewModel.generateTestClipWithCurrentSettings()
                }
            )
        }

        AppScreenDestination.PLAYER -> {
            val activeVideo = uiState.activePlaybackVideo ?: uiState.recordedVideos.firstOrNull()
            if (activeVideo != null) {
                CinemaVideoPlayerScreen(
                    video = activeVideo,
                    onClosePlayer = {
                        viewModel.closeVideoPlayer()
                    },
                    onShareVideo = { video ->
                        viewModel.shareVideo(video)
                    },
                    onDeleteVideo = { video ->
                        viewModel.deleteVideo(video)
                    }
                )
            } else {
                viewModel.navigateToScreen(AppScreenDestination.GALLERY)
            }
        }
    }
}
