package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import com.example.model.AspectRatioOption
import com.example.model.CaptureOrientationMode
import com.example.model.GuideType
import com.example.sensor.HorizonTiltState
import com.example.ui.TapFocusPoint
import com.example.ui.theme.CineAmber
import com.example.ui.theme.TallyRed
import kotlin.math.abs

/**
 * Full-screen iOS / One UI style camera viewfinder overlay.
 * Because the camera window stays anchored in portrait (so buttons never reflow to block the preview),
 * the long axis of the chosen aspect ratio (16:9, 19:6, 4:3, 3:2) always aligns with the long axis
 * of the physical screen, keeping the preview 100% full-screen in both Portrait and Landscape modes!
 */
@Composable
fun CinemaGuideOverlays(
    aspectRatio: AspectRatioOption,
    orientationMode: CaptureOrientationMode,
    areGuidesEnabled: Boolean,
    activeGuides: Set<GuideType>,
    horizonTilt: HorizonTiltState,
    tapFocusPoint: TapFocusPoint?,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("cinema_guides_canvas")
    ) {
        val canvasW = size.width
        val canvasH = size.height
        if (canvasW <= 0f || canvasH <= 0f) return@Canvas

        // Align the short/long aspect ratio with the physical screen's short/long axes
        // so the preview stays full-screen whether held vertically (Portrait) or rotated 90° (Landscape).
        val shortRatio = minOf(aspectRatio.widthRatio, aspectRatio.heightRatio).toFloat()
        val longRatio = maxOf(aspectRatio.widthRatio, aspectRatio.heightRatio).toFloat()
        val screenTargetRatio = if (canvasH >= canvasW) {
            shortRatio / longRatio
        } else {
            longRatio / shortRatio
        }

        val frameWidth: Float
        val frameHeight: Float
        if (canvasW / canvasH > screenTargetRatio) {
            frameHeight = canvasH
            frameWidth = (frameHeight * screenTargetRatio).coerceAtMost(canvasW)
        } else {
            frameWidth = canvasW
            frameHeight = (frameWidth / screenTargetRatio).coerceAtMost(canvasH)
        }

        val left = (canvasW - frameWidth) / 2f
        val top = (canvasH - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight
        val frameRect = Rect(left, top, right, bottom)

        // 1. iOS "View Outside the Frame" style subtle translucent dim outside the active aspect ratio
        val framePath = Path().apply {
            addRect(frameRect)
        }
        if (frameWidth < canvasW - 2f || frameHeight < canvasH - 2f) {
            clipPath(framePath, clipOp = ClipOp.Difference) {
                drawRect(
                    color = Color(0x52000000),
                    size = size
                )
            }
            val borderColor = if (isRecording) {
                TallyRed.copy(alpha = 0.7f)
            } else {
                Color.White.copy(alpha = 0.28f)
            }
            drawRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                style = Stroke(width = if (isRecording) 2f else 1f)
            )
        }

        // 2. Draw composition guides inside the active frame gate if enabled
        if (areGuidesEnabled && activeGuides.isNotEmpty()) {
            clipPath(framePath) {
                if (activeGuides.contains(GuideType.GRID)) {
                    drawCleanGrid4x4(frameRect)
                }
                if (activeGuides.contains(GuideType.DIAGONAL)) {
                    drawCleanDiagonals(frameRect)
                }
                if (activeGuides.contains(GuideType.RULE_OF_THIRDS)) {
                    drawCleanRuleOfThirds(frameRect)
                }
                if (activeGuides.contains(GuideType.CROSSHAIR)) {
                    drawCleanCrosshair(frameRect)
                }
                if (activeGuides.contains(GuideType.HORIZON_LINE)) {
                    drawCleanHorizonLevel(frameRect, horizonTilt, orientationMode)
                }
            }
        }

        // 3. iOS / One UI style Tap-to-Focus yellow square with side ticks
        tapFocusPoint?.let { point ->
            val fx = point.x.coerceIn(44f, canvasW - 44f)
            val fy = point.y.coerceIn(44f, canvasH - 44f)
            val focusColor = CineAmber
            val halfBox = 36f
            val tickLen = 6f

            drawRect(
                color = focusColor,
                topLeft = Offset(fx - halfBox, fy - halfBox),
                size = Size(halfBox * 2f, halfBox * 2f),
                style = Stroke(width = 2f)
            )
            drawLine(focusColor, Offset(fx, fy - halfBox), Offset(fx, fy - halfBox + tickLen), strokeWidth = 2f)
            drawLine(focusColor, Offset(fx, fy + halfBox), Offset(fx, fy + halfBox - tickLen), strokeWidth = 2f)
            drawLine(focusColor, Offset(fx - halfBox, fy), Offset(fx - halfBox + tickLen, fy), strokeWidth = 2f)
            drawLine(focusColor, Offset(fx + halfBox, fy), Offset(fx + halfBox - tickLen, fy), strokeWidth = 2f)
        }
    }
}

