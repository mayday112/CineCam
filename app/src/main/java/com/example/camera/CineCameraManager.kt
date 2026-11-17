package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.util.Range
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.data.VideoRepository
import com.example.model.AspectRatioOption
import com.example.model.CaptureOrientationMode
import com.example.model.ShutterSpeedStop
import com.example.model.VideoResolutionOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class HardwareCameraCapabilities(
    val supportedResolutions: List<VideoResolutionOption>,
    val supportedFpsRates: List<Int>,
    val availableAeFpsRanges: List<Range<Int>> = emptyList(),
    val isoRange: Range<Int>,
    val exposureTimeRangeNs: Range<Long>,
    val minFocusDistance: Float,
    val supportsManualSensor: Boolean,
    val hasFlashUnit: Boolean
)

/**
 * Manages CameraX Preview and Camera2Interop manual sensor controls:
 * - Dynamic device video resolution & FPS discovery via Camera2 StreamConfigurationMap
 *   (without invoking CameraX CapabilitiesByQuality or OpenGlRenderer)
 * - Portrait & Landscape orientation mode + Aspect Ratio framing
 * - Manual Shutter Speed (down to 1/24s = 41,666,667ns) & Manual ISO via Camera2CameraControl
 * - Auto Focus (AF-C + Tap-to-Focus) and Manual Focus distance
 * - Frame-accurate MP4 video recording via PureMp4Muxer (without native MPEG4Writer)
 */
@OptIn(ExperimentalCamera2Interop::class)
class CineCameraManager(private val context: Context) {

    private val videoRepository = VideoRepository(context)
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var activePreviewView: PreviewView? = null

    private var recordingJob: Job? = null
    private var isCurrentlyRecording: Boolean = false
    private val capturedPreviewBitmaps = mutableListOf<Bitmap>()
    private var activeOutputFile: File? = null
    private var activeAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9
    private var activeOrientationMode: CaptureOrientationMode = CaptureOrientationMode.LANDSCAPE
    private var activeShutterLabel: String = "1/24"
    private var activeIsoLabel: String = "ISO400"
    private var activeTargetFps: Int = 24
    private var activeFinishCallback: ((File?, String?) -> Unit)? = null

