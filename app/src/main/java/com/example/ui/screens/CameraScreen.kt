package com.example.ui.screens

import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.AspectRatioOption
import com.example.model.CaptureOrientationMode
import com.example.model.ControlPanelTab
import com.example.model.FpsOption
import com.example.model.GuideType
import com.example.model.ShutterSpeedStop
import com.example.model.VideoResolutionOption
import com.example.ui.CineCameraUiState
import com.example.ui.components.CinemaGuideOverlays
import com.example.ui.components.ManualControlsDeck
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CineAmber
import com.example.ui.theme.GlassOverlay
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TallyRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(
    uiState: CineCameraUiState,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onBindCamera: (androidx.lifecycle.LifecycleOwner, PreviewView) -> Unit,
    onViewfinderTap: (PreviewView, Float, Float) -> Unit,
    onSelectTab: (ControlPanelTab) -> Unit,
    onSelectOrientationMode: (CaptureOrientationMode, androidx.lifecycle.LifecycleOwner, PreviewView?) -> Unit,
    onToggleOrientationMode: (androidx.lifecycle.LifecycleOwner, PreviewView?) -> Unit,
    onToggleRotateScreenWithMode: (Boolean) -> Unit,
    onSelectFps: (FpsOption, androidx.lifecycle.LifecycleOwner, PreviewView?) -> Unit,
    onApply180ShutterRule: () -> Unit,
    onSetExposureMode: (Boolean) -> Unit,
    onSelectShutterSpeed: (ShutterSpeedStop) -> Unit,
    onSelectIso: (Int) -> Unit,
    onSetFocusMode: (Boolean) -> Unit,
    onManualFocusDistanceChange: (Float) -> Unit,
    onSelectResolution: (VideoResolutionOption, androidx.lifecycle.LifecycleOwner, PreviewView?) -> Unit,
    onSelectAspectRatio: (AspectRatioOption, androidx.lifecycle.LifecycleOwner, PreviewView?) -> Unit,
    onToggleMasterGuides: (Boolean) -> Unit,
    onToggleGuide: (GuideType) -> Unit,
    onEnableAllGuides: () -> Unit,
    onDisableAllGuides: () -> Unit,
    onToggleLens: (androidx.lifecycle.LifecycleOwner, PreviewView?) -> Unit,
    onToggleTorch: () -> Unit,
    onToggleAudio: () -> Unit,
    onSetZoom: (Float) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenLatestVideo: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Smooth 90-degree in-place rotation for buttons & icons when in Landscape mode
    // (exactly like iOS Camera / Samsung One UI Camera: buttons stay at their fixed positions
    // and never move to block the preview)
    val uiRotation by animateFloatAsState(
        targetValue = if (uiState.orientationMode == CaptureOrientationMode.LANDSCAPE) 90f else 0f,
        animationSpec = tween(280),
        label = "camera_ui_in_place_rotation"
    )

    // Root is a 100% Full-Screen Box so the camera preview occupies the entire screen edge-to-edge
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // 1. FULL-SCREEN CAMERA PREVIEW (Edge-to-Edge)
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewRef = this
                        onBindCamera(lifecycleOwner, this)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            if (uiState.activeControlTab != ControlPanelTab.NONE) {
                                onSelectTab(ControlPanelTab.NONE)
                            }
                            previewViewRef?.let { pv ->
                                onViewfinderTap(pv, event.x, event.y)
                            }
                        }
                        true
                    }
                    .testTag("camera_preview_view")
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_cinema_lens_hero_1794878073090),
                contentDescription = "Camera lens preview background",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.45f)
            )
        }

        // 2. Full-Screen Composition Guides & Aspect Ratio Gate
        CinemaGuideOverlays(
            aspectRatio = uiState.selectedAspectRatio,
            orientationMode = uiState.orientationMode,
            areGuidesEnabled = uiState.areGuidesEnabled,
            activeGuides = uiState.activeGuides,
            horizonTilt = uiState.horizonTilt,
            tapFocusPoint = uiState.tapFocusPoint,
            isRecording = uiState.isRecording,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top Translucent Floating Bar (Flash, Mic, Timecode/Status, and Single Portrait/Landscape Toggle)
        CleanFloatingTopBar(
            uiState = uiState,
            uiRotation = uiRotation,
            onToggleTorch = onToggleTorch,
            onToggleAudio = onToggleAudio,
            onToggleOrientationMode = { onToggleOrientationMode(lifecycleOwner, previewViewRef) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 4. Floating Toast Pill (below top bar)
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.statusMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp)
        ) {
            Surface(
                color = GlassOverlay,
                shape = CircleShape,
                modifier = Modifier.border(0.5.dp, BorderSubtle, CircleShape)
            ) {
                Text(
                    text = uiState.statusMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CineAmber,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // 5. Permission Prompt Card (only if Camera permission is not granted yet)
        if (!hasCameraPermission) {
            PermissionPromptOverlay(
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 6. Bottom Translucent Floating Controls (Zoom Ring + Parameter Strip + Shutter Row)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optical Zoom Ring (1x, 1.5x, 2x, 3x) with in-place 90° rotation
            OpticalZoomRing(
                currentZoom = uiState.zoomRatio,
                uiRotation = uiRotation,
                onSetZoom = onSetZoom
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Pro Parameter Strip (ISO, SPEED, FOCUS, FPS, RATIO, RES, GRID)
            ManualControlsDeck(
                uiState = uiState,
                onSelectTab = onSelectTab,
                onSelectOrientationMode = { mode ->
                    onSelectOrientationMode(mode, lifecycleOwner, previewViewRef)
                },
                onToggleRotateScreenWithMode = onToggleRotateScreenWithMode,
                onSelectFps = { fpsOpt ->
                    onSelectFps(fpsOpt, lifecycleOwner, previewViewRef)
                },
                onApply180ShutterRule = onApply180ShutterRule,
                onSetExposureMode = onSetExposureMode,
                onSelectShutterSpeed = onSelectShutterSpeed,
                onSelectIso = onSelectIso,
                onSetFocusMode = onSetFocusMode,
                onManualFocusDistanceChange = onManualFocusDistanceChange,
                onSelectResolution = { res ->
                    onSelectResolution(res, lifecycleOwner, previewViewRef)
                },
                onSelectAspectRatio = { ratio ->
                    onSelectAspectRatio(ratio, lifecycleOwner, previewViewRef)
                },
                onToggleMasterGuides = onToggleMasterGuides,
                onToggleGuide = onToggleGuide,
                onEnableAllGuides = onEnableAllGuides,
                onDisableAllGuides = onDisableAllGuides
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Iconic 3-Item Shutter Row: [Galeri] — [Record Shutter] — [Putar Kamera]
            CleanShutterRow(
                uiState = uiState,
                uiRotation = uiRotation,
                hasCameraPermission = hasCameraPermission,
                onRequestPermissions = onRequestPermissions,
                onToggleRecording = onToggleRecording,
                onOpenGallery = onOpenGallery,
                onToggleLens = { onToggleLens(lifecycleOwner, previewViewRef) }
            )
        }
    }
}

/**
 * Floating translucent top bar with ZERO duplicated controls:
 * - Left: Flash & Audio Mic (rotate 90° in-place in Landscape)
 * - Center: Red Recording Timecode (`00:00:00`) when recording, or compact read-only telemetry summary pill
 * - Right: Single Portrait / Landscape toggle button (rotates 90° in-place in Landscape)
 */
@Composable
private fun CleanFloatingTopBar(
    uiState: CineCameraUiState,
    uiRotation: Float,
    onToggleTorch: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleOrientationMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_dot")
    val recDotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.55f),
                        Color.Transparent
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Flash & Audio Mic (rotate 90° in-place without moving position)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.hasFlashUnit) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .rotate(uiRotation)
                        .clip(CircleShape)
                        .background(if (uiState.isTorchOn) CineAmber else GlassOverlay)
                        .border(0.5.dp, BorderSubtle, CircleShape)
                        .clickable(onClick = onToggleTorch)
                        .testTag("toggle_torch_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Flash",
                        tint = if (uiState.isTorchOn) ObsidianBlack else TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .rotate(uiRotation)
                    .clip(CircleShape)
                    .background(if (!uiState.audioEnabled) TallyRed.copy(alpha = 0.28f) else GlassOverlay)
                    .border(0.5.dp, BorderSubtle, CircleShape)
                    .clickable(onClick = onToggleAudio)
                    .testTag("toggle_audio_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiState.audioEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = "Mikrofon",
                    tint = if (uiState.audioEnabled) TextPrimary else TallyRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Center: Recording Timecode Capsule OR subtle live status readout
        if (uiState.isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TallyRed)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .alpha(recDotAlpha)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = uiState.formattedTimecode,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("timecode_display")
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(GlassOverlay)
                    .border(0.5.dp, BorderSubtle, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("timecode_display")
            ) {
                Text(
                    text = "${uiState.selectedResolution.badge} · ${uiState.selectedFps.fps}fps · ${uiState.activeRatioDisplay}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right: SINGLE Portrait / Landscape Mode Switcher Button (rotates 90° in-place)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(GlassOverlay)
                .border(0.5.dp, BorderSubtle, CircleShape)
                .clickable(onClick = onToggleOrientationMode)
                .padding(horizontal = 11.dp, vertical = 7.dp)
                .testTag("quick_toggle_orientation_button")
        ) {
            Icon(
                imageVector = if (uiState.orientationMode == CaptureOrientationMode.PORTRAIT) {
                    Icons.Filled.StayCurrentPortrait
                } else {
                    Icons.Filled.StayCurrentLandscape
                },
                contentDescription = "Mode Portrait / Landscape",
                tint = CineAmber,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(uiRotation)
            )
            Text(
                text = if (uiState.orientationMode == CaptureOrientationMode.PORTRAIT) "Portrait" else "Landscape",
                style = MaterialTheme.typography.labelSmall,
                color = CineAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OpticalZoomRing(
    currentZoom: Float,
    uiRotation: Float,
    onSetZoom: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(GlassOverlay)
            .border(0.5.dp, BorderSubtle, CircleShape)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(1.0f, 1.5f, 2.0f, 3.0f).forEach { zoom ->
            val isSelected = abs(currentZoom - zoom) < 0.1f
            Box(
                modifier = Modifier
                    .size(if (isSelected) 32.dp else 28.dp)
                    .rotate(uiRotation)
                    .clip(CircleShape)
                    .background(if (isSelected) SlateElevated else Color.Transparent)
                    .clickable { onSetZoom(zoom) }
                    .testTag("zoom_preset_${zoom.toString().replace(".", "_")}"),
                contentAlignment = Alignment.Center
            ) {
                val label = if (zoom % 1.0f == 0f) {
                    if (isSelected) "${zoom.toInt()}×" else "${zoom.toInt()}"
                } else {
                    if (isSelected) "${zoom}×" else "$zoom"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isSelected) 11.sp else 10.sp
                    ),
                    color = if (isSelected) CineAmber else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Clean 3-column bottom shutter row with ZERO duplicate buttons:
 * - Left: Single Gallery Thumbnail button (rotates 90° in-place in Landscape)
 * - Center: Iconic White Ring + Red Shutter Button (rotates 90° in-place in Landscape)
 * - Right: Single Flip Camera button (rotates 90° in-place in Landscape)
 */
@Composable
private fun CleanShutterRow(
    uiState: CineCameraUiState,
    uiRotation: Float,
    hasCameraPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenGallery: () -> Unit,
    onToggleLens: () -> Unit
) {
    val latestVideo = uiState.recordedVideos.firstOrNull()
    val innerSize by animateDpAsState(
        targetValue = if (uiState.isRecording) 26.dp else 54.dp,
        animationSpec = tween(220),
        label = "shutter_inner_size"
    )
    val innerCorner by animateDpAsState(
        targetValue = if (uiState.isRecording) 8.dp else 27.dp,
        animationSpec = tween(220),
        label = "shutter_inner_corner"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Single Gallery Thumbnail Button with count badge (rotates 90° in-place)
        Box(
            modifier = Modifier
                .size(52.dp)
                .rotate(uiRotation)
                .clip(RoundedCornerShape(14.dp))
                .background(GlassOverlay)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenGallery)
                .testTag("open_gallery_button"),
            contentAlignment = Alignment.Center
        ) {
            if (latestVideo?.thumbnail != null) {
                Image(
                    bitmap = latestVideo.thumbnail.asImageBitmap(),
                    contentDescription = "Buka Galeri Video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = "Buka Galeri Video",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (uiState.recordedVideos.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(CineAmber)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${uiState.recordedVideos.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = ObsidianBlack,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Center: Iconic iOS / One UI Video Record Shutter Button (rotates 90° in-place)
        Box(
            modifier = Modifier
                .size(72.dp)
                .rotate(uiRotation)
                .clip(CircleShape)
                .border(
                    width = 3.5.dp,
                    color = Color.White,
                    shape = CircleShape
                )
                .clickable {
                    if (hasCameraPermission) {
                        onToggleRecording()
                    } else {
                        onRequestPermissions()
                    }
                }
                .testTag("record_video_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .clip(RoundedCornerShape(innerCorner))
                    .background(TallyRed)
            )
        }

        // Right: Single Circular Flip Camera Button (rotates 90° in-place)
        Box(
            modifier = Modifier
                .size(52.dp)
                .rotate(uiRotation)
                .clip(CircleShape)
                .background(GlassOverlay)
                .border(0.8.dp, BorderSubtle, CircleShape)
                .clickable(onClick = onToggleLens)
                .testTag("switch_lens_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Putar Kamera Depan/Belakang",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PermissionPromptOverlay(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .padding(28.dp)
            .border(0.8.dp, BorderSubtle, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CineAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = CineAmber,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "Izin Kamera & Mikrofon",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Izinkan akses kamera dan audio untuk mulai merekam video.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermissions,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CineAmber,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_camera_permission_button")
            ) {
                Text(
                    text = "Lanjutkan",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
