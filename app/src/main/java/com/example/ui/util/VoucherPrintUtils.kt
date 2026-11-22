package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.HotspotCard
import com.example.data.util.SerialCorner
import com.example.data.util.VoucherSerialEngine
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

enum class CardTemplateType(val title: String) {
    SCOUD_BLUE("قالب سكود نت الأزرق (المعتمد)"),
    MIKROTIK_CLASSIC("قالب ميكروتك الكلاسيكي"),
    VIP_GOLD("قالب VIP الذهبي"),
    EMERALD_GREEN("قالب تيربو الأخضر"),
    CUSTOM_IMAGE("صورة تصميم مخصصة من الجهاز")
}

data class CardDesignConfig(
    val templateType: CardTemplateType = CardTemplateType.SCOUD_BLUE,
    val customImageUri: Uri? = null,
    val networkName: String = "سكود نت",
    val contactPhone: String = "777000111",
    val defaultHeaderFormat: String = "{time} . {data} . {validity}",
    val offsetXPercent: Float = 0f, // horizontal offset for code
    val offsetYPercent: Float = 0f, // vertical offset for code
    val fontSizeSp: Float = 14f,
    val columns: Int = 4,
    val rows: Int = 15,
    val cardWidthMm: Float = 50f,
    val cardHeightMm: Float = 18f,
    val customCodeColorArgb: Int = Color.BLACK,
    val showSerialNumber: Boolean = false,
    val serialPrefix: String = "#",
    val serialCorner: SerialCorner = SerialCorner.TOP_END,
    val serialOffsetXPercent: Float = 0f,
    val serialOffsetYPercent: Float = 0f,
    val serialFontSizeSp: Float = 8f,
    val serialMap: Map<String, String> = emptyMap()
) {
    val safeColumns: Int get() = columns.coerceAtLeast(1)
    val safeRows: Int get() = rows.coerceAtLeast(1)
    val cardsPerPage: Int get() = safeColumns * safeRows
}

object VoucherPrintUtils {