private fun DrawScope.drawCleanRuleOfThirds(rect: Rect) {
    val shadowColor = Color.Black.copy(alpha = 0.18f)
    val guideColor = Color.White.copy(alpha = 0.38f)
    val strokeW = 1.0f

    val x1 = rect.left + rect.width / 3f
    val x2 = rect.left + rect.width * 2f / 3f
    val y1 = rect.top + rect.height / 3f
    val y2 = rect.top + rect.height * 2f / 3f

    drawLine(shadowColor, Offset(x1, rect.top), Offset(x1, rect.bottom), strokeWidth = strokeW + 1.2f)
    drawLine(shadowColor, Offset(x2, rect.top), Offset(x2, rect.bottom), strokeWidth = strokeW + 1.2f)
    drawLine(shadowColor, Offset(rect.left, y1), Offset(rect.right, y1), strokeWidth = strokeW + 1.2f)
    drawLine(shadowColor, Offset(rect.left, y2), Offset(rect.right, y2), strokeWidth = strokeW + 1.2f)

    drawLine(guideColor, Offset(x1, rect.top), Offset(x1, rect.bottom), strokeWidth = strokeW)
    drawLine(guideColor, Offset(x2, rect.top), Offset(x2, rect.bottom), strokeWidth = strokeW)
    drawLine(guideColor, Offset(rect.left, y1), Offset(rect.right, y1), strokeWidth = strokeW)
    drawLine(guideColor, Offset(rect.left, y2), Offset(rect.right, y2), strokeWidth = strokeW)
}

private fun DrawScope.drawCleanGrid4x4(rect: Rect) {
    val gridColor = Color.White.copy(alpha = 0.24f)
    for (i in 1..3) {
        val x = rect.left + (rect.width * i / 4f)
        val y = rect.top + (rect.height * i / 4f)
        drawLine(
            color = gridColor,
            start = Offset(x, rect.top),
            end = Offset(x, rect.bottom),
            strokeWidth = 0.9f
        )
        drawLine(
            color = gridColor,
            start = Offset(rect.left, y),
            end = Offset(rect.right, y),
            strokeWidth = 0.9f
        )
    }
}

private fun DrawScope.drawCleanDiagonals(rect: Rect) {
    val diagColor = Color.White.copy(alpha = 0.24f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
    drawLine(
        color = diagColor,
        start = Offset(rect.left, rect.top),
        end = Offset(rect.right, rect.bottom),
        strokeWidth = 1.0f,
        pathEffect = dash
    )
    drawLine(
        color = diagColor,
        start = Offset(rect.right, rect.top),
        end = Offset(rect.left, rect.bottom),
        strokeWidth = 1.0f,
        pathEffect = dash
    )
}

private fun DrawScope.drawCleanCrosshair(rect: Rect) {
    val cx = rect.center.x
    val cy = rect.center.y
    val arm = (rect.width.coerceAtMost(rect.height) * 0.045f).coerceIn(16f, 28f)
    val color = Color.White.copy(alpha = 0.65f)

    drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 1.4f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCleanHorizonLevel(
    rect: Rect,
    tilt: HorizonTiltState,
    orientationMode: CaptureOrientationMode
) {
    val cx = rect.center.x
    val cy = rect.center.y
    val baseRotation = if (orientationMode == CaptureOrientationMode.LANDSCAPE) 90f else 0f
    val isLevel = abs(tilt.rollDegrees) <= 1.2f
    val horizonColor = if (isLevel) CineAmber else Color.White.copy(alpha = 0.55f)

    val lineHalfSpan = rect.width.coerceAtMost(rect.height) * 0.18f
    val centerGap = rect.width.coerceAtMost(rect.height) * 0.035f

    rotate(degrees = baseRotation, pivot = Offset(cx, cy)) {
        drawLine(
            color = if (isLevel) CineAmber else Color.White.copy(alpha = 0.35f),
            start = Offset(cx - lineHalfSpan - 14f, cy),
            end = Offset(cx - lineHalfSpan - 4f, cy),
            strokeWidth = 2.0f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (isLevel) CineAmber else Color.White.copy(alpha = 0.35f),
            start = Offset(cx + lineHalfSpan + 4f, cy),
            end = Offset(cx + lineHalfSpan + 14f, cy),
            strokeWidth = 2.0f,
            cap = StrokeCap.Round
        )

        rotate(degrees = -tilt.rollDegrees, pivot = Offset(cx, cy)) {
            drawLine(
                color = horizonColor,
                start = Offset(cx - lineHalfSpan, cy),
                end = Offset(cx - centerGap, cy),
                strokeWidth = if (isLevel) 2.4f else 1.6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = horizonColor,
                start = Offset(cx + centerGap, cy),
                end = Offset(cx + lineHalfSpan, cy),
                strokeWidth = if (isLevel) 2.4f else 1.6f,
                cap = StrokeCap.Round
            )
        }
    }
}
