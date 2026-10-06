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
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.HotspotCard
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

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
    val cardHeightMm: Float = 18f
) {
    val cardsPerPage: Int get() = columns * rows
}

object VoucherPrintUtils {

    fun loadBitmapFromUri(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
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
        val marginX = 20f
        val marginY = 20f
        val printableWidth = pageWidth - (marginX * 2)
        val printableHeight = pageHeight - (marginY * 2)

        val cardW = printableWidth / config.columns
        val cardH = printableHeight / config.rows

        val paintBg = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val paintBorder = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.rgb(200, 215, 230)
        }
        val paintTextHeader = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 30, 30)
            textSize = cardH * 0.16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintCodeLabel = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(2, 119, 189)
            textSize = cardH * 0.14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val paintCodeValue = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(10, 20, 30)
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

        // Fill background of page
        canvas.drawColor(Color.WHITE)

        val totalCardsOnThisPage = config.cardsPerPage
        val startIndex = pageIndex * totalCardsOnThisPage

        for (row in 0 until config.rows) {
            for (col in 0 until config.columns) {
                val cardIndexInList = startIndex + (row * config.columns + col)
                if (cardIndexInList >= pageCards.size) break

                val card = pageCards[cardIndexInList]
                val left = marginX + col * cardW
                val top = marginY + row * cardH
                val right = left + cardW - 2f
                val bottom = top + cardH - 2f
                val cardRect = RectF(left, top, right, bottom)

                // 1. Draw card background
                canvas.drawRoundRect(cardRect, 4f, 4f, paintBg)

                if (customBgBitmap != null && config.templateType == CardTemplateType.CUSTOM_IMAGE) {
                    val srcRect = Rect(0, 0, customBgBitmap.width, customBgBitmap.height)
                    canvas.drawBitmap(customBgBitmap, srcRect, cardRect, null)
                } else {
                    // Template decorative elements
                    val badgeWidth = cardW * 0.20f
                    val badgeRect = RectF(right - badgeWidth, top, right, bottom)
                    canvas.drawRoundRect(badgeRect, 2f, 2f, paintPriceBadgeBg)

                    // Draw Price in badge
                    val priceNum = card.price.replace(Regex("[^0-9]"), "").ifEmpty { "500" }
                    canvas.drawText(priceNum, badgeRect.centerX(), top + cardH * 0.45f, paintPriceText)
                    canvas.drawText("ريال", badgeRect.centerX(), top + cardH * 0.75f, paintPriceSub)

                    // Draw Left decorative rocket or wifi band
                    val leftBarRect = RectF(left, top, left + 4f, bottom)
                    canvas.drawRect(leftBarRect, paintPriceBadgeBg)
                }

                // 2. Draw border
                canvas.drawRoundRect(cardRect, 4f, 4f, paintBorder)

                // 3. Card Texts
                val contentCenterX = left + (cardW * 0.40f) + (config.offsetXPercent * cardW * 0.2f)

                // Header line: Time / Quota
                val timeLimit = if (card.limitUptime.isNotBlank()) card.limitUptime else "24 ساعة"
                val quotaMb = if (card.limitBytesTotal > 0) "${card.limitBytesTotal / (1024 * 1024)} ميجا" else "1024 ميجا"
                val headerStr = "$timeLimit . $quotaMb"
                canvas.drawText(headerStr, contentCenterX, top + cardH * 0.24f, paintTextHeader)

                // Code Login Label & Value
                val codeY = top + cardH * 0.58f + (config.offsetYPercent * cardH * 0.2f)
                canvas.drawText("رمز الدخول: ${card.name}", contentCenterX, codeY, paintCodeValue)

                // Dotted separator
                val linePaint = Paint().apply {
                    color = Color.rgb(210, 220, 230)
                    strokeWidth = 1f
                }
                canvas.drawLine(left + 10f, top + cardH * 0.68f, right - (cardW * 0.25f), top + cardH * 0.68f, linePaint)

                // Footer line: Network name & contact
                val footerStr = "${config.networkName} | ت: ${config.contactPhone}"
                canvas.drawText(footerStr, contentCenterX, top + cardH * 0.88f, paintFooter)
            }
        }
    }

    /**
     * Export all pages to a multi-page PDF document
     */
    fun exportToPdf(
        context: Context,
        cards: List<HotspotCard>,
        config: CardDesignConfig,
        onComplete: (File?) -> Unit
    ) {
        try {
            val customBitmap = loadBitmapFromUri(context, config.customImageUri)
            val pdfDocument = PdfDocument()

            val pageWidth = 1240 // A4 at ~150 DPI for crisp printing
            val pageHeight = 1754

            val totalCards = cards.size
            val cardsPerPage = config.cardsPerPage
            val totalPages = (totalCards + cardsPerPage - 1) / cardsPerPage

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

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val pdfFile = File(exportDir, "vouchers_sheet_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            onComplete(pdfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }

    /**
     * Export a single page as a high-resolution PNG image
     */
    fun exportPageToImage(
        context: Context,
        cards: List<HotspotCard>,
        pageIndex: Int,
        config: CardDesignConfig,
        onComplete: (File?) -> Unit
    ) {
        try {
            val customBitmap = loadBitmapFromUri(context, config.customImageUri)
            val pageWidth = 1240
            val pageHeight = 1754

            val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawCardsPage(
                canvas = canvas,
                pageIndex = pageIndex,
                pageCards = cards,
                config = config,
                customBgBitmap = customBitmap,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val imgFile = File(exportDir, "vouchers_page_${pageIndex + 1}_${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(imgFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            onComplete(imgFile)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
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

    /**
     * Native Android Print Adapter
     */
    fun printVouchers(
        context: Context,
        cards: List<HotspotCard>,
        config: CardDesignConfig
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "خدمة الطباعة غير متوفرة على هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }

        val customBitmap = loadBitmapFromUri(context, config.customImageUri)

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

                val cardsPerPage = config.cardsPerPage
                totalPages = (cards.size + cardsPerPage - 1) / cardsPerPage

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
                val pdfDocument = PdfDocument()
                val pageWidth = 1240
                val pageHeight = 1754

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

                try {
                    val outputStream = FileOutputStream(destination?.fileDescriptor)
                    pdfDocument.writeTo(outputStream)
                    outputStream.close()
                    pdfDocument.close()
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print("طباعة كروت شبكة ميكروتك", printAdapter, PrintAttributes.Builder().build())
    }
}
