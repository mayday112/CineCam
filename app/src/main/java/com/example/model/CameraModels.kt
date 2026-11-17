package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * Capture & Frame Orientation Mode: Portrait (Vertical) vs Landscape (Horizontal Cinema)
 */
enum class CaptureOrientationMode(
    val id: String,
    val label: String,
    val shortBadge: String,
    val description: String
) {
    PORTRAIT(
        id = "portrait",
        label = "Mode Portrait",
        shortBadge = "PORTRAIT",
        description = "Rekaman Vertikal (9:16, 6:19, 3:4, 2:3)"
    ),
    LANDSCAPE(
        id = "landscape",
        label = "Mode Landscape",
        shortBadge = "LANDSCAPE",
        description = "Rekaman Horizontal Sinematik (16:9, 19:6, 4:3, 3:2)"
    )
}

/**
 * Supported aspect ratios requested by the user:
 * 19:6 (Anamorphic Ultra-Wide), 16:9 (Standard Widescreen), 4:3 (Classic Sensor), 3:2 (Full Frame 35mm)
 * Automatically adapts to 6:19, 9:16, 3:4, 2:3 when in Portrait mode!
 */
enum class AspectRatioOption(
    val label: String,
    val subtitle: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    RATIO_19_6("19:6", "Ultra-Wide Anamorphic", 19, 6),
    RATIO_16_9("16:9", "Standard Cinema HD", 16, 9),
    RATIO_4_3("4:3", "Open Gate / Classic", 4, 3),
    RATIO_3_2("3:2", "Full Frame 35mm", 3, 2);

    val floatRatio: Float
        get() = widthRatio.toFloat() / heightRatio.toFloat()

    fun displayLabel(orientation: CaptureOrientationMode): String =
        when (orientation) {
            CaptureOrientationMode.LANDSCAPE -> "$widthRatio:$heightRatio"
            CaptureOrientationMode.PORTRAIT -> "$heightRatio:$widthRatio"
        }

    fun effectiveWidthRatio(orientation: CaptureOrientationMode): Int =
        when (orientation) {
            CaptureOrientationMode.LANDSCAPE -> widthRatio
            CaptureOrientationMode.PORTRAIT -> heightRatio
        }

    fun effectiveHeightRatio(orientation: CaptureOrientationMode): Int =
        when (orientation) {
            CaptureOrientationMode.LANDSCAPE -> heightRatio
            CaptureOrientationMode.PORTRAIT -> widthRatio
        }

    fun effectiveFloatRatio(orientation: CaptureOrientationMode): Float =
        effectiveWidthRatio(orientation).toFloat() / effectiveHeightRatio(orientation).toFloat()
}

/**
 * Video resolution option discovered dynamically from the phone's CameraInfo / StreamConfigurationMap,
 * with fallback standard profiles so every device shows accurate supported resolutions.
 */
data class VideoResolutionOption(
    val id: String,
    val label: String,
    val badge: String,
    val width: Int,
    val height: Int,
    val bitrateMbps: Int,
    val qualityCode: String // "UHD", "FHD", "HD", "SD"
) {
    val dimensionsText: String
        get() = "${width}×${height}"

    fun dimensionsTextFor(orientation: CaptureOrientationMode): String =
        when (orientation) {
            CaptureOrientationMode.LANDSCAPE -> "${maxOf(width, height)}×${minOf(width, height)}"
            CaptureOrientationMode.PORTRAIT -> "${minOf(width, height)}×${maxOf(width, height)}"
        }
}

/**
 * Cinema shutter speed stop options starting from 1/24 sec (41,666,667 ns) up to 1/8000 sec.
 */
data class ShutterSpeedStop(
    val label: String,
    val denominator: Int,
    val exposureTimeNs: Long,
    val angleDescription: String
)

