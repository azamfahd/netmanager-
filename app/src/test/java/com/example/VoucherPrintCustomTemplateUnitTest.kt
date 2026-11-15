package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.data.model.HotspotCard
import com.example.ui.util.CardDesignConfig
import com.example.ui.util.CardTemplateType
import com.example.ui.util.VoucherPrintUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VoucherPrintCustomTemplateUnitTest {

    private class RecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        data class DrawnText(val text: String, val x: Float, val y: Float)

        val drawnTexts = mutableListOf<DrawnText>()
        var drawnLineCount = 0
        var drawnBitmapCount = 0

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            drawnTexts.add(DrawnText(text, x, y))
            super.drawText(text, x, y, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            drawnLineCount++
            super.drawLine(startX, startY, stopX, stopY, paint)
        }

        override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
            drawnBitmapCount++
            super.drawBitmap(bitmap, src, dst, paint)
        }
    }

    @Test
    fun customImageModeDrawsOnlyCardNumberAndStripsAllPreviousTemplateOverlays() {
        val pageBitmap = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val customCardBitmap = Bitmap.createBitmap(300, 120, Bitmap.Config.ARGB_8888)
        val canvas = RecordingCanvas(pageBitmap)

        val cards = listOf(
            HotspotCard(
                id = "*1",
                name = "827245",
                password = "",
                profile = "200",
                limitUptime = "24 ساعة",
                limitBytesTotal = 1024L * 1024L * 1024L,
                price = "200 ر.ي"
            ),
            HotspotCard(
                id = "*2",
                name = "482187",
                password = "",
                profile = "200",
                limitUptime = "24 ساعة",
                limitBytesTotal = 1024L * 1024L * 1024L,
                price = "200 ر.ي"
            )
        )

        val config = CardDesignConfig(
            templateType = CardTemplateType.CUSTOM_IMAGE,
            networkName = "سكود نت",
            contactPhone = "777000111",
            columns = 4,
            rows = 15,
            offsetXPercent = 0f,
            offsetYPercent = 0f,
            fontSizeSp = 15f
        )

        VoucherPrintUtils.drawCardsPage(
            canvas = canvas,
            pageIndex = 0,
            pageCards = cards,
            config = config,
            customBgBitmap = customCardBitmap,
            pageWidth = 1240,
            pageHeight = 1754
        )

        assertEquals(2, canvas.drawnBitmapCount)
        assertEquals(0, canvas.drawnLineCount)
        assertEquals(listOf("827245", "482187"), canvas.drawnTexts.map { it.text })

        // Verify none of the built-in texts leaked onto the custom image
        assertFalse(canvas.drawnTexts.any { it.text.contains("رمز الدخول") })
        assertFalse(canvas.drawnTexts.any { it.text.contains("سكود نت") })
        assertFalse(canvas.drawnTexts.any { it.text.contains("777000111") })
        assertFalse(canvas.drawnTexts.any { it.text.contains("ساعة") })
        assertFalse(canvas.drawnTexts.any { it.text.contains("ريال") })

        // Verify card 0 is centered horizontally inside its cardRect (marginX=20, cardW=300, right=left+298 -> centerX=169)
        val firstCardCenterX = 20f + (1200f / 4f - 2f) / 2f
        assertEquals(firstCardCenterX, canvas.drawnTexts[0].x, 0.5f)
    }

    @Test
    fun builtInTemplateModeStillDrawsFullTemplateDetails() {
        val pageBitmap = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val canvas = RecordingCanvas(pageBitmap)

        val cards = listOf(
            HotspotCard(
                id = "*1",
                name = "921400",
                password = "",
                profile = "200",
                limitUptime = "24 ساعة",
                limitBytesTotal = 1024L * 1024L * 1024L,
                price = "200 ر.ي"
            )
        )

        val config = CardDesignConfig(
            templateType = CardTemplateType.SCOUD_BLUE,
            networkName = "سكود نت",
            contactPhone = "777000111",
            columns = 4,
            rows = 15
        )

        VoucherPrintUtils.drawCardsPage(
            canvas = canvas,
            pageIndex = 0,
            pageCards = cards,
            config = config,
            customBgBitmap = null,
            pageWidth = 1240,
            pageHeight = 1754
        )

        assertTrue(canvas.drawnLineCount == 1)
        assertTrue(canvas.drawnTexts.any { it.text == "رمز الدخول: 921400" })
        assertTrue(canvas.drawnTexts.any { it.text.contains("سكود نت | ت: 777000111") })
        assertTrue(canvas.drawnTexts.any { it.text == "200" })
        assertTrue(canvas.drawnTexts.any { it.text == "ريال" })
    }

    @Test
    fun highRes300DpiPageRendersCustomCardCleanlyAndClipsBounds() {
        val w = VoucherPrintUtils.DEFAULT_EXPORT_WIDTH
        val h = VoucherPrintUtils.DEFAULT_EXPORT_HEIGHT
        assertEquals(2480, w)
        assertEquals(3508, h)

        val pageBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val customCardBitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        val canvas = RecordingCanvas(pageBitmap)

        val cards = listOf(
            HotspotCard(
                id = "*1",
                name = "774411",
                password = "",
                profile = "500",
                limitUptime = "10 ساعات",
                price = "500 ر.ي"
            )
        )

        val config = CardDesignConfig(
            templateType = CardTemplateType.CUSTOM_IMAGE,
            networkName = "سكود نت",
            contactPhone = "777000111",
            columns = 4,
            rows = 15,
            cardWidthMm = 50f,
            cardHeightMm = 18f,
            fontSizeSp = 15f
        )

        VoucherPrintUtils.drawCardsPage(
            canvas = canvas,
            pageIndex = 0,
            pageCards = cards,
            config = config,
            customBgBitmap = customCardBitmap,
            pageWidth = w,
            pageHeight = h
        )

        assertEquals(1, canvas.drawnBitmapCount)
        assertEquals(listOf("774411"), canvas.drawnTexts.map { it.text })
        assertTrue("Expected card code within 300 DPI cell bounds", canvas.drawnTexts[0].x in 40f..640f)
    }
}
