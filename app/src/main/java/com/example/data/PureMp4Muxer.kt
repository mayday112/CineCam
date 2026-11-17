package com.example.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-Kotlin ISO Base Media File Format (ISO/IEC 14496-12) MP4 writer for H.264 (avc1) video tracks.
 * Writes `ftyp`, `mdat`, and `moov` (`mvhd`, `trak`, `tkhd`, `mdia`, `mdhd`, `hdlr`, `minf`, `vmhd`,
 * `dinf`, `stbl` -> `stsd`/`avc1`/`avcC`, `stts`, `stss`, `stsc`, `stsz`, `stco`) directly to disk
 * without invoking native `MPEG4Writer` / `MediaMuxer`.
 */
object PureMp4Muxer {

    class EncodedSample(
        val avccData: ByteArray,
        val isKeyFrame: Boolean
    )

    /**
     * Converts Annex-B H.264 bitstream (`00 00 00 01` or `00 00 01` start codes) into
     * MP4 `avc1` length-prefixed NAL units (4-byte big-endian length prefix per NAL unit).
     */
    fun annexBToAvcc(data: ByteArray): ByteArray {
        val nalUnits = splitAnnexBNalUnits(data)
        if (nalUnits.isEmpty()) {
            // Already length-prefixed or raw single NAL unit
            val out = ByteBuffer.allocate(4 + data.size).order(ByteOrder.BIG_ENDIAN)
            out.putInt(data.size)
            out.put(data)
            return out.array()
        }
        val out = ByteArrayOutputStream(data.size + nalUnits.size * 4)
        val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        for (nal in nalUnits) {
            if (nal.isEmpty()) continue
            val nalType = nal[0].toInt() and 0x1F
            // Skip SPS (7) and PPS (8) in sample data as they belong in avcC box
            if (nalType == 7 || nalType == 8) continue
            lenBuf.clear()
            lenBuf.putInt(nal.size)
            out.write(lenBuf.array())
            out.write(nal)
        }
        return out.toByteArray()
    }

    fun extractSpsPpsFromAnnexB(data: ByteArray): Pair<ByteArray?, ByteArray?> {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        for (nal in splitAnnexBNalUnits(data)) {
            if (nal.isEmpty()) continue
            when (nal[0].toInt() and 0x1F) {
                7 -> sps = nal
                8 -> pps = nal
            }
        }
        return sps to pps
    }

    private fun splitAnnexBNalUnits(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var i = 0
        var nalStart = -1

        while (i < data.size - 3) {
            val is4Byte = data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
                    data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1
            val is3Byte = !is4Byte && data[i].toInt() == 0 &&
                    data[i + 1].toInt() == 0 && data[i + 2].toInt() == 1

            if (is4Byte || is3Byte) {
                if (nalStart >= 0 && i > nalStart) {
                    result.add(data.copyOfRange(nalStart, i))
                }
                val step = if (is4Byte) 4 else 3
                nalStart = i + step
                i += step
            } else {
                i++
            }
        }
        if (nalStart in 0 until data.size) {
            result.add(data.copyOfRange(nalStart, data.size))
        }
        return result
    }

