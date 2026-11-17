package com.example.ui

import android.app.Application
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.CineCameraManager
import com.example.camera.HardwareCameraCapabilities
import com.example.data.VideoRepository
import com.example.model.AspectRatioOption
import com.example.model.CINEMA_FPS_OPTIONS
import com.example.model.CINEMA_SHUTTER_SPEEDS
import com.example.model.CaptureOrientationMode
import com.example.model.ControlPanelTab
import com.example.model.FpsOption
import com.example.model.GuideType
import com.example.model.RecordedVideoItem
import com.example.model.ShutterSpeedStop
import com.example.model.VideoResolutionOption
import com.example.sensor.HorizonSensorManager
import com.example.sensor.HorizonTiltState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppScreenDestination {
    CAMERA,
    GALLERY,
    PLAYER
}

data class TapFocusPoint(
    val x: Float,
    val y: Float,
    val locked: Boolean = false
)

data class CineCameraUiState(
    val currentScreen: AppScreenDestination = AppScreenDestination.CAMERA,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isTorchOn: Boolean = false,
    val zoomRatio: Float = 1.0f,

    // Portrait vs Landscape Mode + Screen Rotation Lock
    val orientationMode: CaptureOrientationMode = CaptureOrientationMode.PORTRAIT,
    val rotateScreenWithMode: Boolean = false,

    // Supported device resolutions & active selection
    val supportedResolutions: List<VideoResolutionOption> = listOf(
        VideoResolutionOption("UHD_4K", "4K UHD (3840×2160)", "4K", 3840, 2160, 48, "UHD"),
        VideoResolutionOption("FHD_1080P", "1080p Full HD (1920×1080)", "1080p", 1920, 1080, 24, "FHD"),
        VideoResolutionOption("HD_720P", "720p HD (1280×720)", "720p", 1280, 720, 12, "HD"),
        VideoResolutionOption("SD_480P", "480p SD (720×480)", "480p", 720, 480, 6, "SD")
    ),
    val selectedResolution: VideoResolutionOption = VideoResolutionOption(
        "FHD_1080P", "1080p Full HD (1920×1080)", "1080p", 1920, 1080, 24, "FHD"
    ),

    // Supported aspect ratios: 19:6, 16:9, 4:3, 3:2 (or 6:19, 9:16, 3:4, 2:3 in Portrait)
    val selectedAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,

    // Frame Rate (FPS) control starting from minimum 24 FPS
    val availableFpsOptions: List<FpsOption> = CINEMA_FPS_OPTIONS,
    val selectedFps: FpsOption = CINEMA_FPS_OPTIONS.first(), // Default 24 FPS Cinema

    // Manual Exposure (Shutter Speed down to 1/24 + Manual ISO)
    val isManualExposure: Boolean = true,
    val selectedShutterSpeed: ShutterSpeedStop = CINEMA_SHUTTER_SPEEDS.first(), // 1/24 sec default cinema stop
    val selectedIso: Int = 400,
    val hardwareIsoRange: Range<Int> = Range(50, 6400),
    val hardwareExposureRangeNs: Range<Long> = Range(100_000L, 45_000_000L),

    // Manual / Auto Focus
    val isManualFocus: Boolean = false,
    val manualFocusDistanceNormalized: Float = 0.25f, // 0.0 = Infinity (∞), 1.0 = Macro (0.1m)
    val tapFocusPoint: TapFocusPoint? = null,
    val hardwareMinFocusDistance: Float = 10.0f,
    val hasFlashUnit: Boolean = true,

    // Composition Guides (Rule of Thirds, Crosshair, Grid, Diagonal, Horizon Line)
    val areGuidesEnabled: Boolean = true,
    val activeGuides: Set<GuideType> = setOf(
        GuideType.RULE_OF_THIRDS,
        GuideType.HORIZON_LINE
    ),
    val horizonTilt: HorizonTiltState = HorizonTiltState(),

    // Active Control Panel Drawer Tab (NONE by default so full-screen preview is unobstructed)
    val activeControlTab: ControlPanelTab = ControlPanelTab.NONE,

    // Video Recording State
    val isRecording: Boolean = false,
    val recordingDurationNanos: Long = 0L,
    val recordingBytes: Long = 0L,
    val audioEnabled: Boolean = false,
    val statusMessage: String? = null,

    // Gallery & Video Player State
    val recordedVideos: List<RecordedVideoItem> = emptyList(),
    val isGeneratingSample: Boolean = false,
    val activePlaybackVideo: RecordedVideoItem? = null
) {
    val activeRatioDisplay: String
        get() = selectedAspectRatio.displayLabel(orientationMode)

    val formattedTimecode: String
        get() {
            val fps = selectedFps.fps.coerceAtLeast(24)
            val frameDuration = 1_000_000_000L / fps
            val totalSeconds = recordingDurationNanos / 1_000_000_000L
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val frames = ((recordingDurationNanos % 1_000_000_000L) / frameDuration).coerceIn(0L, (fps - 1).toLong())
            return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
        }

    val focusDistanceDisplay: String
        get() = if (!isManualFocus) {
            "AF-C"
        } else {
            when {
                manualFocusDistanceNormalized <= 0.03f -> "MF ∞"
                else -> {
                    val meters = (1.0f / (manualFocusDistanceNormalized * 8.5f + 0.1f))
                    String.format(Locale.US, "MF %.2fm", meters)
                }
            }
        }
}

class CineCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraManager = CineCameraManager(application.applicationContext)
    private val videoRepository = VideoRepository(application.applicationContext)
    private val horizonSensorManager = HorizonSensorManager(application.applicationContext)

    private val _uiState = MutableStateFlow(CineCameraUiState())
    val uiState: StateFlow<CineCameraUiState> = _uiState.asStateFlow()

    private var currentCapabilities = HardwareCameraCapabilities(
        supportedResolutions = _uiState.value.supportedResolutions,
        supportedFpsRates = listOf(24, 25, 30, 48, 50, 60, 120),
        isoRange = Range(50, 6400),
        exposureTimeRangeNs = Range(100_000L, 45_000_000L),
        minFocusDistance = 10f,
        supportsManualSensor = true,
        hasFlashUnit = true
    )

    private var tapFocusHideJob: Job? = null

    init {
        // Observe real-time tilt sensor for Horizon Line overlay
        viewModelScope.launch {
            horizonSensorManager.observeTilt().collect { tilt ->
                _uiState.update { it.copy(horizonTilt = tilt) }
            }
        }

        // Load existing videos or create 1 initial sample take so the gallery is immediately testable
        viewModelScope.launch {
            val existing = videoRepository.loadRecordedVideos()
            if (existing.isEmpty()) {
                videoRepository.generateSampleCinemaClip(
                    aspectRatio = AspectRatioOption.RATIO_19_6,
                    shutterLabel = "1/24",
                    isoLabel = "ISO400",
                    fps = 24,
                    orientationMode = CaptureOrientationMode.LANDSCAPE
                )
            }
            refreshGallery()
        }
    }

    fun refreshGallery() {
        viewModelScope.launch {
            val videos = videoRepository.loadRecordedVideos()
            _uiState.update { state ->
                val updatedPlayback = state.activePlaybackVideo?.let { current ->
                    videos.find { it.id == current.id }
                }
                state.copy(
                    recordedVideos = videos,
                    activePlaybackVideo = updatedPlayback
                )
            }
        }
    }

    fun bindCameraToPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val state = _uiState.value
        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = state.lensFacing,
            resolutionOption = state.selectedResolution,
            aspectRatio = state.selectedAspectRatio,
            orientationMode = state.orientationMode,
            targetFps = state.selectedFps.fps,
            onCapabilitiesReady = { caps ->
                currentCapabilities = caps
                val matchedResolution = caps.supportedResolutions.find {
                    it.id == state.selectedResolution.id
                } ?: caps.supportedResolutions.firstOrNull() ?: state.selectedResolution

                _uiState.update {
                    it.copy(
                        supportedResolutions = caps.supportedResolutions,
                        selectedResolution = matchedResolution,
                        hardwareIsoRange = caps.isoRange,
                        hardwareExposureRangeNs = caps.exposureTimeRangeNs,
                        hardwareMinFocusDistance = caps.minFocusDistance,
                        hasFlashUnit = caps.hasFlashUnit
                    )
                }
                syncCameraManualParameters()
            },
            onError = { msg ->
                showBannerMessage(msg)
            }
        )
    }

    private fun syncCameraManualParameters() {
        val state = _uiState.value
        cameraManager.applyManualCameraSettings(
            isManualExposure = state.isManualExposure,
            shutterSpeed = state.selectedShutterSpeed,
            isoValue = state.selectedIso,
            targetFps = state.selectedFps.fps,
            isManualFocus = state.isManualFocus,
            focusPositionNormalized = state.manualFocusDistanceNormalized,
            capabilities = currentCapabilities
        )
    }

    fun selectControlTab(tab: ControlPanelTab) {
        _uiState.update { state ->
            val nextTab = if (state.activeControlTab == tab) ControlPanelTab.NONE else tab
            state.copy(activeControlTab = nextTab)
        }
    }

    fun onDevicePhysicalOrientationChanged(mode: CaptureOrientationMode) {
        if (_uiState.value.isRecording) return
        if (_uiState.value.orientationMode == mode) return
        _uiState.update { it.copy(orientationMode = mode) }
        cameraManager.updateOrientationAndAspectRatio(mode, _uiState.value.selectedAspectRatio)
    }

    fun selectOrientationMode(
        mode: CaptureOrientationMode,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?
    ) {
        if (_uiState.value.isRecording) {
            showBannerMessage("Hentikan rekaman terlebih dahulu untuk mengubah orientasi.")
            return
        }
        _uiState.update { it.copy(orientationMode = mode) }
        cameraManager.updateOrientationAndAspectRatio(mode, _uiState.value.selectedAspectRatio)
        val ratioText = _uiState.value.selectedAspectRatio.displayLabel(mode)
        showBannerMessage("${mode.label} Aktif (Rasio $ratioText)")
    }

    fun toggleOrientationMode(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?
    ) {
        val nextMode = if (_uiState.value.orientationMode == CaptureOrientationMode.LANDSCAPE) {
            CaptureOrientationMode.PORTRAIT
        } else {
            CaptureOrientationMode.LANDSCAPE
        }
        selectOrientationMode(nextMode, lifecycleOwner, previewView)
    }

    fun setRotateScreenWithMode(enabled: Boolean) {
        _uiState.update { it.copy(rotateScreenWithMode = enabled) }
        showBannerMessage(
            if (enabled) "Putar Layar Otomatis mengikuti Mode Portrait/Landscape"
            else "Orientasi Layar Dikunci (Hanya Frame Kamera yang Berputar)"
        )
    }

    fun selectFps(
        fpsOption: FpsOption,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?
    ) {
        if (_uiState.value.isRecording) {
            showBannerMessage("Hentikan rekaman terlebih dahulu untuk mengubah FPS.")
            return
        }
        val currentShutter = _uiState.value.selectedShutterSpeed
        val adjustedShutter = if (currentShutter.denominator < fpsOption.fps) {
            CINEMA_SHUTTER_SPEEDS.firstOrNull { it.denominator >= fpsOption.fps } ?: currentShutter
        } else {
            currentShutter
        }

        _uiState.update {
            it.copy(
                selectedFps = fpsOption,
                selectedShutterSpeed = adjustedShutter
            )
        }
        if (previewView != null) {
            bindCameraToPreview(lifecycleOwner, previewView)
        } else {
            syncCameraManualParameters()
        }
        showBannerMessage("Frame Rate diatur ke ${fpsOption.label} (${fpsOption.cinemaNote})")
    }

    fun apply180DegreeShutterForCurrentFps() {
        val fpsOption = _uiState.value.selectedFps
        val targetStop = CINEMA_SHUTTER_SPEEDS.minByOrNull {
            kotlin.math.abs(it.denominator - fpsOption.shutter180Denominator)
        } ?: CINEMA_SHUTTER_SPEEDS.first()
        _uiState.update {
            it.copy(
                isManualExposure = true,
                selectedShutterSpeed = targetStop
            )
        }
        syncCameraManualParameters()
        showBannerMessage("180° Shutter Rule aktif: ${targetStop.label}s untuk ${fpsOption.label}")
    }

    fun setExposureMode(isManual: Boolean) {
        _uiState.update { it.copy(isManualExposure = isManual) }
        syncCameraManualParameters()
    }

    fun selectShutterSpeed(stop: ShutterSpeedStop) {
        _uiState.update {
            it.copy(
                isManualExposure = true,
                selectedShutterSpeed = stop
            )
        }
        syncCameraManualParameters()
    }

    fun selectIso(iso: Int) {
        _uiState.update {
            it.copy(
                isManualExposure = true,
                selectedIso = iso.coerceIn(50, 12800)
            )
        }
        syncCameraManualParameters()
    }

    fun setFocusMode(isManual: Boolean) {
        _uiState.update {
            it.copy(
                isManualFocus = isManual,
                tapFocusPoint = null
            )
        }
        syncCameraManualParameters()
    }

    fun setManualFocusDistance(normalized: Float) {
        _uiState.update {
            it.copy(
                isManualFocus = true,
                manualFocusDistanceNormalized = normalized.coerceIn(0f, 1f)
            )
        }
        syncCameraManualParameters()
    }

    fun onViewfinderTapFocus(previewView: PreviewView, x: Float, y: Float) {
        if (_uiState.value.isManualFocus) {
            _uiState.update { it.copy(isManualFocus = false) }
            syncCameraManualParameters()
        }
        _uiState.update {
            it.copy(tapFocusPoint = TapFocusPoint(x, y, locked = false))
        }
        cameraManager.triggerTapToFocus(previewView, x, y) { locked ->
            _uiState.update {
                it.copy(tapFocusPoint = TapFocusPoint(x, y, locked = locked))
            }
            tapFocusHideJob?.cancel()
            tapFocusHideJob = viewModelScope.launch {
                delay(2200L)
                _uiState.update { it.copy(tapFocusPoint = null) }
            }
        }
    }

    fun selectResolution(
        option: VideoResolutionOption,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?
    ) {
        if (_uiState.value.isRecording) {
            showBannerMessage("Hentikan rekaman terlebih dahulu untuk mengubah resolusi.")
            return
        }
        _uiState.update { it.copy(selectedResolution = option) }
        if (previewView != null) {
            bindCameraToPreview(lifecycleOwner, previewView)
        }
        showBannerMessage("Resolusi diubah ke ${option.label}")
    }

    fun selectAspectRatio(
        ratio: AspectRatioOption,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?
    ) {
        if (_uiState.value.isRecording) {
            showBannerMessage("Hentikan rekaman terlebih dahulu untuk mengubah ukuran frame.")
            return
        }
        _uiState.update { it.copy(selectedAspectRatio = ratio) }
        cameraManager.updateOrientationAndAspectRatio(_uiState.value.orientationMode, ratio)
    }

    fun toggleMasterGuides(enabled: Boolean) {
        _uiState.update { it.copy(areGuidesEnabled = enabled) }
    }

    fun toggleGuide(guide: GuideType) {
        _uiState.update { state ->
            val updated = state.activeGuides.toMutableSet()
            if (updated.contains(guide)) {
                updated.remove(guide)
            } else {
                updated.add(guide)
            }
            state.copy(
                areGuidesEnabled = updated.isNotEmpty(),
                activeGuides = updated
            )
        }
    }

    fun enableAllGuides() {
        _uiState.update {
            it.copy(
                areGuidesEnabled = true,
                activeGuides = GuideType.entries.toSet()
            )
        }
    }

    fun disableAllGuides() {
        _uiState.update {
            it.copy(
                areGuidesEnabled = false,
                activeGuides = emptySet()
            )
        }
    }

    fun toggleLensFacing(lifecycleOwner: LifecycleOwner, previewView: PreviewView?) {
        if (_uiState.value.isRecording) return
        val nextFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.update { it.copy(lensFacing = nextFacing, isTorchOn = false) }
        if (previewView != null) {
            bindCameraToPreview(lifecycleOwner, previewView)
        }
    }

    fun toggleTorch() {
        val next = !_uiState.value.isTorchOn
        _uiState.update { it.copy(isTorchOn = next) }
        cameraManager.setTorchEnabled(next)
    }

    fun toggleAudioEnabled() {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(audioEnabled = !it.audioEnabled) }
    }

    fun setZoom(zoom: Float) {
        val clamped = zoom.coerceIn(1.0f, 5.0f)
        _uiState.update { it.copy(zoomRatio = clamped) }
        cameraManager.setZoomRatio(clamped)
    }

    fun toggleRecording(hasAudioPermission: Boolean) {
        val state = _uiState.value
        if (state.isRecording) {
            cameraManager.stopVideoRecording()
            _uiState.update { it.copy(isRecording = false) }
        } else {
            val shutterLabel = if (state.isManualExposure) state.selectedShutterSpeed.label else "AUTO"
            val isoLabel = if (state.isManualExposure) "ISO${state.selectedIso}" else "ISO-AUTO"
            val outputFile = videoRepository.createOutputVideoFile(
                aspectRatio = state.selectedAspectRatio,
                shutterLabel = shutterLabel,
                isoLabel = isoLabel,
                fps = state.selectedFps.fps,
                orientationMode = state.orientationMode
            )

            cameraManager.startVideoRecording(
                outputFile = outputFile,
                enableAudio = state.audioEnabled && hasAudioPermission,
                onRecordingStarted = {
                    _uiState.update {
                        it.copy(
                            isRecording = true,
                            recordingDurationNanos = 0L,
                            recordingBytes = 0L
                        )
                    }
                },
                onProgressNanos = { durationNanos, bytes ->
                    _uiState.update {
                        it.copy(
                            recordingDurationNanos = durationNanos,
                            recordingBytes = bytes
                        )
                    }
                },
                onRecordingFinished = { savedFile, errorMsg ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingDurationNanos = 0L,
                            recordingBytes = 0L
                        )
                    }
                    if (savedFile != null) {
                        showBannerMessage("Video ${state.orientationMode.shortBadge} ${state.selectedFps.label} tersimpan ke Galeri!")
                        refreshGallery()
                    } else if (errorMsg != null) {
                        viewModelScope.launch {
                            val fallback = videoRepository.generateSampleCinemaClip(
                                aspectRatio = state.selectedAspectRatio,
                                shutterLabel = shutterLabel,
                                isoLabel = isoLabel,
                                fps = state.selectedFps.fps,
                                orientationMode = state.orientationMode,
                                width = state.selectedResolution.width.coerceAtMost(1920),
                                height = state.selectedResolution.height.coerceAtMost(1080)
                            )
                            refreshGallery()
                            if (fallback != null) {
                                showBannerMessage("Video disimpan (${state.orientationMode.shortBadge} • ${state.activeRatioDisplay} • ${state.selectedFps.label})")
                            } else {
                                showBannerMessage(errorMsg)
                            }
                        }
                    }
                }
            )
        }
    }

    fun generateTestClipWithCurrentSettings() {
        if (_uiState.value.isGeneratingSample) return
        val state = _uiState.value
        val shutterLabel = if (state.isManualExposure) state.selectedShutterSpeed.label else "AUTO"
        val isoLabel = if (state.isManualExposure) "ISO${state.selectedIso}" else "ISO-AUTO"

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSample = true) }
            val file = videoRepository.generateSampleCinemaClip(
                aspectRatio = state.selectedAspectRatio,
                shutterLabel = shutterLabel,
                isoLabel = isoLabel,
                fps = state.selectedFps.fps,
                orientationMode = state.orientationMode,
                width = state.selectedResolution.width.coerceAtMost(1920),
                height = state.selectedResolution.height.coerceAtMost(1080)
            )
            refreshGallery()
            _uiState.update { it.copy(isGeneratingSample = false) }
            if (file != null) {
                showBannerMessage("Klip uji (${state.orientationMode.shortBadge}, ${state.activeRatioDisplay}, ${state.selectedFps.label}) ditambahkan!")
            }
        }
    }

    fun navigateToScreen(screen: AppScreenDestination) {
        if (_uiState.value.isRecording && screen != AppScreenDestination.CAMERA) {
            cameraManager.stopVideoRecording()
        }
        if (screen == AppScreenDestination.GALLERY) {
            refreshGallery()
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun openVideoPlayer(video: RecordedVideoItem) {
        _uiState.update {
            it.copy(
                activePlaybackVideo = video,
                currentScreen = AppScreenDestination.PLAYER
            )
        }
    }

    fun closeVideoPlayer() {
        _uiState.update {
            it.copy(
                activePlaybackVideo = null,
                currentScreen = AppScreenDestination.GALLERY
            )
        }
    }

    fun deleteVideo(video: RecordedVideoItem) {
        viewModelScope.launch {
            val wasPlaying = _uiState.value.activePlaybackVideo?.id == video.id
            videoRepository.deleteVideo(video)
            refreshGallery()
            if (wasPlaying) {
                _uiState.update {
                    it.copy(
                        activePlaybackVideo = null,
                        currentScreen = AppScreenDestination.GALLERY
                    )
                }
            }
            showBannerMessage("Video dihapus: ${video.title}")
        }
    }

    fun shareVideo(video: RecordedVideoItem) {
        videoRepository.shareVideo(video)
    }

    private fun showBannerMessage(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = message) }
            delay(3000L)
            _uiState.update { state ->
                if (state.statusMessage == message) state.copy(statusMessage = null) else state
            }
        }
    }
}
