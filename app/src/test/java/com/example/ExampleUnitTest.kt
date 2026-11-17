package com.example

import com.example.data.PureMp4Muxer
import com.example.model.AspectRatioOption
import com.example.model.CaptureOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {
    @Test
    fun pureMp4Muxer_writesValidIsoBmffMp4File() {
        val annexBConfig = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1E, 0x2A,
            0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte()
        )
        val (sps, pps) = PureMp4Muxer.extractSpsPpsFromAnnexB(annexBConfig)
        assertNotNull(sps)
        assertNotNull(pps)

        val annexBFrame = byteArrayOf(0, 0, 0, 1, 0x65, 0x11, 0x22, 0x33, 0x44)
        val avccFrame = PureMp4Muxer.annexBToAvcc(annexBFrame)
        assertTrue(avccFrame.size == 9)

        val tempFile = File.createTempFile("test_cine", ".mp4")
        try {
            val samples = List(24) { idx ->
                PureMp4Muxer.EncodedSample(avccFrame, isKeyFrame = idx == 0)
            }
            val written = PureMp4Muxer.writeMp4File(
                outputFile = tempFile,
                width = 640,
                height = 368,
                fps = 24,
                sps = sps!!,
                pps = pps!!,
                samples = samples
            )
            assertTrue(written)
            assertTrue(tempFile.length() > 100L)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun orientationAspectRatio_adaptsForPortraitAndLandscape() {
        assertEquals("16:9", AspectRatioOption.RATIO_16_9.displayLabel(CaptureOrientationMode.LANDSCAPE))
        assertEquals("9:16", AspectRatioOption.RATIO_16_9.displayLabel(CaptureOrientationMode.PORTRAIT))
        assertEquals("19:6", AspectRatioOption.RATIO_19_6.displayLabel(CaptureOrientationMode.LANDSCAPE))
        assertEquals("6:19", AspectRatioOption.RATIO_19_6.displayLabel(CaptureOrientationMode.PORTRAIT))
    }
}