    const val DEFAULT_EXPORT_WIDTH = 2480
    const val DEFAULT_EXPORT_HEIGHT = 3508

    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadBitmapFromUri(context: Context, uri: Uri?, maxDim: Int = 3072): Bitmap? {
        if (uri == null) return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            if (boundsOpts.outWidth > maxDim || boundsOpts.outHeight > maxDim) {
                val halfW = boundsOpts.outWidth / 2
                val halfH = boundsOpts.outHeight / 2
                while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a page of cards onto an Android Canvas (used for PDF and Bitmap export)
     */
    fun drawCardsPage(
        canvas: Canvas,
        pageIndex: Int,
        pageCards: List<HotspotCard>,
        config: CardDesignConfig,
        customBgBitmap: Bitmap?,
        pageWidth: Int,
        pageHeight: Int
    ) {
        val safeCols = config.safeColumns
        val safeRows = config.safeRows
        val scale = pageWidth / 1240f
        val marginX = 20f * scale
        val marginY = 20f * scale
        val printableWidth = pageWidth - (marginX * 2)
        val printableHeight = pageHeight - (marginY * 2)

        val cardW = printableWidth / safeCols
        val cardH = printableHeight / safeRows

        val paintBg = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val paintBorder = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
            color = Color.rgb(200, 215, 230)
        }
        val paintTextHeader = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 30, 30)
            textSize = cardH * 0.16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintCodeValue = Paint().apply {
            isAntiAlias = true
            color = config.customCodeColorArgb
            textSize = cardH * 0.22f * (config.fontSizeSp / 14f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintFooter = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(70, 70, 70)
            textSize = cardH * 0.13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val paintPriceBadgeBg = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = when (config.templateType) {
                CardTemplateType.SCOUD_BLUE -> Color.rgb(2, 119, 189)
                CardTemplateType.VIP_GOLD -> Color.rgb(212, 160, 23)
                CardTemplateType.EMERALD_GREEN -> Color.rgb(0, 137, 123)
                else -> Color.rgb(21, 101, 192)
            }
        }
        val paintPriceText = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = cardH * 0.18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintPriceSub = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = cardH * 0.12f
            textAlign = Paint.Align.CENTER
        }
        val paintSerial = Paint().apply {
            isAntiAlias = true
            color = if (config.templateType == CardTemplateType.CUSTOM_IMAGE) config.customCodeColorArgb else Color.rgb(38, 50, 56)
            textSize = (cardH * 0.135f * (config.serialFontSizeSp / 8f)).coerceAtLeast(6f * scale)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Fill background of page
        canvas.drawColor(Color.WHITE)

        val totalCardsOnThisPage = config.cardsPerPage
        val startIndex = pageIndex * totalCardsOnThisPage

        for (row in 0 until safeRows) {
            for (col in 0 until safeCols) {
                val cardIndexInList = startIndex + (row * safeCols + col)
                if (cardIndexInList >= pageCards.size) break

                val card = pageCards[cardIndexInList]
                val left = marginX + col * cardW
                val top = marginY + row * cardH
                val right = left + cardW - (2f * scale)
                val bottom = top + cardH - (2f * scale)
                val cardRect = RectF(left, top, right, bottom)

                // 1. Draw card background
                canvas.drawRoundRect(cardRect, 4f * scale, 4f * scale, paintBg)

                val isCustomImg = customBgBitmap != null && !customBgBitmap.isRecycled && config.templateType == CardTemplateType.CUSTOM_IMAGE
                if (isCustomImg) {
                    val srcRect = Rect(0, 0, customBgBitmap.width, customBgBitmap.height)
                    canvas.drawBitmap(customBgBitmap, srcRect, cardRect, bitmapPaint)
                } else {
                    val badgeWidth = cardW * 0.20f
                    val badgeRect = RectF(right - badgeWidth, top, right, bottom)
                    canvas.drawRoundRect(badgeRect, 2f * scale, 2f * scale, paintPriceBadgeBg)

                    val normalizedPrice = com.example.data.util.FinanceAnalyticsEngine.normalizeDigits(card.price)
                    val priceNum = Regex("""\d+(?:\.\d+)?""").find(normalizedPrice)?.value ?: "0"
                    canvas.drawText(priceNum, badgeRect.centerX(), top + cardH * 0.45f, paintPriceText)
                    canvas.drawText("ريال", badgeRect.centerX(), top + cardH * 0.75f, paintPriceSub)

                    val leftBarRect = RectF(left, top, left + (4f * scale), bottom)
                    canvas.drawRect(leftBarRect, paintPriceBadgeBg)
                }

                // 2. Draw border
                canvas.drawRoundRect(cardRect, 4f * scale, 4f * scale, paintBorder)

                // 3. Card Texts (offset formulas aligned with SingleCardRender preview)
                val baseCenterX = if (isCustomImg) cardRect.centerX() else left + cardW * 0.40f
                val contentCenterX = baseCenterX + (config.offsetXPercent / 100f) * cardW * 0.45f
                val offsetYDelta = (config.offsetYPercent / 50f) * cardH * 0.45f

                if (isCustomImg) {
                    val codeY = top + (cardH * 0.55f) + offsetYDelta
                    canvas.drawText(card.name, contentCenterX, codeY, paintCodeValue)
                } else {
                    val timeLimit = if (card.limitUptime.isNotBlank()) card.limitUptime else "مفتوح"
                    val quotaMb = if (card.limitBytesTotal > 0L) "${card.limitBytesTotal / (1024L * 1024L)} ميجا" else "مفتوح"
                    val headerStr = "$timeLimit . $quotaMb"
                    canvas.drawText(headerStr, contentCenterX, top + cardH * 0.24f, paintTextHeader)

                    val codeY = top + cardH * 0.58f + offsetYDelta
                    canvas.drawText("رمز الدخول: ${card.name}", contentCenterX, codeY, paintCodeValue)

                    val linePaint = Paint().apply {
                        isAntiAlias = true
                        color = Color.rgb(210, 220, 230)
                        strokeWidth = 1f * scale
                    }
                    canvas.drawLine(left + (10f * scale), top + cardH * 0.68f, right - (cardW * 0.25f), top + cardH * 0.68f, linePaint)

                    val footerStr = "${config.networkName} | ت: ${config.contactPhone}"
                    canvas.drawText(footerStr, contentCenterX, top + cardH * 0.88f, paintFooter)
                }

                // 4. Optional Configurable Serial Number Badge
                if (config.showSerialNumber) {
                    val resolvedDigits = config.serialMap[card.name]
                        ?: config.serialMap[card.id]
                        ?: VoucherSerialEngine.extractCardSerial(card)?.let { VoucherSerialEngine.formatSerialDigits(it) }
                        ?: VoucherSerialEngine.formatSerialDigits((cardIndexInList + 1).toLong())
                    val serialLabel = "${config.serialPrefix}$resolvedDigits"
                    val snShiftX = (config.serialOffsetXPercent / 100f) * cardW * 0.45f
                    val snShiftY = (config.serialOffsetYPercent / 50f) * cardH * 0.45f
                    val rightMarginAnchor = if (isCustomImg) right - (8f * scale) else right - (cardW * 0.23f)
                    val leftMarginAnchor = left + (8f * scale)
                    val topAnchorY = top + (cardH * 0.16f)
                    val bottomAnchorY = bottom - (cardH * 0.08f)

                    val (snX, snY, align) = when (config.serialCorner) {
                        SerialCorner.TOP_START -> Triple(rightMarginAnchor, topAnchorY, Paint.Align.RIGHT)
                        SerialCorner.TOP_END -> Triple(leftMarginAnchor, topAnchorY, Paint.Align.LEFT)
                        SerialCorner.BOTTOM_START -> Triple(rightMarginAnchor, bottomAnchorY, Paint.Align.RIGHT)
                        SerialCorner.BOTTOM_END -> Triple(leftMarginAnchor, bottomAnchorY, Paint.Align.LEFT)
                    }
                    paintSerial.textAlign = align
                    canvas.drawText(serialLabel, snX + snShiftX, snY + snShiftY, paintSerial)
                }
            }
        }
    }

    /**
     * Export all pages to a multi-page PDF document on a background worker thread with 300 DPI high resolution
     */
    fun exportToPdf(
        context: Context,
        cards: List<HotspotCard>,
        config: CardDesignConfig,
        onComplete: (File?) -> Unit
    ) {
        if (cards.isEmpty()) {
            Toast.makeText(context, "لا توجد كروت محددة للتصدير", Toast.LENGTH_SHORT).show()
            onComplete(null)
            return
        }
        val appContext = context.applicationContext
        Thread {
            val pdfDocument = PdfDocument()
            var customBitmap: Bitmap? = null
            try {
                customBitmap = loadBitmapFromUri(appContext, config.customImageUri)
                val pageWidth = 2480
                val pageHeight = 3508

                val cardsPerPage = max(1, config.cardsPerPage)
                val totalPages = max(1, (cards.size + cardsPerPage - 1) / cardsPerPage)

                for (p in 0 until totalPages) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p + 1).create()
                    val page = pdfDocument.startPage(pageInfo)

                    drawCardsPage(
                        canvas = page.canvas,
                        pageIndex = p,
                        pageCards = cards,
                        config = config,
                        customBgBitmap = customBitmap,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight
                    )

                    pdfDocument.finishPage(page)
                }

                val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
                val pdfFile = File(exportDir, "vouchers_sheet_${System.currentTimeMillis()}.pdf")
                FileOutputStream(pdfFile).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                mainHandler.post { onComplete(pdfFile) }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onComplete(null) }
            } finally {
                try { pdfDocument.close() } catch (_: Exception) {}
                try { customBitmap?.recycle() } catch (_: Exception) {}
            }
        }.start()
    }

    /**
     * Export a single page as a high-resolution PNG image (300 DPI) on a background worker thread
     */
    fun exportPageToImage(
        context: Context,
        cards: List<HotspotCard>,
        pageIndex: Int,
        config: CardDesignConfig,
        onComplete: (File?) -> Unit
    ) {
        if (cards.isEmpty()) {
            Toast.makeText(context, "لا توجد كروت محددة للتصدير", Toast.LENGTH_SHORT).show()
            onComplete(null)
            return
        }
        val appContext = context.applicationContext
        Thread {
            var customBitmap: Bitmap? = null
            var bitmap: Bitmap? = null
            try {
                customBitmap = loadBitmapFromUri(appContext, config.customImageUri)
                val pageWidth = 2480
                val pageHeight = 3508

                bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                drawCardsPage(
                    canvas = canvas,
                    pageIndex = pageIndex.coerceAtLeast(0),
                    pageCards = cards,
                    config = config,
                    customBgBitmap = customBitmap,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight
                )

                val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
                val imgFile = File(exportDir, "vouchers_page_${pageIndex + 1}_${System.currentTimeMillis()}.png")
                FileOutputStream(imgFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                mainHandler.post { onComplete(imgFile) }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onComplete(null) }
            } finally {
                try { bitmap?.recycle() } catch (_: Exception) {}
                try { customBitmap?.recycle() } catch (_: Exception) {}
            }
        }.start()
    }

    /**
     * Share exported file via FileProvider
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "فشلت مشاركة الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun renderPageBitmap(
        context: Context,
        cards: List<HotspotCard>,
        pageIndex: Int,
        config: CardDesignConfig,
        targetWidth: Int = 1800
    ): Bitmap? {
        var customBg: Bitmap? = null
        var bitmap: Bitmap? = null
        return try {
            val pageWidth = targetWidth
            val pageHeight = (targetWidth * 1.4142f).toInt()
            bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            customBg = loadBitmapFromUri(context, config.customImageUri)
            drawCardsPage(
                canvas = canvas,
                pageIndex = pageIndex.coerceAtLeast(0),
                pageCards = cards,
                config = config,
                customBgBitmap = customBg,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
            bitmap
        } catch (_: Exception) {
            try { bitmap?.recycle() } catch (_: Exception) {}
            null
        } finally {
            try { customBg?.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Native Android Print Adapter with high resolution (300 DPI)
     */
    fun printVouchers(
        context: Context,
        cards: List<HotspotCard>,
        config: CardDesignConfig
    ) {
        if (cards.isEmpty()) {
            Toast.makeText(context, "لا توجد كروت محددة للطباعة", Toast.LENGTH_SHORT).show()
            return
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "خدمة الطباعة غير متوفرة على هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }

        var customBitmap: Bitmap? = loadBitmapFromUri(context, config.customImageUri)

        val printAdapter = object : PrintDocumentAdapter() {
            private var totalPages = 1

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val cardsPerPage = max(1, config.cardsPerPage)
                totalPages = max(1, (cards.size + cardsPerPage - 1) / cardsPerPage)

                val info = PrintDocumentInfo.Builder("كروت_الهوتسبوت_${System.currentTimeMillis()}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPages)
                    .build()

                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                val fd = destination?.fileDescriptor
                if (fd == null) {
                    callback?.onWriteFailed("وجهة الطباعة غير متاحة")
                    return
                }
                val pdfDocument = PdfDocument()
                val pageWidth = 2480
                val pageHeight = 3508
                try {
                    for (p in 0 until totalPages) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            return
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p + 1).create()
                        val page = pdfDocument.startPage(pageInfo)

                        drawCardsPage(
                            canvas = page.canvas,
                            pageIndex = p,
                            pageCards = cards,
                            config = config,
                            customBgBitmap = customBitmap,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight
                        )

                        pdfDocument.finishPage(page)
                    }

                    FileOutputStream(fd).use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                } finally {
                    try { pdfDocument.close() } catch (_: Exception) {}
                }
            }

            override fun onFinish() {
                super.onFinish()
                try { customBitmap?.recycle() } catch (_: Exception) {}
                customBitmap = null
            }
        }

        printManager.print("طباعة كروت شبكة ميكروتك", printAdapter, PrintAttributes.Builder().build())
    }
}

