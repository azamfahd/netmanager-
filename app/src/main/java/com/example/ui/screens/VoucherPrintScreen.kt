package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.model.HotspotCard
import com.example.data.model.PrintableBatchOption
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.RouterBlueDark
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.util.CardDesignConfig
import com.example.ui.util.CardTemplateType
import com.example.ui.util.VoucherPrintUtils
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherPrintScreen(
    cards: List<HotspotCard>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    batchOptions: List<PrintableBatchOption> = emptyList(),
    activeBatchKey: String? = null,
    onSelectBatchCards: ((List<HotspotCard>, String?) -> Unit)? = null,
    onMarkPrinted: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Design Customization State matching the uploaded image
    var offsetX by remember { mutableFloatStateOf(0f) } // horizontal offset in px/dp
    var offsetY by remember { mutableFloatStateOf(0f) } // vertical offset in px/dp
    var fontSizeSp by remember { mutableFloatStateOf(15f) } // font size in px/sp
    var cardHeightMm by remember { mutableFloatStateOf(15f) } // height mm
    var cardWidthMm by remember { mutableFloatStateOf(50f) } // width mm

    var columns by remember { mutableIntStateOf(4) } // عدد الكروت بالعرض
    var rows by remember { mutableIntStateOf(15) } // عدد الكروت بالطول

    var templateType by remember { mutableStateOf(CardTemplateType.SCOUD_BLUE) }
    var customImageUri by remember { mutableStateOf<Uri?>(null) }
    var networkName by remember { mutableStateOf("سكود نت") }
    var contactPhone by remember { mutableStateOf("777000111") }
    var customCodeColorArgb by remember { mutableIntStateOf(android.graphics.Color.rgb(13, 27, 42)) }

    // Pagination
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val cardsPerPage = max(1, columns * rows)
    val totalPages = max(1, (cards.size + cardsPerPage - 1) / cardsPerPage)

    // Ensure currentPageIndex is valid
    val safePageIndex = min(currentPageIndex, totalPages - 1)

    // Photo picker for custom card template
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customImageUri = uri
            templateType = CardTemplateType.CUSTOM_IMAGE
            // Delete previous template styling, offsets & fields so only the card code remains on the custom image
            offsetX = 0f
            offsetY = 0f
            networkName = ""
            contactPhone = ""
            Toast.makeText(
                context,
                "تم اعتماد الصورة وحذف التصميم والمعلومات السابقة — سيُطبع رقم الكرت فقط",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val switchTemplateType: (CardTemplateType) -> Unit = { newType ->
        templateType = newType
        if (newType != CardTemplateType.CUSTOM_IMAGE) {
            if (networkName.isBlank()) networkName = "سكود نت"
            if (contactPhone.isBlank()) contactPhone = "777000111"
        }
    }

    val clearCustomImage: () -> Unit = {
        customImageUri = null
        templateType = CardTemplateType.SCOUD_BLUE
        offsetX = 0f
        offsetY = 0f
        if (networkName.isBlank()) networkName = "سكود نت"
        if (contactPhone.isBlank()) contactPhone = "777000111"
    }

    val currentConfig = CardDesignConfig(
        templateType = templateType,
        customImageUri = customImageUri,
        networkName = networkName,
        contactPhone = contactPhone,
        offsetXPercent = offsetX / 100f,
        offsetYPercent = offsetY / 50f,
        fontSizeSp = fontSizeSp,
        columns = columns,
        rows = rows,
        cardWidthMm = cardWidthMm,
        cardHeightMm = cardHeightMm,
        customCodeColorArgb = customCodeColorArgb
    )

    // Get cards for current page
    val startIndex = safePageIndex * cardsPerPage
    val pageCards = cards.drop(startIndex).take(cardsPerPage)

    // Collapsible customization panel state for mobile
    var showControlPanel by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "معاينة وتصميم طباعة الكروت",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${cards.size} كرت | صفحة ${safePageIndex + 1} من $totalPages (${cardsPerPage} كرت/صفحة)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع"
                        )
                    }
                },
                actions = {
                    // Export PDF Button
                    IconButton(
                        onClick = {
                            onMarkPrinted?.invoke()
                            Toast.makeText(context, "جاري إنشاء ملف PDF...", Toast.LENGTH_SHORT).show()
                            VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                if (file != null) {
                                    VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                } else {
                                    Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export_pdf_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "تصدير كـ PDF",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    // Export Image Button
                    IconButton(
                        onClick = {
                            onMarkPrinted?.invoke()
                            Toast.makeText(context, "جاري تصدير الصفحة الحالية كصورة...", Toast.LENGTH_SHORT).show()
                            VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                if (file != null) {
                                    VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت")
                                } else {
                                    Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export_img_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "تصدير كصورة",
                            tint = RouterBlue
                        )
                    }

                    // Customization Toggle Button in TopAppBar (Compact)
                    Button(
                        onClick = { showControlPanel = !showControlPanel },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00838F)
                        ),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("toggle_customization_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("تخصيص", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Print Button (Compact)
                    Button(
                        onClick = {
                            onMarkPrinted?.invoke()
                            VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RouterBlue
                        ),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("direct_print_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("طباعة", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Switcher for Newly Added / Saved Batches
            if (batchOptions.isNotEmpty() && onSelectBatchCards != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(batchOptions, key = { it.batchKey }) { batch ->
                            val isSelected = activeBatchKey == batch.batchKey
                            val countInBatch = batch.readyCards.size.takeIf { it > 0 } ?: batch.allCardsInBatch.size
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val targetCards = batch.readyCards.ifEmpty { batch.allCardsInBatch }
                                    onSelectBatchCards(targetCards, batch.batchKey)
                                    currentPageIndex = 0
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (batch.isNewlyAdded) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isSelected) Color.White.copy(alpha = 0.25f) else StatusActiveGreen.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "جديد",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else StatusActiveGreen,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${batch.title} ($countInBatch)",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RouterBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val isWideScreen = maxWidth >= 800.dp

            if (isWideScreen) {
                // Wide Screen: Dual Pane (Left Sheet Preview, Right Control Panel matching image)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left 74%: Sheet Preview & Pagination
                    Box(
                        modifier = Modifier
                            .weight(0.74f)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F4F8))
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        SheetPreviewArea(
                            pageCards = pageCards,
                            columns = columns,
                            rows = rows,
                            cardHeightMm = cardHeightMm,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            fontSizeSp = fontSizeSp,
                            templateType = templateType,
                            customImageUri = customImageUri,
                            customCodeColorArgb = customCodeColorArgb,
                            networkName = networkName,
                            contactPhone = contactPhone,
                            safePageIndex = safePageIndex,
                            totalPages = totalPages,
                            onPrevPage = { if (safePageIndex > 0) currentPageIndex-- },
                            onNextPage = { if (safePageIndex < totalPages - 1) currentPageIndex++ },
                            context = context
                        )
                    }

                    // Right 26%: Compact Dark Blue Control Panel
                    Box(
                        modifier = Modifier
                            .weight(0.26f)
                            .fillMaxHeight()
                            .background(Color(0xFF0D253A))
                    ) {
                        CustomizationControlPanel(
                            offsetX = offsetX,
                            onOffsetXChange = { offsetX = it },
                            offsetY = offsetY,
                            onOffsetYChange = { offsetY = it },
                            fontSizeSp = fontSizeSp,
                            onFontSizeChange = { fontSizeSp = it },
                            cardHeightMm = cardHeightMm,
                            onCardHeightMmChange = { cardHeightMm = it },
                            cardWidthMm = cardWidthMm,
                            onCardWidthMmChange = { cardWidthMm = it },
                            columns = columns,
                            onColumnsChange = { columns = it },
                            rows = rows,
                            onRowsChange = { rows = it },
                            cardsCount = cards.size,
                            cardsPerPage = cardsPerPage,
                            totalPages = totalPages,
                            templateType = templateType,
                            onTemplateTypeChange = switchTemplateType,
                            customImageUri = customImageUri,
                            customCodeColorArgb = customCodeColorArgb,
                            onCustomCodeColorChange = { customCodeColorArgb = it },
                            onSelectImageClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onClearCustomImage = clearCustomImage,
                            networkName = networkName,
                            onNetworkNameChange = { networkName = it },
                            contactPhone = contactPhone,
                            onContactPhoneChange = { contactPhone = it },
                            onPrintClick = {
                                onMarkPrinted?.invoke()
                                VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                            },
                            onExportPdfClick = {
                                onMarkPrinted?.invoke()
                                Toast.makeText(context, "جاري إنشاء ملف PDF...", Toast.LENGTH_SHORT).show()
                                VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                    if (file != null) {
                                        VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                    } else {
                                        Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onExportImageClick = {
                                onMarkPrinted?.invoke()
                                Toast.makeText(context, "جاري تصدير الصورة...", Toast.LENGTH_SHORT).show()
                                VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                    if (file != null) {
                                        VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت")
                                    } else {
                                        Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Mobile Screen: Sheet Preview background with sliding collapsible side drawer overlay
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F4F8))
                            .padding(8.dp)
                    ) {
                        SheetPreviewArea(
                            pageCards = pageCards,
                            columns = columns,
                            rows = rows,
                            cardHeightMm = cardHeightMm,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            fontSizeSp = fontSizeSp,
                            templateType = templateType,
                            customImageUri = customImageUri,
                            customCodeColorArgb = customCodeColorArgb,
                            networkName = networkName,
                            contactPhone = contactPhone,
                            safePageIndex = safePageIndex,
                            totalPages = totalPages,
                            onPrevPage = { if (safePageIndex > 0) currentPageIndex-- },
                            onNextPage = { if (safePageIndex < totalPages - 1) currentPageIndex++ },
                            context = context
                        )
                    }

                    // Backdrop Scrim when control panel is open
                    if (showControlPanel) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { showControlPanel = false }
                        )
                    }

                    // Sliding Side Drawer for Customization Panel with swipe-to-dismiss gesture
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControlPanel,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }),
                        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it })
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(265.dp)
                                .background(Color(0xFF0D253A))
                                .shadow(8.dp)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        // Swipe right to close panel
                                        if (dragAmount > 20f) {
                                            showControlPanel = false
                                        }
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF15334E))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "أدوات التخصيص والتصميم",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    IconButton(onClick = { showControlPanel = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    CustomizationControlPanel(
                                        offsetX = offsetX,
                                        onOffsetXChange = { offsetX = it },
                                        offsetY = offsetY,
                                        onOffsetYChange = { offsetY = it },
                                        fontSizeSp = fontSizeSp,
                                        onFontSizeChange = { fontSizeSp = it },
                                        cardHeightMm = cardHeightMm,
                                        onCardHeightMmChange = { cardHeightMm = it },
                                        cardWidthMm = cardWidthMm,
                                        onCardWidthMmChange = { cardWidthMm = it },
                                        columns = columns,
                                        onColumnsChange = { columns = it },
                                        rows = rows,
                                        onRowsChange = { rows = it },
                                        cardsCount = cards.size,
                                        cardsPerPage = cardsPerPage,
                                        totalPages = totalPages,
                                        templateType = templateType,
                                        onTemplateTypeChange = switchTemplateType,
                                        customImageUri = customImageUri,
                                        customCodeColorArgb = customCodeColorArgb,
                                        onCustomCodeColorChange = { customCodeColorArgb = it },
                                        onSelectImageClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onClearCustomImage = clearCustomImage,
                                        networkName = networkName,
                                        onNetworkNameChange = { networkName = it },
                                        contactPhone = contactPhone,
                                        onContactPhoneChange = { contactPhone = it },
                                        onPrintClick = {
                                            onMarkPrinted?.invoke()
                                            VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                                        },
                                        onExportPdfClick = {
                                            onMarkPrinted?.invoke()
                                            Toast.makeText(context, "جاري إنشاء ملف PDF...", Toast.LENGTH_SHORT).show()
                                            VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                                if (file != null) {
                                                    VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                                } else {
                                                    Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onExportImageClick = {
                                            onMarkPrinted?.invoke()
                                            Toast.makeText(context, "جاري تصدير الصورة...", Toast.LENGTH_SHORT).show()
                                            VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                                if (file != null) {
                                                    VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت")
                                                } else {
                                                    Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * The Printable Sheet Preview Component (Grid of vouchers on A4 white sheet)
 */
@Composable
fun SheetPreviewArea(
    pageCards: List<HotspotCard>,
    columns: Int,
    rows: Int,
    cardHeightMm: Float,
    offsetX: Float,
    offsetY: Float,
    fontSizeSp: Float,
    templateType: CardTemplateType,
    customImageUri: Uri?,
    customCodeColorArgb: Int,
    networkName: String,
    contactPhone: String,
    safePageIndex: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    context: Context
) {
    val customBitmap = remember(customImageUri?.toString()) {
        VoucherPrintUtils.loadBitmapFromUri(context, customImageUri)
    }
    val cellHeightDp = (42f * (cardHeightMm / 15f)).coerceIn(30f, 88f).dp

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pagination & Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onPrevPage,
                enabled = safePageIndex > 0,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "السابق")
                Spacer(modifier = Modifier.width(4.dp))
                Text("الصفحة السابقة")
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = "صفحة ${safePageIndex + 1} من $totalPages",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = RouterBlueDark
                )
            }

            OutlinedButton(
                onClick = onNextPage,
                enabled = safePageIndex < totalPages - 1,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("الصفحة التالية")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "التالي")
            }
        }

        // A4 Paper Container with White Background & Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(6.dp, RoundedCornerShape(6.dp))
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFFD0DCE5), RoundedCornerShape(6.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (c in 0 until columns) {
                            val cardIndex = r * columns + c
                            val card = pageCards.getOrNull(cardIndex)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeightDp)
                                    .border(
                                        width = if (templateType == CardTemplateType.CUSTOM_IMAGE) 0.5.dp else 0.8.dp,
                                        color = if (templateType == CardTemplateType.CUSTOM_IMAGE) Color(0xFFD8E0E8) else Color(0xFFB0C4DE),
                                        shape = RoundedCornerShape(if (templateType == CardTemplateType.CUSTOM_IMAGE) 1.dp else 3.dp)
                                    )
                                    .background(Color.White)
                                    .padding(if (templateType == CardTemplateType.CUSTOM_IMAGE) 0.dp else 1.dp)
                            ) {
                                if (card != null) {
                                    SingleCardRender(
                                        card = card,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        fontSizeSp = fontSizeSp,
                                        templateType = templateType,
                                        customBitmap = customBitmap,
                                        customCodeColorArgb = customCodeColorArgb,
                                        networkName = networkName,
                                        contactPhone = contactPhone
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Voucher Card Rendering inside the Preview Grid
 */
@Composable
fun SingleCardRender(
    card: HotspotCard,
    offsetX: Float,
    offsetY: Float,
    fontSizeSp: Float,
    templateType: CardTemplateType,
    customBitmap: Bitmap?,
    customCodeColorArgb: Int,
    networkName: String,
    contactPhone: String
) {
    val priceNum = card.price.replace(Regex("[^0-9]"), "").ifEmpty { "500" }
    val timeLimit = if (card.limitUptime.isNotBlank()) card.limitUptime else "20 ساعة"
    val quotaMb = if (card.limitBytesTotal > 0) "${card.limitBytesTotal / (1024 * 1024)} ميجا" else "1024 ميجا"

    val badgeColor = when (templateType) {
        CardTemplateType.VIP_GOLD -> Color(0xFFD4A017)
        CardTemplateType.EMERALD_GREEN -> Color(0xFF00897B)
        else -> Color(0xFF0277BD)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Custom Image Mode: strictly clear previous design & info, render ONLY image + card code
        if (templateType == CardTemplateType.CUSTOM_IMAGE) {
            if (customBitmap != null && !customBitmap.isRecycled) {
                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            val offsetXPx = ((offsetX / 100f) * constraints.maxWidth * 0.45f).roundToInt()
            val offsetYPx = ((offsetY / 50f) * constraints.maxHeight * 0.45f).roundToInt()
            val scaledCodeFontSp = (maxHeight.value * 0.22f * (fontSizeSp / 14f)).coerceAtLeast(5f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = offsetXPx, y = offsetYPx) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = card.name,
                    fontSize = scaledCodeFontSp.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(customCodeColorArgb),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            // Default Scoud Net style template as shown in user's image
            Row(modifier = Modifier.fillMaxSize()) {
                // Left decorative rocket/wifi bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(badgeColor)
                )

                // Middle Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header text: "20 ساعة . 1024 ميجا . 20 يوم"
                    Text(
                        text = "$timeLimit . $quotaMb",
                        fontSize = 5.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E1E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Login Code with custom offsets & font size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                IntOffset(
                                    x = (offsetX * 0.4f).roundToInt(),
                                    y = (offsetY * 0.2f).roundToInt()
                                )
                            }
                    ) {
                        Text(
                            text = "رمز الدخول: ",
                            fontSize = 5.sp,
                            color = Color(0xFF0277BD),
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = card.name,
                            fontSize = (fontSizeSp * 0.5f).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF0D1B2A)
                        )
                    }

                    // Footer text: Network name & phone
                    Text(
                        text = "$networkName  📞$contactPhone",
                        fontSize = 4.5.sp,
                        color = Color(0xFF555555),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right Price Badge (e.g., 500 ريال)
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = priceNum,
                            fontSize = 6.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ريال",
                            fontSize = 4.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Dark Blue Customization Control Panel (Identical in structure & styling to the user's uploaded image!)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationControlPanel(
    offsetX: Float,
    onOffsetXChange: (Float) -> Unit,
    offsetY: Float,
    onOffsetYChange: (Float) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    cardHeightMm: Float,
    onCardHeightMmChange: (Float) -> Unit,
    cardWidthMm: Float,
    onCardWidthMmChange: (Float) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    rows: Int,
    onRowsChange: (Int) -> Unit,
    cardsCount: Int,
    cardsPerPage: Int,
    totalPages: Int,
    templateType: CardTemplateType,
    onTemplateTypeChange: (CardTemplateType) -> Unit,
    customImageUri: Uri?,
    customCodeColorArgb: Int,
    onCustomCodeColorChange: (Int) -> Unit,
    onSelectImageClick: () -> Unit,
    onClearCustomImage: () -> Unit,
    networkName: String,
    onNetworkNameChange: (String) -> Unit,
    contactPhone: String,
    onContactPhoneChange: (String) -> Unit,
    onPrintClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onExportImageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: تغيير حجم الخط و موقع رقم الكرت (Font & Code Position)
        Text(
            text = "تغيير حجم الخط و موقع رقم الكرت",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // افقياً Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "افقيا :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                Text(text = "${offsetX.roundToInt()}px", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = offsetX,
                onValueChange = onOffsetXChange,
                valueRange = -50f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF0288D1),
                    inactiveTrackColor = Color(0xFF1E3A5F)
                )
            )
        }

        // عمودياً Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "عموديا :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                Text(text = "${offsetY.roundToInt()}px", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = offsetY,
                onValueChange = onOffsetYChange,
                valueRange = -30f..50f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF0288D1),
                    inactiveTrackColor = Color(0xFF1E3A5F)
                )
            )
        }

        // حجم الخط Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "حجم الخط :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                Text(text = "${fontSizeSp.roundToInt()}px", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = fontSizeSp,
                onValueChange = onFontSizeChange,
                valueRange = 10f..26f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF0288D1),
                    inactiveTrackColor = Color(0xFF1E3A5F)
                )
            )
        }

        HorizontalDivider(color = Color(0xFF1E3A5F), thickness = 1.dp)

        // Section 2: تغيير حجم الكرت (Card Dimensions)
        Text(
            text = "تغيير حجم الكرت",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // الطول Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "الطول :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                Text(text = "${cardHeightMm.roundToInt()}mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = cardHeightMm,
                onValueChange = onCardHeightMmChange,
                valueRange = 10f..35f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF0288D1),
                    inactiveTrackColor = Color(0xFF1E3A5F)
                )
            )
        }

        // العرض Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "العرض :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                Text(text = "${cardWidthMm.roundToInt()}mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = cardWidthMm,
                onValueChange = onCardWidthMmChange,
                valueRange = 30f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF0288D1),
                    inactiveTrackColor = Color(0xFF1E3A5F)
                )
            )
        }

        HorizontalDivider(color = Color(0xFF1E3A5F), thickness = 1.dp)

        // Section 3: أو تحديد عدد الكروت (Grid Count / Sheet Layout)
        Text(
            text = "او تحديد عدد الكروت",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "عدد الكروت بالعرض :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (columns > 2) onColumnsChange(columns - 1) }) {
                    Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "$columns",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { if (columns < 6) onColumnsChange(columns + 1) }) {
                    Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "عدد الكروت بالطول :", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (rows > 5) onRowsChange(rows - 1) }) {
                    Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "$rows",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { if (rows < 25) onRowsChange(rows + 1) }) {
                    Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Summary Text matching image
        Text(
            text = "عدد الكروت بالصفحة الواحدة هو : $cardsPerPage كرت تقريباً",
            color = Color(0xFF81D4FA),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "و عدد الصفحات هو : $totalPages صفحة",
            color = Color(0xFF81D4FA),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        HorizontalDivider(color = Color(0xFF1E3A5F), thickness = 1.dp)

        // Section 4: تصميم الكرت (Templates & Photo Picker)
        Text(
            text = "تصميم الكرت",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Photo Picker Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSelectImageClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("pick_card_template_btn")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (customImageUri != null) "تغيير الصورة" else "اختيار صورة", fontWeight = FontWeight.Bold)
            }

            Text(
                text = if (customImageUri != null) "صورة مخصصة نشطة" else "لم يتم اختيار صورة",
                color = if (templateType == CardTemplateType.CUSTOM_IMAGE) Color(0xFF81D4FA) else Color(0xFFB0BEC5),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            if (customImageUri != null) {
                IconButton(onClick = onClearCustomImage) {
                    Icon(Icons.Default.Close, contentDescription = "مسح الصورة", tint = Color(0xFFFF8A80))
                }
            }
        }

        // Predefined Templates
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                CardTemplateType.SCOUD_BLUE to "سكود نت (الأزرق)",
                CardTemplateType.VIP_GOLD to "VIP الذهبي",
                CardTemplateType.EMERALD_GREEN to "تيربو الأخضر"
            ).forEach { (tType, label) ->
                FilterChip(
                    selected = templateType == tType,
                    onClick = { onTemplateTypeChange(tType) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0288D1),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF15334E),
                        labelColor = Color(0xFFECEFF1)
                    )
                )
            }
        }

        if (templateType == CardTemplateType.CUSTOM_IMAGE) {
            // Custom Image Mode Info & Code Color Selector (old template details are stripped)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF15334E),
                border = BorderStroke(1.dp, Color(0xFF0288D1).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "وضع الصورة المخصصة: تم حذف التصميم والمعلومات السابقة، ويُطبع رقم الكرت فقط.",
                        color = Color(0xFF81D4FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "لون رقم الكرت :",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            android.graphics.Color.rgb(13, 27, 42) to "كحلي",
                            android.graphics.Color.BLACK to "أسود",
                            android.graphics.Color.WHITE to "أبيض",
                            android.graphics.Color.rgb(198, 40, 40) to "أحمر",
                            android.graphics.Color.rgb(245, 158, 11) to "ذهبي"
                        ).forEach { (colorInt, label) ->
                            val isSelected = customCodeColorArgb == colorInt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCustomCodeColorChange(colorInt) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = label,
                                            tint = if (colorInt == android.graphics.Color.WHITE || colorInt == android.graphics.Color.rgb(245, 158, 11)) Color.Black else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Network Name & Contact Text Fields (Built-in templates only)
            OutlinedTextField(
                value = networkName,
                onValueChange = onNetworkNameChange,
                label = { Text("اسم الشبكة في الكرت", color = Color(0xFFB0BEC5), fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color(0xFF2E5370)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = contactPhone,
                onValueChange = onContactPhoneChange,
                label = { Text("رقم التواصل في الكرت", color = Color(0xFFB0BEC5), fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color(0xFF2E5370)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Section 5: Big Action Buttons
        Button(
            onClick = onPrintClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("panel_print_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0288D1)
            )
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("طباعة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExportPdfClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFE53935)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("panel_export_pdf_btn")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("تصدير PDF", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onExportImageClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFF0288D1)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("panel_export_image_btn")
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF81D4FA), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("تصدير كصورة", fontSize = 12.sp)
            }
        }
    }
}
