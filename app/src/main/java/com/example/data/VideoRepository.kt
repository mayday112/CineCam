package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.model.AspectRatioOption
import com.example.model.CaptureOrientationMode
import com.example.model.RecordedVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRepository(private val context: Context) {

    val videoDirectory: File by lazy {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        File(baseDir, "CineProVideos").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Creates a new destination MP4 file encoding the active aspect ratio, orientation, shutter speed, ISO, and FPS
     * so the gallery can display exact camera exposure metadata alongside extracted MediaMetadataRetriever metrics.
     */
    fun createOutputVideoFile(
        aspectRatio: AspectRatioOption,
        shutterLabel: String,
        isoLabel: String,
        fps: Int = 24,
        orientationMode: CaptureOrientationMode = CaptureOrientationMode.LANDSCAPE
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeRatio = aspectRatio.displayLabel(orientationMode).replace(":", "x")
        val safeShutter = shutterLabel.replace("/", "-")
        val safeIso = isoLabel.replace(" ", "")
        val safeFps = "${fps.coerceAtLeast(24)}fps"
        val safeOrient = orientationMode.shortBadge
        val fileName = "CINE_${timestamp}__${safeRatio}__${safeShutter}__${safeIso}__${safeFps}__${safeOrient}.mp4"
        return File(videoDirectory, fileName)
    }

    suspend fun loadRecordedVideos(): List<RecordedVideoItem> = withContext(Dispatchers.IO) {
        val files = videoDirectory.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.US) == "mp4" && file.length() > 0L
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        files.mapNotNull { file ->
            parseVideoFile(file)
        }
    }

    private fun parseVideoFile(file: File): RecordedVideoItem? {
        var durationMs = 0L
        var width = 1920
        var height = 1080
        var thumbnail: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            thumbnail = retriever.getFrameAtTime(
                200_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime
        } catch (_: Exception) {
            // Keep default fallback metadata if file is still flushing
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // Parse encoded exposure info from filename: CINE_yyyyMMdd_HHmmss__19x6__1-24__ISO400__24fps__LANDSCAPE.mp4
        val parts = file.nameWithoutExtension.split("__")
        val ratioLabel = parts.getOrNull(1)?.replace("x", ":") ?: "16:9"
        val shutterLabel = parts.getOrNull(2)?.replace("-", "/") ?: "1/24"
        val isoLabel = parts.getOrNull(3) ?: "ISO AUTO"
        val fpsLabel = parts.getOrNull(4)?.uppercase(Locale.US)?.replace("FPS", " FPS") ?: "24 FPS"
        val orientationLabel = parts.getOrNull(5)?.uppercase(Locale.US)
            ?: if (height > width) "PORTRAIT" else "LANDSCAPE"

        val displayTitle = parts.firstOrNull()?.replace("CINE_", "TAKE ") ?: file.nameWithoutExtension

        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        return RecordedVideoItem(
            id = file.name,
            file = file,
            uri = uri,
            title = displayTitle,
            durationMs = durationMs.coerceAtLeast(1000L),
            width = width,
            height = height,
            sizeBytes = file.length(),
            createdAtMillis = file.lastModified(),
            aspectRatioLabel = ratioLabel,
            shutterLabel = shutterLabel,
            isoLabel = isoLabel,
            fpsLabel = fpsLabel,
            orientationLabel = orientationLabel,
            thumbnail = thumbnail
        )
    }

    suspend fun deleteVideo(item: RecordedVideoItem): Boolean = withContext(Dispatchers.IO) {
        try {
            item.file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun shareVideo(item: RecordedVideoItem) {
        try {
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                item.file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, "Bagikan Video CinePro").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Generates a playable H.264 MP4 cinema clip using MediaCodec + PureMp4Muxer
     * at the selected FPS (minimum 24fps) and Orientation (Portrait or Landscape).
     * Never touches native MPEG4Writer / MediaMuxer.
     */
    suspend fun generateSampleCinemaClip(
        aspectRatio: AspectRatioOption,
        shutterLabel: String,
        isoLabel: String,
        fps: Int = 24,
        orientationMode: CaptureOrientationMode = CaptureOrientationMode.LANDSCAPE,
        width: Int = 640,
        height: Int = 360,
        outputFile: File? = null,
        capturedBitmaps: List<Bitmap> = emptyList()
    ): File? = withContext(Dispatchers.IO) {
        val safeFps = fps.coerceIn(24, 60)
        val outFile = outputFile ?: createOutputVideoFile(aspectRatio, shutterLabel, isoLabel, safeFps, orientationMode)
        val safeWidth = if (orientationMode == CaptureOrientationMode.PORTRAIT) 368 else 640
        val safeHeight = if (orientationMode == CaptureOrientationMode.PORTRAIT) 640 else 368
        val totalFrames = if (capturedBitmaps.size >= 12) {
            capturedBitmaps.size.coerceAtMost(safeFps * 10)
        } else {
            safeFps * 2
        }
        val frameStepUs = 1_000_000L / safeFps

        var encoder: MediaCodec? = null
        val collectState = AvcCollectState()

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, safeWidth, safeHeight).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val bufferInfo = MediaCodec.BufferInfo()

            for (frameIdx in 0 until totalFrames) {
                val inIndex = encoder.dequeueInputBuffer(20_000L)
                if (inIndex >= 0) {
                    val ptsUs = frameIdx * frameStepUs
                    val sourceBitmap = if (capturedBitmaps.isNotEmpty()) {
                        capturedBitmaps[frameIdx % capturedBitmaps.size]
                    } else {
                        null
                    }

                    val inputImage = try {
                        encoder.getInputImage(inIndex)
                    } catch (_: Exception) {
                        null
                    }

                    if (inputImage != null) {
                        if (sourceBitmap != null) {
                            fillFrameFromBitmapYuv(inputImage, sourceBitmap, safeWidth, safeHeight)
                        } else {
                            fillCinemaTestFrameYuv(inputImage, frameIdx, totalFrames, safeWidth, safeHeight)
                        }
                        inputImage.close()
                        encoder.queueInputBuffer(
                            inIndex,
                            0,
                            safeWidth * safeHeight * 3 / 2,
                            ptsUs,
                            0
                        )
                    } else {
                        val inBuffer = encoder.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val frameBytes = safeWidth * safeHeight * 3 / 2
                            val limit = inBuffer.capacity().coerceAtMost(frameBytes)
                            val lumaVal = (28 + ((frameIdx * 3) % 90)).toByte()
                            for (i in 0 until limit) {
                                inBuffer.put(lumaVal)
                            }
                            encoder.queueInputBuffer(inIndex, 0, limit, ptsUs, 0)
                        } else {
                            encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, 0)
                        }
                    }
                }
                drainAvcEncoder(encoder, bufferInfo, collectState, endOfStream = false)
            }

            val eosIndex = encoder.dequeueInputBuffer(20_000L)
            if (eosIndex >= 0) {
                encoder.queueInputBuffer(
                    eosIndex,
                    0,
                    0,
                    totalFrames * frameStepUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drainAvcEncoder(encoder, bufferInfo, collectState, endOfStream = true)

            val sps = collectState.sps
            val pps = collectState.pps
            if (sps != null && pps != null && collectState.samples.isNotEmpty()) {
                val written = PureMp4Muxer.writeMp4File(
                    outputFile = outFile,
                    width = safeWidth,
                    height = safeHeight,
                    fps = safeFps,
                    sps = sps,
                    pps = pps,
                    samples = collectState.samples
                )
                if (written) outFile else null
            } else {
                outFile.delete()
                null
            }
        } catch (_: Exception) {
            outFile.delete()
            null
        } finally {
            try {
                encoder?.stop()
            } catch (_: Exception) {
            }
            try {
                encoder?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun fillFrameFromBitmapYuv(
        image: android.media.Image,
        bitmap: Bitmap,
        width: Int,
        height: Int
    ) {
        val planes = image.planes
        if (planes.isEmpty()) return
        val scaled = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val yPlane = planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        for (y in 0 until height) {
            val rowOffset = y * yRowStride
            val pixRow = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x * yPixelStride
                if (idx < yBuf.limit()) {
                    val c = pixels[pixRow + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val luma = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235)
                    yBuf.put(idx, luma.toByte())
                }
            }
        }
    }

    private fun fillCinemaTestFrameYuv(
        image: android.media.Image,
        frameIdx: Int,
        totalFrames: Int,
        width: Int,
        height: Int
    ) {
        val planes = image.planes
        if (planes.isEmpty()) return
        val yPlane = planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val barX = ((frameIdx.toFloat() / totalFrames.coerceAtLeast(1)) * width).toInt()
        val thirdW1 = width / 3
        val thirdW2 = (width * 2) / 3
        val thirdH1 = height / 3
        val thirdH2 = (height * 2) / 3

        for (y in 0 until height) {
            val rowOffset = y * yRowStride
            val isHorizGuide = (y == thirdH1 || y == thirdH2 || y == height / 2)
            for (x in 0 until width) {
                val idx = rowOffset + x * yPixelStride
                if (idx < yBuf.limit()) {
                    val isVertGuide = (x == thirdW1 || x == thirdW2)
                    val isSweepBar = kotlin.math.abs(x - barX) < 10
                    val luma = when {
                        isSweepBar -> 225
                        isHorizGuide || isVertGuide -> 165
                        else -> (22 + (y * 40 / height) + (x * 25 / width)).coerceIn(16, 235)
                    }
                    yBuf.put(idx, luma.toByte())
                }
            }
        }
    }

    private class AvcCollectState(
        var sps: ByteArray? = null,
        var pps: ByteArray? = null,
        val samples: MutableList<PureMp4Muxer.EncodedSample> = mutableListOf()
    )

    private fun drainAvcEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        state: AvcCollectState,
        endOfStream: Boolean
    ) {
        var emptyRetries = 0
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, 15_000L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                } else {
                    emptyRetries++
                    if (emptyRetries > 20) break
                }
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outFmt = encoder.outputFormat
                outFmt.getByteBuffer("csd-0")?.let { buf ->
                    val dup = buf.duplicate()
                    val bytes = ByteArray(dup.remaining())
                    dup.get(bytes)
                    val (extractedSps, extractedPps) = PureMp4Muxer.extractSpsPpsFromAnnexB(bytes)
                    if (extractedSps != null) state.sps = extractedSps
                    if (extractedPps != null) state.pps = extractedPps
                }
                outFmt.getByteBuffer("csd-1")?.let { buf ->
                    val dup = buf.duplicate()
                    val bytes = ByteArray(dup.remaining())
                    dup.get(bytes)
                    val (_, extractedPps) = PureMp4Muxer.extractSpsPpsFromAnnexB(bytes)
                    if (extractedPps != null) state.pps = extractedPps
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null && bufferInfo.size > 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    val rawBytes = ByteArray(bufferInfo.size)
                    encodedData.get(rawBytes)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        val (s, p) = PureMp4Muxer.extractSpsPpsFromAnnexB(rawBytes)
                        if (s != null) state.sps = s
                        if (p != null) state.pps = p
                    } else {
                        if (state.sps == null || state.pps == null) {
                            val (s, p) = PureMp4Muxer.extractSpsPpsFromAnnexB(rawBytes)
                            if (s != null) state.sps = s
                            if (p != null) state.pps = p
                        }
                        val avccPayload = PureMp4Muxer.annexBToAvcc(rawBytes)
                        if (avccPayload.isNotEmpty()) {
                            val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            state.samples.add(PureMp4Muxer.EncodedSample(avccPayload, isKey))
                        }
                    }
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }
}
