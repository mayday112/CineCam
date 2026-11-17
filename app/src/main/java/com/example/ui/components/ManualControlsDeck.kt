package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioOption
import com.example.model.CINEMA_ISO_STOPS
import com.example.model.CINEMA_SHUTTER_SPEEDS
import com.example.model.CaptureOrientationMode
import com.example.model.ControlPanelTab
import com.example.model.FpsOption
import com.example.model.GuideType
import com.example.model.ShutterSpeedStop
import com.example.model.VideoResolutionOption
import com.example.ui.CineCameraUiState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CineAmber
import com.example.ui.theme.GlassOverlay
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Translucent, floating One UI Pro Video / iOS Camera parameter strip.
 * When in Landscape mode (or rotated 90°), the parameter strip stays in its exact position
 * and each parameter button smoothly rotates 90° in-place (`Modifier.rotate(itemRotation)`).
 */
@Composable
fun ManualControlsDeck(
    uiState: CineCameraUiState,
    onSelectTab: (ControlPanelTab) -> Unit,
    onSelectOrientationMode: (CaptureOrientationMode) -> Unit,
    onToggleRotateScreenWithMode: (Boolean) -> Unit,
    onSelectFps: (FpsOption) -> Unit,
    onApply180ShutterRule: () -> Unit,
    onSetExposureMode: (Boolean) -> Unit,
    onSelectShutterSpeed: (ShutterSpeedStop) -> Unit,
    onSelectIso: (Int) -> Unit,
    onSetFocusMode: (Boolean) -> Unit,
    onManualFocusDistanceChange: (Float) -> Unit,
    onSelectResolution: (VideoResolutionOption) -> Unit,
    onSelectAspectRatio: (AspectRatioOption) -> Unit,
    onToggleMasterGuides: (Boolean) -> Unit,
    onToggleGuide: (GuideType) -> Unit,
    onEnableAllGuides: () -> Unit,
    onDisableAllGuides: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemRotation by animateFloatAsState(
        targetValue = if (uiState.orientationMode == CaptureOrientationMode.LANDSCAPE) 90f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "parameter_item_rotation"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Compact Floating Frosted Popover (only visible when a parameter pill is tapped)
        AnimatedVisibility(
            visible = uiState.activeControlTab != ControlPanelTab.NONE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = Color(0xCC161618),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .border(
                        width = 0.6.dp,
                        color = BorderSubtle,
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    when (uiState.activeControlTab) {
                        ControlPanelTab.ORIENTATION -> OrientationControlPanel(
                            selectedOrientation = uiState.orientationMode,
                            selectedAspectRatio = uiState.selectedAspectRatio,
                            itemRotation = itemRotation,
                            onSelectOrientation = onSelectOrientationMode,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.FPS -> FpsControlPanel(
                            fpsOptions = uiState.availableFpsOptions,
                            selectedFps = uiState.selectedFps,
                            selectedShutter = uiState.selectedShutterSpeed,
                            itemRotation = itemRotation,
                            onSelectFps = onSelectFps,
                            onApply180ShutterRule = onApply180ShutterRule,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.SHUTTER -> ShutterSpeedPanel(
                            isManualExposure = uiState.isManualExposure,
                            selectedShutter = uiState.selectedShutterSpeed,
                            itemRotation = itemRotation,
                            onSetManualMode = onSetExposureMode,
                            onSelectShutter = onSelectShutterSpeed,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.ISO -> IsoControlPanel(
                            isManualExposure = uiState.isManualExposure,
                            selectedIso = uiState.selectedIso,
                            isoRange = uiState.hardwareIsoRange,
                            itemRotation = itemRotation,
                            onSetManualMode = onSetExposureMode,
                            onSelectIso = onSelectIso,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.FOCUS -> FocusControlPanel(
                            isManualFocus = uiState.isManualFocus,
                            focusNormalized = uiState.manualFocusDistanceNormalized,
                            focusDisplay = uiState.focusDistanceDisplay,
                            itemRotation = itemRotation,
                            onSetManualFocus = onSetFocusMode,
                            onFocusDistanceChange = onManualFocusDistanceChange,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.ASPECT_RATIO -> AspectRatioControlPanel(
                            selectedRatio = uiState.selectedAspectRatio,
                            orientationMode = uiState.orientationMode,
                            itemRotation = itemRotation,
                            onSelectRatio = onSelectAspectRatio,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.RESOLUTION -> ResolutionControlPanel(
                            resolutions = uiState.supportedResolutions,
                            selectedResolution = uiState.selectedResolution,
                            orientationMode = uiState.orientationMode,
                            itemRotation = itemRotation,
                            onSelectResolution = onSelectResolution,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.GUIDES -> GuidesControlPanel(
                            areGuidesEnabled = uiState.areGuidesEnabled,
                            activeGuides = uiState.activeGuides,
                            itemRotation = itemRotation,
                            onToggleMaster = onToggleMasterGuides,
                            onToggleGuide = onToggleGuide,
                            onEnableAll = onEnableAllGuides,
                            onDisableAll = onDisableAllGuides,
                            onClose = { onSelectTab(ControlPanelTab.NONE) }
                        )

                        ControlPanelTab.NONE -> {}
                    }
                }
            }
        }

        // 2. Single Compact Floating Frosted Capsule for Pro Parameters (rotates each item 90° in-place!)
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(CircleShape)
                .background(GlassOverlay)
                .border(0.5.dp, BorderSubtle, CircleShape)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CleanParameterItem(
                label = "ISO",
                value = if (uiState.isManualExposure) "${uiState.selectedIso}" else "AUTO",
                isActive = uiState.activeControlTab == ControlPanelTab.ISO,
                isModified = uiState.isManualExposure,
                rotationDegrees = itemRotation,
                testTag = "tab_iso_button",
                onClick = { onSelectTab(ControlPanelTab.ISO) }
            )

            CleanParameterItem(
                label = "SPEED",
                value = if (uiState.isManualExposure) uiState.selectedShutterSpeed.label else "AUTO",
                isActive = uiState.activeControlTab == ControlPanelTab.SHUTTER,
                isModified = uiState.isManualExposure,
                rotationDegrees = itemRotation,
                testTag = "tab_shutter_button",
                onClick = { onSelectTab(ControlPanelTab.SHUTTER) }
            )

            CleanParameterItem(
                label = "FOCUS",
                value = if (uiState.isManualFocus) uiState.focusDistanceDisplay.removePrefix("MF ") else "AF",
                isActive = uiState.activeControlTab == ControlPanelTab.FOCUS,
                isModified = uiState.isManualFocus,
                rotationDegrees = itemRotation,
                testTag = "tab_focus_button",
                onClick = { onSelectTab(ControlPanelTab.FOCUS) }
            )

            CleanParameterItem(
                label = "FPS",
                value = "${uiState.selectedFps.fps}",
                isActive = uiState.activeControlTab == ControlPanelTab.FPS,
                isModified = false,
                rotationDegrees = itemRotation,
                testTag = "tab_fps_button",
                onClick = { onSelectTab(ControlPanelTab.FPS) }
            )

            CleanParameterItem(
                label = "RATIO",
                value = uiState.activeRatioDisplay,
                isActive = uiState.activeControlTab == ControlPanelTab.ASPECT_RATIO,
                isModified = false,
                rotationDegrees = itemRotation,
                testTag = "tab_aspect_ratio_button",
                onClick = { onSelectTab(ControlPanelTab.ASPECT_RATIO) }
            )

            CleanParameterItem(
                label = "RES",
                value = uiState.selectedResolution.badge,
                isActive = uiState.activeControlTab == ControlPanelTab.RESOLUTION,
                isModified = false,
                rotationDegrees = itemRotation,
                testTag = "tab_resolution_button",
                onClick = { onSelectTab(ControlPanelTab.RESOLUTION) }
            )

            val guidesCount = if (uiState.areGuidesEnabled) uiState.activeGuides.size else 0
            CleanParameterItem(
                label = "GRID",
                value = if (guidesCount > 0) "${guidesCount}" else "OFF",
                isActive = uiState.activeControlTab == ControlPanelTab.GUIDES,
                isModified = guidesCount > 0,
                rotationDegrees = itemRotation,
                testTag = "tab_guides_button",
                onClick = { onSelectTab(ControlPanelTab.GUIDES) }
            )
        }
    }
}

@Composable
private fun CleanParameterItem(
    label: String,
    value: String,
    isActive: Boolean,
    isModified: Boolean,
    rotationDegrees: Float,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor = if (isActive) Color.White.copy(alpha = 0.16f) else Color.Transparent
    val labelColor = if (isActive) CineAmber else TextSecondary
    val valueColor = if (isActive || isModified) CineAmber else TextPrimary

    // Fixed circular touch target so rotating 90° in-place never shifts neighboring items
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.rotate(rotationDegrees),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    letterSpacing = 0.4.sp
                ),
                color = labelColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp
                ),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CleanPanelHeader(
    title: String,
    subtitle: String,
    isManual: Boolean?,
    itemRotation: Float = 0f,
    onToggleManual: ((Boolean) -> Unit)? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        if (isManual != null && onToggleManual != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (!isManual) CineAmber else SlateElevated)
                    .clickable { onToggleManual(!isManual) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("exposure_manual_switch")
            ) {
                Text(
                    text = if (isManual) "MANUAL" else "AUTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (!isManual) ObsidianBlack else CineAmber
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SlateElevated)
                .rotate(itemRotation)
                .testTag("close_control_panel_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Tutup",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun FpsControlPanel(
    fpsOptions: List<FpsOption>,
    selectedFps: FpsOption,
    selectedShutter: ShutterSpeedStop,
    itemRotation: Float,
    onSelectFps: (FpsOption) -> Unit,
    onApply180ShutterRule: () -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CleanPanelHeader(
            title = "Frame Rate",
            subtitle = "${selectedFps.label} • Rekomendasi shutter 1/${selectedFps.shutter180Denominator}s",
            isManual = null,
            itemRotation = itemRotation,
            onClose = onClose
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(fpsOptions, key = { it.fps }) { option ->
                    val isSelected = option.fps == selectedFps.fps
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) CineAmber else SlateElevated)
                            .clickable { onSelectFps(option) }
                            .testTag("fps_option_${option.fps}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${option.fps}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) ObsidianBlack else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.rotate(itemRotation)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CineAmber.copy(alpha = 0.2f))
                    .border(0.8.dp, CineAmber, CircleShape)
                    .clickable(onClick = onApply180ShutterRule)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("apply_180_shutter_button")
            ) {
                Text(
                    text = "180° (1/${selectedFps.shutter180Denominator})",
                    style = MaterialTheme.typography.labelSmall,
                    color = CineAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ShutterSpeedPanel(
    isManualExposure: Boolean,
    selectedShutter: ShutterSpeedStop,
    itemRotation: Float,
    onSetManualMode: (Boolean) -> Unit,
    onSelectShutter: (ShutterSpeedStop) -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CleanPanelHeader(
            title = "Shutter Speed",
            subtitle = if (isManualExposure) "${selectedShutter.label}s (${selectedShutter.angleDescription})" else "Otomatis",
            isManual = isManualExposure,
            itemRotation = itemRotation,
            onToggleManual = onSetManualMode,
            onClose = onClose
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(CINEMA_SHUTTER_SPEEDS, key = { it.label }) { stop ->
                val isSelected = isManualExposure && stop.label == selectedShutter.label
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CineAmber else SlateElevated)
                        .clickable { onSelectShutter(stop) }
                        .testTag("shutter_stop_${stop.denominator}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stop.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) ObsidianBlack else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.rotate(itemRotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun IsoControlPanel(
    isManualExposure: Boolean,
    selectedIso: Int,
    isoRange: android.util.Range<Int>,
    itemRotation: Float,
    onSetManualMode: (Boolean) -> Unit,
    onSelectIso: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CleanPanelHeader(
            title = "ISO",
            subtitle = if (isManualExposure) "ISO $selectedIso" else "Otomatis",
            isManual = isManualExposure,
            itemRotation = itemRotation,
            onToggleManual = onSetManualMode,
            onClose = onClose
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CINEMA_ISO_STOPS, key = { it }) { isoStop ->
                val isSelected = isManualExposure && selectedIso == isoStop
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CineAmber else SlateElevated)
                        .clickable { onSelectIso(isoStop) }
                        .testTag("iso_stop_$isoStop"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$isoStop",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) ObsidianBlack else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.rotate(itemRotation)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("${isoRange.lower}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Slider(
                value = selectedIso.toFloat().coerceIn(
                    isoRange.lower.toFloat(),
                    isoRange.upper.toFloat().coerceAtLeast(isoRange.lower.toFloat() + 100f)
                ),
                onValueChange = { onSelectIso(it.roundToInt()) },
                valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat().coerceAtLeast(isoRange.lower.toFloat() + 100f),
                colors = SliderDefaults.colors(
                    thumbColor = CineAmber,
                    activeTrackColor = CineAmber,
                    inactiveTrackColor = SlateElevated
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("iso_fine_slider")
            )
            Text("${isoRange.upper}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun FocusControlPanel(
    isManualFocus: Boolean,
    focusNormalized: Float,
    focusDisplay: String,
    itemRotation: Float,
    onSetManualFocus: (Boolean) -> Unit,
    onFocusDistanceChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CleanPanelHeader(
            title = "Focus",
            subtitle = if (isManualFocus) "Manual ($focusDisplay)" else "Auto Focus (Ketuk layar untuk fokus)",
            isManual = isManualFocus,
            itemRotation = itemRotation,
            onToggleManual = onSetManualFocus,
            onClose = onClose
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (!isManualFocus) CineAmber else SlateElevated)
                    .clickable { onSetManualFocus(false) }
                    .testTag("focus_mode_auto_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!isManualFocus) ObsidianBlack else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.rotate(itemRotation)
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isManualFocus) CineAmber else SlateElevated)
                    .clickable { onSetManualFocus(true) }
                    .testTag("focus_mode_manual_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isManualFocus) ObsidianBlack else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.rotate(itemRotation)
                )
            }

            Text("∞", style = MaterialTheme.typography.labelMedium, color = CineAmber)
            Slider(
                value = focusNormalized,
                onValueChange = onFocusDistanceChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = CineAmber,
                    activeTrackColor = CineAmber,
                    inactiveTrackColor = SlateElevated
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("manual_focus_slider")
            )
            Text("Macro", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun OrientationControlPanel(
    selectedOrientation: CaptureOrientationMode,
    selectedAspectRatio: AspectRatioOption,
    itemRotation: Float,
    onSelectOrientation: (CaptureOrientationMode) -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CleanPanelHeader(
            title = "Mode Orientasi",
            subtitle = "${selectedOrientation.label} (${selectedAspectRatio.displayLabel(selectedOrientation)})",
            isManual = null,
            itemRotation = itemRotation,
            onClose = onClose
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptureOrientationMode.entries.forEach { mode ->
                val isSelected = mode == selectedOrientation
                val icon = if (mode == CaptureOrientationMode.PORTRAIT) {
                    Icons.Filled.ScreenLockPortrait
                } else {
                    Icons.Filled.ScreenLockLandscape
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) CineAmber else SlateElevated)
                        .clickable { onSelectOrientation(mode) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("orientation_mode_${mode.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.label,
                            tint = if (isSelected) ObsidianBlack else TextPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(itemRotation)
                        )
                        Text(
                            text = mode.shortBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) ObsidianBlack else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioControlPanel(
    selectedRatio: AspectRatioOption,
    orientationMode: CaptureOrientationMode,
    itemRotation: Float,
    onSelectRatio: (AspectRatioOption) -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CleanPanelHeader(
            title = "Ukuran Rasio Frame",
            subtitle = "Mode ${orientationMode.shortBadge} aktif",
            isManual = null,
            itemRotation = itemRotation,
            onClose = onClose
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatioOption.entries.forEach { ratio ->
                val isSelected = ratio == selectedRatio
                val displayRatio = ratio.displayLabel(orientationMode)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CineAmber else SlateElevated)
                        .clickable { onSelectRatio(ratio) }
                        .testTag("aspect_ratio_${ratio.label.replace(":", "_")}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayRatio,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) ObsidianBlack else TextPrimary,
                        modifier = Modifier.rotate(itemRotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolutionControlPanel(
    resolutions: List<VideoResolutionOption>,
    selectedResolution: VideoResolutionOption,
    orientationMode: CaptureOrientationMode,
    itemRotation: Float,
    onSelectResolution: (VideoResolutionOption) -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CleanPanelHeader(
            title = "Resolusi Video",
            subtitle = "${selectedResolution.label} • ${selectedResolution.dimensionsTextFor(orientationMode)}",
            isManual = null,
            itemRotation = itemRotation,
            onClose = onClose
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(resolutions, key = { it.id }) { res ->
                val isSelected = res.id == selectedResolution.id
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CineAmber else SlateElevated)
                        .clickable { onSelectResolution(res) }
                        .testTag("resolution_option_${res.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = res.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) ObsidianBlack else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.rotate(itemRotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidesControlPanel(
    areGuidesEnabled: Boolean,
    activeGuides: Set<GuideType>,
    itemRotation: Float,
    onToggleMaster: (Boolean) -> Unit,
    onToggleGuide: (GuideType) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CleanPanelHeader(
            title = "Garis Bantu Komposisi",
            subtitle = if (areGuidesEnabled && activeGuides.isNotEmpty()) "${activeGuides.size} aktif" else "Nonaktif",
            isManual = areGuidesEnabled,
            itemRotation = itemRotation,
            onToggleManual = onToggleMaster,
            onClose = onClose
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(GuideType.entries, key = { it.name }) { guide ->
                val isSelected = areGuidesEnabled && activeGuides.contains(guide)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) CineAmber else SlateElevated)
                        .clickable { onToggleGuide(guide) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("guide_chip_${guide.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = ObsidianBlack,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = guide.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ObsidianBlack else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(SlateElevated)
                    .clickable(onClick = onEnableAll)
                    .padding(vertical = 6.dp)
                    .testTag("enable_all_guides_button"),
                contentAlignment = Alignment.Center
            ) {
                Text("Aktifkan Semua", style = MaterialTheme.typography.labelSmall, color = CineAmber)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(SlateElevated)
                    .clickable(onClick = onDisableAll)
                    .padding(vertical = 6.dp)
                    .testTag("disable_all_guides_button"),
                contentAlignment = Alignment.Center
            ) {
                Text("Matikan Semua", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}