    fun writeMp4File(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        sps: ByteArray,
        pps: ByteArray,
        samples: List<EncodedSample>
    ): Boolean {
        if (samples.isEmpty() || sps.isEmpty() || pps.isEmpty()) return false

        val safeFps = fps.coerceIn(1, 240)
        val mediaTimescale = 90_000
        val sampleDelta = mediaTimescale / safeFps
        val totalMediaDuration = samples.size * sampleDelta
        val movieTimescale = 1_000
        val totalMovieDuration = (samples.size * 1000L / safeFps).toInt().coerceAtLeast(1)

        val ftypBox = buildFtypBox()
        val mdatHeaderSize = 8
        val firstSampleOffset = ftypBox.size + mdatHeaderSize

        val sampleOffsets = IntArray(samples.size)
        val sampleSizes = IntArray(samples.size)
        var currentOffset = firstSampleOffset
        var totalSamplesBytes = 0
        for (idx in samples.indices) {
            val sz = samples[idx].avccData.size
            sampleOffsets[idx] = currentOffset
            sampleSizes[idx] = sz
            currentOffset += sz
            totalSamplesBytes += sz
        }

        val moovBox = buildMoovBox(
            width = width,
            height = height,
            movieTimescale = movieTimescale,
            movieDuration = totalMovieDuration,
            mediaTimescale = mediaTimescale,
            mediaDuration = totalMediaDuration,
            sampleDelta = sampleDelta,
            sps = sps,
            pps = pps,
            samples = samples,
            sampleSizes = sampleSizes,
            sampleOffsets = sampleOffsets
        )

        FileOutputStream(outputFile).use { fos ->
            // 1. Write ftyp box
            fos.write(ftypBox)

            // 2. Write mdat box header + raw sample payloads
            val mdatHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(8 + totalSamplesBytes)
                put("mdat".toByteArray(Charsets.US_ASCII))
            }.array()
            fos.write(mdatHeader)
            for (sample in samples) {
                fos.write(sample.avccData)
            }

            // 3. Write moov box
            fos.write(moovBox)
            fos.flush()
        }
        return outputFile.exists() && outputFile.length() > 0L
    }

    private fun buildFtypBox(): ByteArray = box("ftyp") {
        putAscii("isom")
        putInt(0x00000200)
        putAscii("isom")
        putAscii("iso2")
        putAscii("avc1")
        putAscii("mp41")
    }

    private fun buildMoovBox(
        width: Int,
        height: Int,
        movieTimescale: Int,
        movieDuration: Int,
        mediaTimescale: Int,
        mediaDuration: Int,
        sampleDelta: Int,
        sps: ByteArray,
        pps: ByteArray,
        samples: List<EncodedSample>,
        sampleSizes: IntArray,
        sampleOffsets: IntArray
    ): ByteArray = box("moov") {
        putBytes(buildMvhdBox(movieTimescale, movieDuration))
        putBytes(
            buildTrakBox(
                width = width,
                height = height,
                movieDuration = movieDuration,
                mediaTimescale = mediaTimescale,
                mediaDuration = mediaDuration,
                sampleDelta = sampleDelta,
                sps = sps,
                pps = pps,
                samples = samples,
                sampleSizes = sampleSizes,
                sampleOffsets = sampleOffsets
            )
        )
    }

    private fun buildMvhdBox(timescale: Int, duration: Int): ByteArray = fullBox("mvhd", 0, 0) {
        putInt(0) // creation_time
        putInt(0) // modification_time
        putInt(timescale)
        putInt(duration)
        putInt(0x00010000) // rate = 1.0
        putShort(0x0100) // volume = 1.0
        putShort(0) // reserved
        putInt(0)
        putInt(0)
        // Unity matrix (9 ints)
        putInt(0x00010000); putInt(0); putInt(0)
        putInt(0); putInt(0x00010000); putInt(0)
        putInt(0); putInt(0); putInt(0x40000000)
        // Pre-defined (6 ints)
        repeat(6) { putInt(0) }
        putInt(2) // next_track_ID
    }

    private fun buildTrakBox(
        width: Int,
        height: Int,
        movieDuration: Int,
        mediaTimescale: Int,
        mediaDuration: Int,
        sampleDelta: Int,
        sps: ByteArray,
        pps: ByteArray,
        samples: List<EncodedSample>,
        sampleSizes: IntArray,
        sampleOffsets: IntArray
    ): ByteArray = box("trak") {
        putBytes(buildTkhdBox(width, height, movieDuration))
        putBytes(
            buildMdiaBox(
                width = width,
                height = height,
                mediaTimescale = mediaTimescale,
                mediaDuration = mediaDuration,
                sampleDelta = sampleDelta,
                sps = sps,
                pps = pps,
                samples = samples,
                sampleSizes = sampleSizes,
                sampleOffsets = sampleOffsets
            )
        )
    }

    private fun buildTkhdBox(width: Int, height: Int, duration: Int): ByteArray =
        fullBox("tkhd", 0, 0x000007) { // track_enabled | track_in_movie | track_in_preview
            putInt(0) // creation_time
            putInt(0) // modification_time
            putInt(1) // track_ID
            putInt(0) // reserved
            putInt(duration)
            putInt(0) // reserved
            putInt(0) // reserved
            putShort(0) // layer
            putShort(0) // alternate_group
            putShort(0) // volume (0 for video)
            putShort(0) // reserved
            // Unity matrix
            putInt(0x00010000); putInt(0); putInt(0)
            putInt(0); putInt(0x00010000); putInt(0)
            putInt(0); putInt(0); putInt(0x40000000)
            putInt(width shl 16) // 16.16 fixed-point width
            putInt(height shl 16) // 16.16 fixed-point height
        }

    private fun buildMdiaBox(
        width: Int,
        height: Int,
        mediaTimescale: Int,
        mediaDuration: Int,
        sampleDelta: Int,
        sps: ByteArray,
        pps: ByteArray,
        samples: List<EncodedSample>,
        sampleSizes: IntArray,
        sampleOffsets: IntArray
    ): ByteArray = box("mdia") {
        putBytes(buildMdhdBox(mediaTimescale, mediaDuration))
        putBytes(buildHdlrBox())
        putBytes(
            buildMinfBox(
                width = width,
                height = height,
                sampleDelta = sampleDelta,
                sps = sps,
                pps = pps,
                samples = samples,
                sampleSizes = sampleSizes,
                sampleOffsets = sampleOffsets
            )
        )
    }

    private fun buildMdhdBox(timescale: Int, duration: Int): ByteArray = fullBox("mdhd", 0, 0) {
        putInt(0)
        putInt(0)
        putInt(timescale)
        putInt(duration)
        putShort(0x55C4) // language = "und"
        putShort(0)
    }

    private fun buildHdlrBox(): ByteArray = fullBox("hdlr", 0, 0) {
        putInt(0) // pre_defined
        putAscii("vide") // handler_type
        putInt(0); putInt(0); putInt(0) // reserved
        putAscii("VideoHandler")
        putByte(0) // null terminator
    }

    private fun buildMinfBox(
        width: Int,
        height: Int,
        sampleDelta: Int,
        sps: ByteArray,
        pps: ByteArray,
        samples: List<EncodedSample>,
        sampleSizes: IntArray,
        sampleOffsets: IntArray
    ): ByteArray = box("minf") {
        putBytes(fullBox("vmhd", 0, 1) {
            putShort(0) // graphicsmode
            putShort(0); putShort(0); putShort(0) // opcolor
        })
        putBytes(box("dinf") {
            putBytes(fullBox("dref", 0, 0) {
                putInt(1) // entry_count
                putBytes(fullBox("url ", 0, 1) {})
            })
        })
        putBytes(
            buildStblBox(
                width = width,
                height = height,
                sampleDelta = sampleDelta,
                sps = sps,
                pps = pps,
                samples = samples,
                sampleSizes = sampleSizes,
                sampleOffsets = sampleOffsets
            )
        )
    }

    private fun buildStblBox(
        width: Int,
        height: Int,
        sampleDelta: Int,
        sps: ByteArray,
        pps: ByteArray,
        samples: List<EncodedSample>,
        sampleSizes: IntArray,
        sampleOffsets: IntArray
    ): ByteArray = box("stbl") {
        // 1. stsd (Sample Description -> avc1 -> avcC)
        putBytes(fullBox("stsd", 0, 0) {
            putInt(1) // entry_count = 1
            putBytes(buildAvc1Box(width, height, sps, pps))
        })

        // 2. stts (Decoding Time to Sample)
        putBytes(fullBox("stts", 0, 0) {
            putInt(1) // entry_count = 1
            putInt(samples.size)
            putInt(sampleDelta)
        })

        // 3. stss (Sync Sample Box - 1-based indices of keyframes)
        val syncIndices = samples.mapIndexedNotNull { idx, sample ->
            if (sample.isKeyFrame || idx == 0) idx + 1 else null
        }
        putBytes(fullBox("stss", 0, 0) {
            putInt(syncIndices.size)
            for (syncIdx in syncIndices) {
                putInt(syncIdx)
            }
        })

        // 4. stsc (Sample to Chunk - 1 sample per chunk)
        putBytes(fullBox("stsc", 0, 0) {
            putInt(1) // entry_count = 1
            putInt(1) // first_chunk = 1
            putInt(1) // samples_per_chunk = 1
            putInt(1) // sample_description_index = 1
        })

        // 5. stsz (Sample Size Box)
        putBytes(fullBox("stsz", 0, 0) {
            putInt(0) // sample_size = 0 (variable)
            putInt(sampleSizes.size)
            for (sz in sampleSizes) {
                putInt(sz)
            }
        })

        // 6. stco (Chunk Offset Box)
        putBytes(fullBox("stco", 0, 0) {
            putInt(sampleOffsets.size)
            for (offset in sampleOffsets) {
                putInt(offset)
            }
        })
    }

    private fun buildAvc1Box(
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray
    ): ByteArray = box("avc1") {
        repeat(6) { putByte(0) } // reserved
        putShort(1) // data_reference_index = 1
        putShort(0) // pre_defined
        putShort(0) // reserved
        putInt(0); putInt(0); putInt(0) // pre_defined
        putShort(width)
        putShort(height)
        putInt(0x00480000) // horizresolution = 72 dpi
        putInt(0x00480000) // vertresolution = 72 dpi
        putInt(0) // reserved
        putShort(1) // frame_count = 1
        repeat(32) { putByte(0) } // compressorname (32 bytes)
        putShort(0x0018) // depth = 24
        putShort(-1) // pre_defined = -1
        putBytes(buildAvcCBox(sps, pps))
    }

    private fun buildAvcCBox(sps: ByteArray, pps: ByteArray): ByteArray = box("avcC") {
        val profileIdc = if (sps.size > 1) sps[1].toInt() and 0xFF else 0x42
        val profileCompat = if (sps.size > 2) sps[2].toInt() and 0xFF else 0x80
        val levelIdc = if (sps.size > 3) sps[3].toInt() and 0xFF else 0x1E

        putByte(1) // configurationVersion = 1
        putByte(profileIdc)
        putByte(profileCompat)
        putByte(levelIdc)
        putByte(0xFF) // 6 bits reserved (111111) + 2 bits lengthSizeMinusOne (3 -> 4 bytes)
        putByte(0xE1) // 3 bits reserved (111) + 5 bits numOfSequenceParameterSets (1)
        putShort(sps.size)
        putBytes(sps)
        putByte(1) // numOfPictureParameterSets = 1
        putShort(pps.size)
        putBytes(pps)
    }

    private class BoxWriter {
        private val out = ByteArrayOutputStream()

        fun putByte(v: Int) {
            out.write(v and 0xFF)
        }

        fun putShort(v: Int) {
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        fun putInt(v: Int) {
            out.write((v ushr 24) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        fun putAscii(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        fun putBytes(bytes: ByteArray) {
            out.write(bytes)
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private fun box(type: String, block: BoxWriter.() -> Unit): ByteArray {
        val payload = BoxWriter().apply(block).toByteArray()
        return BoxWriter().apply {
            putInt(8 + payload.size)
            putAscii(type)
            putBytes(payload)
        }.toByteArray()
    }

    private fun fullBox(type: String, version: Int, flags: Int, block: BoxWriter.() -> Unit): ByteArray {
        return box(type) {
            putInt(((version and 0xFF) shl 24) or (flags and 0x00FFFFFF))
            block()
        }
    }
}