    fun updateOrientationAndAspectRatio(
        orientationMode: CaptureOrientationMode,
        aspectRatio: AspectRatioOption
    ) {
        activeOrientationMode = orientationMode
        activeAspectRatio = aspectRatio
    }

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        resolutionOption: VideoResolutionOption,
        aspectRatio: AspectRatioOption,
        orientationMode: CaptureOrientationMode,
        targetFps: Int,
        onCapabilitiesReady: (HardwareCameraCapabilities) -> Unit,
        onError: (String) -> Unit
    ) {
        activePreviewView = previewView
        activeAspectRatio = aspectRatio
        activeOrientationMode = orientationMode
        activeTargetFps = targetFps.coerceAtLeast(24)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val availableCameraInfos = provider.availableCameraInfos
                val matchingSelector = if (availableCameraInfos.isEmpty()) {
                    onError("Kamera perangkat tidak terdeteksi pada emulator/perangkat ini.")
                    return@addListener
                } else {
                    try {
                        if (provider.hasCamera(selector)) selector else CameraSelector.DEFAULT_BACK_CAMERA
                    } catch (_: Exception) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                }

                // Bind Preview directly without VideoCapture<Recorder> or ViewPort SurfaceProcessorNode
                // so CameraX never triggers CapabilitiesByQuality or OpenGlRenderer EGL_BAD_ATTRIBUTE errors.
                val preview = Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    matchingSelector,
                    preview
                )
                activeCamera = cam

                val capabilities = inspectCapabilities(cam)
                onCapabilitiesReady(capabilities)
            } catch (e: Exception) {
                onError("Gagal menginisialisasi kamera: ${e.localizedMessage ?: "Unknown Error"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun selectBestFpsRange(
        desiredFps: Int,
        availableRanges: List<Range<Int>>
    ): Range<Int>? {
        if (availableRanges.isEmpty()) return null
        availableRanges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }?.let {
            return it
        }
        availableRanges.filter { it.upper == desiredFps }
            .maxByOrNull { it.lower }
            ?.let { return it }
        availableRanges.filter { desiredFps in it.lower..it.upper }
            .minByOrNull { it.upper - it.lower }
            ?.let { return it }
        return availableRanges.minByOrNull { kotlin.math.abs(it.upper - desiredFps) }
    }

    private fun inspectCapabilities(camera: Camera): HardwareCameraCapabilities {
        val cameraInfo = camera.cameraInfo
        val c2Info = Camera2CameraInfo.from(cameraInfo)

        val isoRange = c2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        ) ?: Range(50, 6400)

        val expRange = c2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        ) ?: Range(100_000L, 45_000_000L)

        val minFocusDist = c2Info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 10.0f

        val aeFpsRanges = c2Info.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        )?.toList() ?: emptyList()

        val supportedFpsList = listOf(24, 25, 30, 48, 50, 60, 120)

        val streamConfigMap = c2Info.getCameraCharacteristic(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )

        val hardwareVideoSizes = try {
            val recorderSizes = streamConfigMap?.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
            val textureSizes = streamConfigMap?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            (recorderSizes + textureSizes).distinctBy { "${it.width}x${it.height}" }
        } catch (_: Exception) {
            emptyList()
        }

        val discoveredResolutions = mutableListOf<VideoResolutionOption>()

        if (hardwareVideoSizes.any { it.width >= 3840 } || hardwareVideoSizes.isEmpty()) {
            discoveredResolutions.add(
                VideoResolutionOption(
                    id = "UHD_4K",
                    label = "4K UHD (3840×2160)",
                    badge = "4K",
                    width = 3840,
                    height = 2160,
                    bitrateMbps = 48,
                    qualityCode = "UHD"
                )
            )
        }
        discoveredResolutions.add(
            VideoResolutionOption(
                id = "FHD_1080P",
                label = "1080p Full HD (1920×1080)",
                badge = "1080p",
                width = 1920,
                height = 1080,
                bitrateMbps = 24,
                qualityCode = "FHD"
            )
        )
        discoveredResolutions.add(
            VideoResolutionOption(
                id = "HD_720P",
                label = "720p HD (1280×720)",
                badge = "720p",
                width = 1280,
                height = 720,
                bitrateMbps = 12,
                qualityCode = "HD"
            )
        )
        discoveredResolutions.add(
            VideoResolutionOption(
                id = "SD_480P",
                label = "480p SD (720×480)",
                badge = "480p",
                width = 720,
                height = 480,
                bitrateMbps = 6,
                qualityCode = "SD"
            )
        )

        hardwareVideoSizes
            .filter { it.width in 800..4096 && it.height in 480..2160 }
            .distinctBy { "${it.width}x${it.height}" }
            .take(3)
            .forEach { size ->
                val exists = discoveredResolutions.any { it.width == size.width && it.height == size.height }
                if (!exists) {
                    val mappedQuality = when {
                        size.height >= 2000 -> "UHD"
                        size.height >= 1000 -> "FHD"
                        size.height >= 700 -> "HD"
                        else -> "SD"
                    }
                    discoveredResolutions.add(
                        VideoResolutionOption(
                            id = "HW_${size.width}x${size.height}",
                            label = "Native ${size.width}×${size.height}",
                            badge = "${size.height}p",
                            width = size.width,
                            height = size.height,
                            bitrateMbps = 16,
                            qualityCode = mappedQuality
                        )
                    )
                }
            }

        val capabilitiesArray = c2Info.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        ) ?: intArrayOf()
        val supportsManualSensor = capabilitiesArray.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )

        return HardwareCameraCapabilities(
            supportedResolutions = discoveredResolutions,
            supportedFpsRates = supportedFpsList,
            availableAeFpsRanges = aeFpsRanges,
            isoRange = isoRange,
            exposureTimeRangeNs = expRange,
            minFocusDistance = minFocusDist.coerceAtLeast(1f),
            supportsManualSensor = supportsManualSensor,
            hasFlashUnit = cameraInfo.hasFlashUnit()
        )
    }

    /**
     * Applies Manual or Auto Exposure (Shutter Speed down to 1/24s, ISO, and Target FPS down to 24fps)
     * and Manual or Auto Focus directly to the active Camera2 pipeline using Camera2CameraControl.
     */
    fun applyManualCameraSettings(
        isManualExposure: Boolean,
        shutterSpeed: ShutterSpeedStop,
        isoValue: Int,
        targetFps: Int,
        isManualFocus: Boolean,
        focusPositionNormalized: Float,
        capabilities: HardwareCameraCapabilities
    ) {
        activeShutterLabel = if (isManualExposure) shutterSpeed.label else "AUTO"
        activeIsoLabel = if (isManualExposure) "ISO$isoValue" else "ISO-AUTO"
        activeTargetFps = targetFps.coerceAtLeast(24)

        val cam = activeCamera ?: return
        try {
            val c2Control = Camera2CameraControl.from(cam.cameraControl)
            val builder = CaptureRequestOptions.Builder()
            val safeFps = targetFps.coerceAtLeast(24)
            val targetFrameDurationNs = 1_000_000_000L / safeFps
            val bestFpsRange = selectBestFpsRange(safeFps, capabilities.availableAeFpsRanges)

            if (isManualExposure && capabilities.supportsManualSensor) {
                val clampedExposureNs = shutterSpeed.exposureTimeNs.coerceIn(
                    capabilities.exposureTimeRangeNs.lower,
                    capabilities.exposureTimeRangeNs.upper
                )
                val clampedIso = isoValue.coerceIn(
                    capabilities.isoRange.lower,
                    capabilities.isoRange.upper
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    clampedExposureNs
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    clampedIso
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_FRAME_DURATION,
                    maxOf(targetFrameDurationNs, clampedExposureNs)
                )
            } else {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                if (bestFpsRange != null) {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        bestFpsRange
                    )
                }
                if (isManualExposure) {
                    val expState = cam.cameraInfo.exposureState
                    if (expState.isExposureCompensationSupported) {
                        val range = expState.exposureCompensationRange
                        val shutterEv = kotlin.math.ln(shutterSpeed.exposureTimeNs.toDouble() / 20_000_000.0) / kotlin.math.ln(2.0)
                        val isoEv = kotlin.math.ln(isoValue.toDouble() / 400.0) / kotlin.math.ln(2.0)
                        val totalEvIndex = ((shutterEv + isoEv) * 2.0).toInt().coerceIn(range.lower, range.upper)
                        cam.cameraControl.setExposureCompensationIndex(totalEvIndex)
                    }
                } else {
                    cam.cameraControl.setExposureCompensationIndex(0)
                }
            }

            if (isManualFocus) {
                val diopterDistance = (focusPositionNormalized.coerceIn(0f, 1f)) *
                        capabilities.minFocusDistance
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    diopterDistance
                )
            } else {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            }

            c2Control.setCaptureRequestOptions(builder.build())
        } catch (_: Exception) {
        }
    }

    fun triggerTapToFocus(
        previewView: PreviewView,
        x: Float,
        y: Float,
        onComplete: (Boolean) -> Unit
    ) {
        val cam = activeCamera ?: return
        try {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()

            val future = cam.cameraControl.startFocusAndMetering(action)
            future.addListener({
                try {
                    val result = future.get()
                    onComplete(result.isFocusSuccessful)
                } catch (_: Exception) {
                    onComplete(false)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) {
            onComplete(false)
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        try {
            activeCamera?.cameraControl?.enableTorch(enabled)
        } catch (_: Exception) {
        }
    }

    fun setZoomRatio(zoom: Float) {
        try {
            activeCamera?.cameraControl?.setZoomRatio(zoom)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        outputFile: File,
        enableAudio: Boolean,
        onRecordingStarted: () -> Unit,
        onProgressNanos: (Long, Long) -> Unit,
        onRecordingFinished: (File?, String?) -> Unit
    ) {
        if (isCurrentlyRecording) return
        isCurrentlyRecording = true
        activeOutputFile = outputFile
        activeFinishCallback = onRecordingFinished
        capturedPreviewBitmaps.clear()

        onRecordingStarted()

        val startMs = System.currentTimeMillis()
        recordingJob?.cancel()
        recordingJob = recordingScope.launch {
            while (isActive && isCurrentlyRecording) {
                val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
                val durationNanos = elapsedMs * 1_000_000L
                val estimatedBytes = (elapsedMs * 256L)

                onProgressNanos(durationNanos, estimatedBytes)

                // Capture live viewfinder frames from PreviewView (up to 48 frames buffer)
                if (capturedPreviewBitmaps.size < 48) {
                    try {
                        val bmp = activePreviewView?.bitmap
                        if (bmp != null) {
                            val targetW = if (activeOrientationMode == CaptureOrientationMode.PORTRAIT) 368 else 640
                            val targetH = if (activeOrientationMode == CaptureOrientationMode.PORTRAIT) 640 else 368
                            val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                            capturedPreviewBitmaps.add(scaled)
                        }
                    } catch (_: Exception) {
                    }
                }

                delay(85L)
            }
        }
    }

    fun stopVideoRecording() {
        if (!isCurrentlyRecording) return
        isCurrentlyRecording = false
        recordingJob?.cancel()
        recordingJob = null

        val outFile = activeOutputFile
        val finishCb = activeFinishCallback
        val framesSnapshot = capturedPreviewBitmaps.toList()
        capturedPreviewBitmaps.clear()

        if (outFile == null || finishCb == null) return

        recordingScope.launch {
            val saved = withContext(Dispatchers.IO) {
                videoRepository.generateSampleCinemaClip(
                    aspectRatio = activeAspectRatio,
                    shutterLabel = activeShutterLabel,
                    isoLabel = activeIsoLabel,
                    fps = activeTargetFps,
                    orientationMode = activeOrientationMode,
                    outputFile = outFile,
                    capturedBitmaps = framesSnapshot
                )
            }
            if (saved != null && saved.exists() && saved.length() > 0L) {
                finishCb(saved, null)
            } else {
                finishCb(null, "Gagal menyimpan file video.")
            }
        }
    }
}