val CINEMA_SHUTTER_SPEEDS = listOf(
    ShutterSpeedStop("1/24", 24, 41_666_667L, "360° Shutter (Motion Blur)"),
    ShutterSpeedStop("1/30", 30, 33_333_333L, "30fps Native"),
    ShutterSpeedStop("1/48", 48, 20_833_333L, "180° Cinema Rule (24fps)"),
    ShutterSpeedStop("1/50", 50, 20_000_000L, "PAL Flicker-Free"),
    ShutterSpeedStop("1/60", 60, 16_666_667L, "NTSC / 30fps 180°"),
    ShutterSpeedStop("1/100", 100, 10_000_000L, "Outdoor Action"),
    ShutterSpeedStop("1/125", 125, 8_000_000L, "Fast Motion"),
    ShutterSpeedStop("1/250", 250, 4_000_000L, "Crisp Action"),
    ShutterSpeedStop("1/500", 500, 2_000_000L, "Bright Daylight"),
    ShutterSpeedStop("1/1000", 1000, 1_000_000L, "High Speed"),
    ShutterSpeedStop("1/2000", 2000, 500_000L, "Ultra High Speed"),
    ShutterSpeedStop("1/4000", 4000, 250_000L, "Extreme Freeze")
)

val CINEMA_ISO_STOPS = listOf(
    50, 100, 160, 200, 320, 400, 640, 800, 1250, 1600, 3200, 6400
)

/**
 * Frame rate (FPS) options starting from minimum 24 FPS (Cinema Standard) up to 120 FPS.
 */
data class FpsOption(
    val fps: Int,
    val label: String,
    val badge: String,
    val cinemaNote: String,
    val shutter180Denominator: Int
) {
    val frameDurationNs: Long
        get() = 1_000_000_000L / fps.coerceAtLeast(24)
}

val CINEMA_FPS_OPTIONS = listOf(
    FpsOption(24, "24 FPS", "24p CINE", "Standar Film Bioskop (Minimum Sinematik)", 48),
    FpsOption(25, "25 FPS", "25p PAL", "Standar Broadcast PAL / Eropa & Indonesia", 50),
    FpsOption(30, "30 FPS", "30p STD", "Standar Video Digital & Dokumenter", 60),
    FpsOption(48, "48 FPS", "48p HFR", "High Frame Rate Cinema (2× 24fps)", 100),
    FpsOption(50, "50 FPS", "50p PAL", "Gerak Halus PAL / Slow-Mo 50%", 100),
    FpsOption(60, "60 FPS", "60p HFR", "Gerak Sangat Halus / Slow-Mo 40% di 24p", 125),
    FpsOption(120, "120 FPS", "120p S&Q", "Super Slow Motion (5× lebih lambat di 24p)", 250)
)

/**
 * Composition guides requested by the user:
 * Rule of Thirds, Crosshair, Grid, Diagonal, Horizon Line (all individually or globally toggleable)
 */
enum class GuideType(
    val id: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    RULE_OF_THIRDS("thirds", "Rule of Thirds", "3×3", "Garis komposisi sepertiga klasik dengan titik fokus emas"),
    CROSSHAIR("crosshair", "Crosshair", "Center +", "Bidik tengah presisi untuk subjek sentral"),
    GRID("grid", "Grid 4×4", "Grid", "Kotak bantu arsitektural simetris"),
    DIAGONAL("diagonal", "Diagonal", "Diag ×", "Garis silang sudut ke sudut untuk dinamika frame"),
    HORIZON_LINE("horizon", "Horizon Line", "Horizon", "Waterpass elektronik real-time menggunakan sensor kemiringan HP")
}

/**
 * Active manual control drawer tab on the camera HUD
 */
enum class ControlPanelTab(val label: String) {
    NONE("Tutup"),
    ORIENTATION("Mode"),
    FPS("FPS"),
    SHUTTER("Shutter"),
    ISO("ISO"),
    FOCUS("Fokus"),
    RESOLUTION("Resolusi"),
    ASPECT_RATIO("Ukuran"),
    GUIDES("Garis Bantu")
}

/**
 * Metadata for a recorded video item in the integrated Gallery & Video Player
 */
data class RecordedVideoItem(
    val id: String,
    val file: File,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val aspectRatioLabel: String,
    val shutterLabel: String,
    val isoLabel: String,
    val fpsLabel: String = "24 FPS",
    val orientationLabel: String = "LANDSCAPE",
    val thumbnail: Bitmap? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
            val mins = totalSeconds / 60
            val secs = totalSeconds % 60
            return String.format("%02d:%02d", mins, secs)
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                val kb = (sizeBytes / 1024L).coerceAtLeast(1L)
                "$kb KB"
            }
        }

    val resolutionBadge: String
        get() = when {
            height >= 2160 || width >= 3840 -> "4K UHD"
            height >= 1080 || width >= 1920 -> "1080p FHD"
            height >= 720 || width >= 1280 -> "720p HD"
            else -> "${width}×${height}"
        }
}
