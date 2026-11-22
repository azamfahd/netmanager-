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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
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
import com.example.data.util.SerialCorner
import com.example.data.util.VoucherSerialEngine
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.RouterBlueDark
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.util.CardDesignConfig
import com.example.ui.util.CardTemplateType
import com.example.ui.util.VoucherPrintUtils
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onMarkPrinted: (() -> Unit)? = null,
    serialMap: Map<String, String> = emptyMap(),
    stockActivationState: MainViewModel.StockActivationUiState = MainViewModel.StockActivationUiState.Idle,
    onActivatePendingCards: ((() -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printDesignPrefs = remember(context) {
        context.getSharedPreferences("voucher_print_design_prefs", Context.MODE_PRIVATE)
    }

    // Design Customization State matching the uploaded image
    var offsetX by remember { mutableFloatStateOf(0f) } // horizontal offset in px/dp
    var offsetY by remember { mutableFloatStateOf(0f) } // vertical offset in px/dp
    var fontSizeSp by remember { mutableFloatStateOf(15f) } // font size in px/sp
    var cardHeightMm by remember { mutableFloatStateOf(18f) } // height mm
    var cardWidthMm by remember { mutableFloatStateOf(50f) } // width mm

    var columns by remember { mutableIntStateOf(4) } // عدد الكروت بالعرض
    var rows by remember { mutableIntStateOf(15) } // عدد الكروت بالطول

    var templateType by remember { mutableStateOf(CardTemplateType.SCOUD_BLUE) }
    var customImageUri by remember { mutableStateOf<Uri?>(null) }
    var networkName by remember { mutableStateOf("سكود نت") }
    var contactPhone by remember { mutableStateOf("777000111") }
    var customCodeColorArgb by remember { mutableIntStateOf(android.graphics.Color.rgb(13, 27, 42)) }

    // Serial Number Print Customization State (persisted across sessions)
    var showSerialNumber by remember { mutableStateOf(printDesignPrefs.getBoolean("show_serial", false)) }
    var serialPrefix by remember { mutableStateOf(printDesignPrefs.getString("serial_prefix", "#") ?: "#") }
    var serialCorner by remember {
        val savedName = printDesignPrefs.getString("serial_corner", SerialCorner.TOP_END.name)
        val parsed = SerialCorner.entries.firstOrNull { it.name == savedName } ?: SerialCorner.TOP_END
        mutableStateOf(parsed)
    }
    var serialOffsetX by remember { mutableFloatStateOf(printDesignPrefs.getFloat("serial_offset_x", 0f)) }
    var serialOffsetY by remember { mutableFloatStateOf(printDesignPrefs.getFloat("serial_offset_y", 0f)) }
    var serialFontSizeSp by remember { mutableFloatStateOf(printDesignPrefs.getFloat("serial_font_sp", 8f)) }
    var autoActivateOnPrint by remember { mutableStateOf(true) }

    LaunchedEffect(showSerialNumber, serialPrefix, serialCorner, serialOffsetX, serialOffsetY, serialFontSizeSp) {
        printDesignPrefs.edit()
            .putBoolean("show_serial", showSerialNumber)
            .putString("serial_prefix", serialPrefix)
            .putString("serial_corner", serialCorner.name)
            .putFloat("serial_offset_x", serialOffsetX)
            .putFloat("serial_offset_y", serialOffsetY)
            .putFloat("serial_font_sp", serialFontSizeSp)
            .apply()
    }

    val effectiveSerialMap = remember(cards, serialMap) {
        if (serialMap.isNotEmpty()) serialMap else VoucherSerialEngine.buildSerialMap(cards)
    }

    val pendingDisabledCards = remember(cards) { cards.filter { it.disabled } }

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
        customCodeColorArgb = customCodeColorArgb,
        showSerialNumber = showSerialNumber,
        serialPrefix = serialPrefix,
        serialCorner = serialCorner,
        serialOffsetXPercent = serialOffsetX,
        serialOffsetYPercent = serialOffsetY,
        serialFontSizeSp = serialFontSizeSp,
        serialMap = effectiveSerialMap
    )

    val executePrintOrExport: (() -> Unit) -> Unit = { outputAction ->
        if (pendingDisabledCards.isNotEmpty() && autoActivateOnPrint && onActivatePendingCards != null) {
            onActivatePendingCards {
                onMarkPrinted?.invoke()
                outputAction()
            }
        } else {
            onMarkPrinted?.invoke()
            outputAction()
        }
    }

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
                    var showHdPreview by remember { mutableStateOf(false) }

                    // HD Preview Button
                    IconButton(
                        onClick = { showHdPreview = true },
                        modifier = Modifier.testTag("hd_preview_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "معاينة الجودة العالية HD",
                            tint = Color(0xFFFFA000)
                        )
                    }

                    if (showHdPreview) {
                        HdPreviewDialog(
                            cards = cards,
                            pageIndex = safePageIndex,
                            config = currentConfig,
                            onDismiss = { showHdPreview = false },
                            onExportPdf = {
                                executePrintOrExport {
                                    Toast.makeText(context, "جاري إنشاء ملف PDF بدقة عالية...", Toast.LENGTH_SHORT).show()
                                    VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                        if (file != null) {
                                            VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                        } else {
                                            Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onExportImage = {
                                executePrintOrExport {
                                    Toast.makeText(context, "جاري تصدير الصورة بجودة HD...", Toast.LENGTH_SHORT).show()
                                    VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                        if (file != null) {
                                            VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت عالية الدقة")
                                        } else {
                                            Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onPrint = {
                                executePrintOrExport {
                                    VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                                }
                            }
                        )
                    }

                    // Export PDF Button
                    IconButton(
                        onClick = {
                            executePrintOrExport {
                                Toast.makeText(context, "جاري إنشاء ملف PDF...", Toast.LENGTH_SHORT).show()
                                VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                    if (file != null) {
                                        VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                    } else {
                                        Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                    }
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
                            executePrintOrExport {
                                Toast.makeText(context, "جاري تصدير الصفحة الحالية كصورة...", Toast.LENGTH_SHORT).show()
                                VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                    if (file != null) {
                                        VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت")
                                    } else {
                                        Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                    }
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
                            executePrintOrExport {
                                VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                            }
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

            // Stock Pending Activation / Serial Activation Progress Banner
            if (pendingDisabledCards.isNotEmpty() || stockActivationState !is MainViewModel.StockActivationUiState.Idle) {
                val firstSn = cards.firstOrNull()?.let { effectiveSerialMap[it.name] ?: effectiveSerialMap[it.id] }
                val lastSn = cards.lastOrNull()?.let { effectiveSerialMap[it.name] ?: effectiveSerialMap[it.id] }
                val snRangeText = if (!firstSn.isNullOrBlank() && !lastSn.isNullOrBlank()) " (#$firstSn – #$lastSn)" else ""

                Surface(
                    color = when (stockActivationState) {
                        is MainViewModel.StockActivationUiState.Error -> MaterialTheme.colorScheme.errorContainer
                        is MainViewModel.StockActivationUiState.Activating -> Color(0xFFE3F2FD)
                        else -> Color(0xFFFFF8E1)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        when (stockActivationState) {
                            is MainViewModel.StockActivationUiState.Activating -> {
                                val pct = if (stockActivationState.total > 0) {
                                    stockActivationState.completed.toFloat() / stockActivationState.total.toFloat()
                                } else 0f
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "جاري تفعيل ${stockActivationState.completed} من ${stockActivationState.total} كرت (${stockActivationState.currentCardName})...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RouterBlueDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                                    color = RouterBlue
                                )
                            }
                            is MainViewModel.StockActivationUiState.Error -> {
                                Text(
                                    text = "تنبيه التفعيل: ${stockActivationState.message}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${pendingDisabledCards.size} كرت مخزون غير مفعّل$snRangeText",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { autoActivateOnPrint = !autoActivateOnPrint }
                                        ) {
                                            Text(
                                                text = if (autoActivateOnPrint) "تفعيل تلقائي لحظة الطباعة: مفعّل" else "تفعيل تلقائي لحظة الطباعة: متوقف",
                                                fontSize = 10.sp,
                                                color = Color(0xFF5D4037)
                                            )
                                        }
                                    }
                                    if (onActivatePendingCards != null) {
                                        Button(
                                            onClick = { onActivatePendingCards {} },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("activate_pending_stock_btn")
                                        ) {
                                            Text("تفعيل الآن (${pendingDisabledCards.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
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
                            cardWidthMm = cardWidthMm,
                            cardHeightMm = cardHeightMm,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            fontSizeSp = fontSizeSp,
                            templateType = templateType,
                            customImageUri = customImageUri,
                            customCodeColorArgb = customCodeColorArgb,
                            networkName = networkName,
                            contactPhone = contactPhone,
                            showSerialNumber = showSerialNumber,
                            serialPrefix = serialPrefix,
                            serialCorner = serialCorner,
                            serialOffsetX = serialOffsetX,
                            serialOffsetY = serialOffsetY,
                            serialFontSizeSp = serialFontSizeSp,
                            serialMap = effectiveSerialMap,
                            startIndex = startIndex,
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
                            showSerialNumber = showSerialNumber,
                            onShowSerialNumberChange = { showSerialNumber = it },
                            serialPrefix = serialPrefix,
                            onSerialPrefixChange = { serialPrefix = it },
                            serialCorner = serialCorner,
                            onSerialCornerChange = { serialCorner = it },
                            serialOffsetX = serialOffsetX,
                            onSerialOffsetXChange = { serialOffsetX = it },
                            serialOffsetY = serialOffsetY,
                            onSerialOffsetYChange = { serialOffsetY = it },
                            serialFontSizeSp = serialFontSizeSp,
                            onSerialFontSizeChange = { serialFontSizeSp = it },
                            onPrintClick = {
                                executePrintOrExport {
                                    VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                                }
                            },
                            onExportPdfClick = {
                                executePrintOrExport {
                                    Toast.makeText(context, "جاري إنشاء ملف PDF...", Toast.LENGTH_SHORT).show()
                                    VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                        if (file != null) {
                                            VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                        } else {
                                            Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onExportImageClick = {
                                executePrintOrExport {
                                    Toast.makeText(context, "جاري تصدير الصورة...", Toast.LENGTH_SHORT).show()
                                    VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                        if (file != null) {
                                            VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت")
                                        } else {
                                            Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                        }
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
                            cardWidthMm = cardWidthMm,
                            cardHeightMm = cardHeightMm,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            fontSizeSp = fontSizeSp,
                            templateType = templateType,
                            customImageUri = customImageUri,
                            customCodeColorArgb = customCodeColorArgb,
                            networkName = networkName,
                            contactPhone = contactPhone,
                            showSerialNumber = showSerialNumber,
                            serialPrefix = serialPrefix,
                            serialCorner = serialCorner,
                            serialOffsetX = serialOffsetX,
                            serialOffsetY = serialOffsetY,
                            serialFontSizeSp = serialFontSizeSp,
                            serialMap = effectiveSerialMap,
                            startIndex = startIndex,
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
                                        showSerialNumber = showSerialNumber,
                                        onShowSerialNumberChange = { showSerialNumber = it },
                                        serialPrefix = serialPrefix,
                                        onSerialPrefixChange = { serialPrefix = it },
                                        serialCorner = serialCorner,
                                        onSerialCornerChange = { serialCorner = it },
                                        serialOffsetX = serialOffsetX,
                                        onSerialOffsetXChange = { serialOffsetX = it },
                                        serialOffsetY = serialOffsetY,
                                        onSerialOffsetYChange = { serialOffsetY = it },
                                        serialFontSizeSp = serialFontSizeSp,
                                        onSerialFontSizeChange = { serialFontSizeSp = it },
                                        onPrintClick = {
                                            executePrintOrExport {
                                                VoucherPrintUtils.printVouchers(context, cards, currentConfig)
                                            }
                                        },
                                        onExportPdfClick = {
                                            executePrintOrExport {
                                                Toast.makeText(context, "جاري إنشاء ملف PDF...", Toast.LENGTH_SHORT).show()
                                                VoucherPrintUtils.exportToPdf(context, cards, currentConfig) { file ->
                                                    if (file != null) {
                                                        VoucherPrintUtils.shareFile(context, file, "application/pdf", "مشاركة ملف PDF لكروت الهوتسبوت")
                                                    } else {
                                                        Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        onExportImageClick = {
                                            executePrintOrExport {
                                                Toast.makeText(context, "جاري تصدير الصورة...", Toast.LENGTH_SHORT).show()
                                                VoucherPrintUtils.exportPageToImage(context, cards, safePageIndex, currentConfig) { file ->
                                                    if (file != null) {
                                                        VoucherPrintUtils.shareFile(context, file, "image/png", "مشاركة صورة كروت الهوتسبوت")
                                                    } else {
                                                        Toast.makeText(context, "فشل تصدير الصورة", Toast.LENGTH_SHORT).show()
                                                    }
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
    cardWidthMm: Float,
    cardHeightMm: Float,
    offsetX: Float,
    offsetY: Float,
    fontSizeSp: Float,
    templateType: CardTemplateType,
    customImageUri: Uri?,
    customCodeColorArgb: Int,
    networkName: String,
    contactPhone: String,
    showSerialNumber: Boolean,
    serialPrefix: String,
    serialCorner: SerialCorner,
    serialOffsetX: Float,
    serialOffsetY: Float,
    serialFontSizeSp: Float,
    serialMap: Map<String, String>,
    startIndex: Int,
    safePageIndex: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    context: Context
) {
    var customBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(customImageUri?.toString()) {
        customBitmap = if (customImageUri != null) {
            withContext(Dispatchers.IO) {
                VoucherPrintUtils.loadBitmapFromUri(context, customImageUri, maxDim = 1280)
            }
        } else {
            null
        }
    }
    val cellAspect = (cardWidthMm.coerceAtLeast(20f) / cardHeightMm.coerceAtLeast(8f)).coerceIn(1.2f, 5.5f)

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
                                    .aspectRatio(cellAspect)
                                    .clipToBounds()
                                    .border(
                                        width = if (templateType == CardTemplateType.CUSTOM_IMAGE) 0.5.dp else 0.8.dp,
                                        color = if (templateType == CardTemplateType.CUSTOM_IMAGE) Color(0xFFD8E0E8) else Color(0xFFB0C4DE),
                                        shape = RoundedCornerShape(if (templateType == CardTemplateType.CUSTOM_IMAGE) 1.dp else 3.dp)
                                    )
                                    .background(Color.White)
                                    .padding(if (templateType == CardTemplateType.CUSTOM_IMAGE) 0.dp else 1.dp)
                            ) {
                                if (card != null) {
                                    val resolvedDigits = serialMap[card.name]
                                        ?: serialMap[card.id]
                                        ?: VoucherSerialEngine.extractCardSerial(card)?.let { VoucherSerialEngine.formatSerialDigits(it) }
                                        ?: VoucherSerialEngine.formatSerialDigits((startIndex + cardIndex + 1).toLong())
                                    SingleCardRender(
                                        card = card,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        fontSizeSp = fontSizeSp,
                                        templateType = templateType,
                                        customBitmap = customBitmap,
                                        customCodeColorArgb = customCodeColorArgb,
                                        networkName = networkName,
                                        contactPhone = contactPhone,
                                        showSerialNumber = showSerialNumber,
                                        serialLabel = "$serialPrefix$resolvedDigits",
                                        serialCorner = serialCorner,
                                        serialOffsetX = serialOffsetX,
                                        serialOffsetY = serialOffsetY,
                                        serialFontSizeSp = serialFontSizeSp
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
    contactPhone: String,
    showSerialNumber: Boolean = false,
    serialLabel: String = "",
    serialCorner: SerialCorner = SerialCorner.TOP_END,
    serialOffsetX: Float = 0f,
    serialOffsetY: Float = 0f,
    serialFontSizeSp: Float = 8f
) {
    val normalizedPrice = com.example.data.util.FinanceAnalyticsEngine.normalizeDigits(card.price)
    val priceNum = Regex("""\d+(?:\.\d+)?""").find(normalizedPrice)?.value ?: "0"
    val timeLimit = if (card.limitUptime.isNotBlank()) card.limitUptime else "مفتوح"
    val quotaMb = if (card.limitBytesTotal > 0L) "${card.limitBytesTotal / (1024L * 1024L)} ميجا" else "مفتوح"

    val badgeColor = when (templateType) {
        CardTemplateType.VIP_GOLD -> Color(0xFFD4A017)
        CardTemplateType.EMERALD_GREEN -> Color(0xFF00897B)
        else -> Color(0xFF0277BD)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val boxConstraints = constraints
        val boxMaxHeight = maxHeight
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
            val offsetXPx = ((offsetX / 100f) * boxConstraints.maxWidth * 0.45f).roundToInt()
            val offsetYPx = ((offsetY / 50f) * boxConstraints.maxHeight * 0.45f).roundToInt()
            val scaledCodeFontSp = (boxMaxHeight.value * 0.22f * (fontSizeSp / 14f)).coerceAtLeast(5f)

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

                    val builtinOffsetXPx = ((offsetX / 100f) * boxConstraints.maxWidth * 0.45f).roundToInt()
                    val builtinOffsetYPx = ((offsetY / 50f) * boxConstraints.maxHeight * 0.45f).roundToInt()
                    val scaledBuiltinFontSp = (boxMaxHeight.value * 0.22f * (fontSizeSp / 14f)).coerceAtLeast(4.5f)
                    // Login Code with custom offsets, font size, & color
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                IntOffset(
                                    x = builtinOffsetXPx,
                                    y = builtinOffsetYPx
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
                            fontSize = scaledBuiltinFontSp.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(customCodeColorArgb),
                            maxLines = 1,
                            softWrap = false
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

        if (showSerialNumber && serialLabel.isNotBlank()) {
            val snOffsetXPx = ((serialOffsetX / 100f) * boxConstraints.maxWidth * 0.45f).roundToInt()
            val snOffsetYPx = ((serialOffsetY / 50f) * boxConstraints.maxHeight * 0.45f).roundToInt()
            val snFontSp = (boxMaxHeight.value * 0.135f * (serialFontSizeSp / 8f)).coerceAtLeast(3.8f)
            val snAlign = when (serialCorner) {
                SerialCorner.TOP_START -> Alignment.TopStart
                SerialCorner.TOP_END -> Alignment.TopEnd
                SerialCorner.BOTTOM_START -> Alignment.BottomStart
                SerialCorner.BOTTOM_END -> Alignment.BottomEnd
            }
            val rightReserveDp = if (templateType == CardTemplateType.CUSTOM_IMAGE) 3.dp else 18.dp
            Text(
                text = serialLabel,
                fontSize = snFontSp.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (templateType == CardTemplateType.CUSTOM_IMAGE) Color(customCodeColorArgb) else Color(0xFF263238),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(snAlign)
                    .padding(
                        start = if (serialCorner == SerialCorner.TOP_START || serialCorner == SerialCorner.BOTTOM_START) rightReserveDp else 3.dp,
                        end = if (serialCorner == SerialCorner.TOP_END || serialCorner == SerialCorner.BOTTOM_END) 3.dp else 2.dp,
                        top = 1.dp,
                        bottom = 1.dp
                    )
                    .offset { IntOffset(x = snOffsetXPx, y = snOffsetYPx) }
            )
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
    showSerialNumber: Boolean,
    onShowSerialNumberChange: (Boolean) -> Unit,
    serialPrefix: String,
    onSerialPrefixChange: (String) -> Unit,
    serialCorner: SerialCorner,
    onSerialCornerChange: (SerialCorner) -> Unit,
    serialOffsetX: Float,
    onSerialOffsetXChange: (Float) -> Unit,
    serialOffsetY: Float,
    onSerialOffsetYChange: (Float) -> Unit,
    serialFontSizeSp: Float,
    onSerialFontSizeChange: (Float) -> Unit,
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

        val isCustomImageMode = templateType == CardTemplateType.CUSTOM_IMAGE
        if (isCustomImageMode) {
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
        }

        // Network Name & Contact Text Fields — explicitly disabled in CUSTOM_IMAGE mode to avoid overlapping custom design artwork
        OutlinedTextField(
            value = if (isCustomImageMode) "" else networkName,
            onValueChange = onNetworkNameChange,
            enabled = !isCustomImageMode,
            label = { Text("اسم الشبكة في الكرت", color = Color(0xFFB0BEC5), fontSize = 12.sp) },
            placeholder = if (isCustomImageMode) {
                { Text("معطّل مع الصورة المخصصة", color = Color(0xFF78909C), fontSize = 11.sp) }
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color(0xFF78909C),
                focusedBorderColor = Color(0xFF0288D1),
                unfocusedBorderColor = Color(0xFF2E5370),
                disabledBorderColor = Color(0xFF1E3A5F),
                disabledLabelColor = Color(0xFF607D8B)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("network_name_card_input")
        )

        OutlinedTextField(
            value = if (isCustomImageMode) "" else contactPhone,
            onValueChange = onContactPhoneChange,
            enabled = !isCustomImageMode,
            label = { Text("رقم التواصل في الكرت", color = Color(0xFFB0BEC5), fontSize = 12.sp) },
            placeholder = if (isCustomImageMode) {
                { Text("معطّل مع الصورة المخصصة", color = Color(0xFF78909C), fontSize = 11.sp) }
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color(0xFF78909C),
                focusedBorderColor = Color(0xFF0288D1),
                unfocusedBorderColor = Color(0xFF2E5370),
                disabledBorderColor = Color(0xFF1E3A5F),
                disabledLabelColor = Color(0xFF607D8B)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("contact_phone_card_input")
        )

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1E3A5F), thickness = 1.dp)

        // Section 4B: الرقم التسلسلي على الكرت (Flexible Serial Display & Placement)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "إظهار الرقم التسلسلي على الكرت",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "ترقيم مطبوع قابل للتحريك والتخصيص",
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5)
                )
            }
            Switch(
                checked = showSerialNumber,
                onCheckedChange = onShowSerialNumberChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0288D1)
                ),
                modifier = Modifier.testTag("toggle_show_serial_switch")
            )
        }

        AnimatedVisibility(visible = showSerialNumber) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "بادئة التسلسل :", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("#" to "#", "S/N:" to "S/N:", "رقم:" to "رقم:", "" to "رقم فقط").forEach { (prefixVal, label) ->
                        FilterChip(
                            selected = serialPrefix == prefixVal,
                            onClick = { onSerialPrefixChange(prefixVal) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0288D1),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF15334E),
                                labelColor = Color(0xFFECEFF1)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(text = "زاوية العرض على الكرت :", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(SerialCorner.TOP_START, SerialCorner.TOP_END).forEach { corner ->
                        FilterChip(
                            selected = serialCorner == corner,
                            onClick = { onSerialCornerChange(corner) },
                            label = { Text(corner.labelAr, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0288D1),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF15334E),
                                labelColor = Color(0xFFECEFF1)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(SerialCorner.BOTTOM_START, SerialCorner.BOTTOM_END).forEach { corner ->
                        FilterChip(
                            selected = serialCorner == corner,
                            onClick = { onSerialCornerChange(corner) },
                            label = { Text(corner.labelAr, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0288D1),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF15334E),
                                labelColor = Color(0xFFECEFF1)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Serial Horizontal Offset
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "إزاحة التسلسل أفقياً :", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        Text(text = "${serialOffsetX.roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Slider(
                        value = serialOffsetX,
                        onValueChange = onSerialOffsetXChange,
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF0288D1),
                            inactiveTrackColor = Color(0xFF1E3A5F)
                        )
                    )
                }

                // Serial Vertical Offset
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "إزاحة التسلسل عمودياً :", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        Text(text = "${serialOffsetY.roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Slider(
                        value = serialOffsetY,
                        onValueChange = onSerialOffsetYChange,
                        valueRange = -50f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF0288D1),
                            inactiveTrackColor = Color(0xFF1E3A5F)
                        )
                    )
                }

                // Serial Font Size
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "حجم خط التسلسل :", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        Text(text = "${serialFontSizeSp.roundToInt()}sp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Slider(
                        value = serialFontSizeSp,
                        onValueChange = onSerialFontSizeChange,
                        valueRange = 5f..16f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF0288D1),
                            inactiveTrackColor = Color(0xFF1E3A5F)
                        )
                    )
                }
            }
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

/**
 * High-Definition (HD) Preview Dialog to inspect cards layout and clarity before saving/exporting
 */
@Composable
fun HdPreviewDialog(
    cards: List<HotspotCard>,
    pageIndex: Int,
    config: CardDesignConfig,
    onDismiss: () -> Unit,
    onExportPdf: () -> Unit,
    onExportImage: () -> Unit,
    onPrint: () -> Unit
) {
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, config) {
        isLoading = true
        val prev = previewBitmap
        val rendered = withContext(Dispatchers.IO) {
            VoucherPrintUtils.renderPageBitmap(context, cards, pageIndex, config, targetWidth = 1800)
        }
        previewBitmap = rendered
        if (prev != null && prev !== rendered && !prev.isRecycled) {
            try { prev.recycle() } catch (_: Exception) {}
        }
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            val bmp = previewBitmap
            previewBitmap = null
            if (bmp != null && !bmp.isRecycled) {
                try { bmp.recycle() } catch (_: Exception) {}
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFA000))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "معاينة الجودة العالية (HD Preview) - صفحة ${pageIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview Canvas Box (Scrollable & Zoomable container)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE8EEF5), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFC0D0E0), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(color = RouterBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("جاري توليد معاينة الجودة العالية بدقة متناهية...", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else if (previewBitmap != null) {
                        val scrollStateX = rememberScrollState()
                        val scrollStateY = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(scrollStateX)
                                .verticalScroll(scrollStateY),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "معاينة الصفحة عالية الدقة",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        Text("تعذر إنشاء المعاينة", color = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onExportImage()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير كصورة HD")
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onExportPdf()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير PDF")
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onPrint()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RouterBlue)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طباعة مباشرة")
                    }
                }
            }
        }
    }
}
