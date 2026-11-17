package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.data.model.ConnectionState
import com.example.ui.theme.StatusActiveGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private data class PortalBookmark(
    val title: String,
    val url: String
)

private object PortalBookmarkStore {
    private const val PREFS_NAME = "portal_bookmarks_prefs"

    private fun hostKey(rawUrl: String): String {
        val host = runCatching { Uri.parse(rawUrl).host?.lowercase() }.getOrNull().orEmpty()
        return "bm_${host.ifBlank { "default" }}"
    }

    fun load(context: Context, portalUrl: String): List<PortalBookmark> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(hostKey(portalUrl), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val u = obj.optString("url")
                    val t = obj.optString("title").ifBlank { u }
                    if (u.isNotBlank()) add(PortalBookmark(title = t, url = u))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, portalUrl: String, bookmarks: List<PortalBookmark>) {
        val arr = JSONArray()
        bookmarks.take(12).forEach { b ->
            arr.put(JSONObject().apply {
                put("title", b.title)
                put("url", b.url)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(hostKey(portalUrl), arr.toString())
            .apply()
    }
}

private class WebPortalJsBridge(
    private val onDataPayload: (base64DataUrl: String, fileName: String, mimeType: String) -> Unit,
    private val onPrintRequested: () -> Unit
) {
    @JavascriptInterface
    fun saveBase64File(dataUrl: String, fileName: String, mimeType: String) {
        onDataPayload(dataUrl, fileName, mimeType)
    }

    @JavascriptInterface
    fun triggerNativePrint() {
        onPrintRequested()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPortalScreen(
    portalState: ConnectionState.WebPortal,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember(portalState.url) { mutableStateOf(portalState.url) }
    var pageTitle by remember(portalState.url) { mutableStateOf(portalState.serverName) }
    var mainFrameError by remember(portalState.url) { mutableStateOf<String?>(null) }
    var lastAutoFilledUrl by remember(portalState.url) { mutableStateOf<String?>(null) }
    var pendingUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var bookmarks by remember(portalState.url) {
        mutableStateOf(PortalBookmarkStore.load(context, portalState.url))
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val callback = pendingUploadCallback
        pendingUploadCallback = null
        callback?.onReceiveValue(uris.toTypedArray())
    }

    val downloadClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun printCurrentWebView(view: WebView?) {
        if (view == null) return
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val cleanName = pageTitle.ifBlank { portalState.serverName }.take(40).ifBlank { "PortalPage" }
        val adapter = view.createPrintDocumentAdapter(cleanName)
        printManager.print(cleanName, adapter, PrintAttributes.Builder().build())
    }

    fun openExportedFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val resolvedMime = mimeType.ifBlank {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "*/*"
            }
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, "فتح / مشاركة الملف: ${file.name}"))
        } catch (_: Exception) {
            Toast.makeText(context, "تم حفظ الملف (${file.name}) بنجاح", Toast.LENGTH_LONG).show()
        }
    }

    fun saveDataUriToExports(dataUrl: String, suggestedName: String, mimeType: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val commaIdx = dataUrl.indexOf(',')
                if (commaIdx <= 0) return@launch
                val header = dataUrl.substring(0, commaIdx)
                val payload = dataUrl.substring(commaIdx + 1)
                val detectedMime = mimeType.ifBlank {
                    header.substringAfter("data:", "").substringBefore(";").ifBlank { "application/octet-stream" }
                }
                val bytes = if (header.contains(";base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    Uri.decode(payload).toByteArray(Charsets.UTF_8)
                }
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(detectedMime) ?: "bin"
                val safeName = suggestedName.ifBlank { "portal_export_${System.currentTimeMillis()}.$ext" }
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outFile = File(exportDir, safeName)
                outFile.writeBytes(bytes)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "تم تجهيز الملف: $safeName", Toast.LENGTH_SHORT).show()
                    openExportedFile(outFile, detectedMime)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "تعذر حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadHttpFileWithCookies(
        fileUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        userAgent: String?
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "جاري تحميل الملف...", Toast.LENGTH_SHORT).show()
                }
                val cookie = CookieManager.getInstance().getCookie(fileUrl).orEmpty()
                val reqBuilder = Request.Builder().url(fileUrl)
                if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
                if (!userAgent.isNullOrBlank()) reqBuilder.header("User-Agent", userAgent)

                downloadClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "تعذر تحميل الملف (${resp.code})", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val respMime = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?: mimeType?.ifBlank { null }
                        ?: "application/octet-stream"
                    val guessed = URLUtil.guessFileName(fileUrl, contentDisposition, respMime)
                        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val outFile = File(exportDir, guessed)
                    resp.body?.byteStream()?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        openExportedFile(outFile, respMime)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "فشل التحميل: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun injectCredentialsAutoFill(view: WebView?, showFeedback: Boolean) {
        if (view == null) return
        val user = portalState.username.trim()
        val pass = portalState.password
        if (user.isBlank() && pass.isBlank()) {
            if (showFeedback) {
                Toast.makeText(context, "لا توجد بيانات دخول محفوظة للتعبئة التلقائية", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val quotedUser = JSONObject.quote(user)
        val quotedPass = JSONObject.quote(pass)
        val js = """
            (function() {
                var uVal = $quotedUser;
                var pVal = $quotedPass;
                var filled = false;
                var inputs = Array.prototype.slice.call(document.querySelectorAll('input'));
                function isVisible(el) {
                    if (!el || el.disabled || el.readOnly) return false;
                    var style = window.getComputedStyle(el);
                    return style && style.display !== 'none' && style.visibility !== 'hidden' && el.type !== 'hidden';
                }
                function setNativeValue(el, val) {
                    var proto = window.HTMLInputElement && window.HTMLInputElement.prototype;
                    var desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
                    if (desc && desc.set) { desc.set.call(el, val); } else { el.value = val; }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var passInputs = inputs.filter(function(el) {
                    return isVisible(el) && (el.type || '').toLowerCase() === 'password';
                });
                var userCandidates = inputs.filter(function(el) {
                    var t = (el.type || 'text').toLowerCase();
                    return isVisible(el) && (t === 'email' || t === 'text' || t === 'tel');
                });
                var pickedUser = null;
                for (var i = 0; i < userCandidates.length; i++) {
                    var el = userCandidates[i];
                    var meta = ((el.type || '') + ' ' + (el.name || '') + ' ' + (el.id || '') + ' ' +
                        (el.placeholder || '') + ' ' + (el.getAttribute('autocomplete') || '') + ' ' +
                        (el.getAttribute('aria-label') || '')).toLowerCase();
                    if (meta.indexOf('email') >= 0 || meta.indexOf('user') >= 0 || meta.indexOf('login') >= 0 ||
                        meta.indexOf('account') >= 0 || meta.indexOf('بريد') >= 0 || meta.indexOf('مستخدم') >= 0) {
                        pickedUser = el;
                        break;
                    }
                }
                if (!pickedUser && passInputs.length > 0 && userCandidates.length > 0) {
                    pickedUser = userCandidates[0];
                }
                if (pickedUser && uVal && !pickedUser.value) {
                    setNativeValue(pickedUser, uVal);
                    filled = true;
                }
                if (passInputs.length > 0 && pVal && !passInputs[0].value) {
                    setNativeValue(passInputs[0], pVal);
                    filled = true;
                }
                return filled;
            })();
        """.trimIndent()
        view.evaluateJavascript(js) { result ->
            if (showFeedback) {
                val msg = if (result == "true") "تمت تعبئة البريد/المستخدم وكلمة المرور في الصفحة" else "لم يتم العثور على حقول تسجيل دخول فارغة في الصفحة الحالية"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onDisconnect()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentUrl.startsWith("https://", true)) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = if (currentUrl.startsWith("https://", true)) StatusActiveGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = portalState.serverName.ifBlank { "لوحة السيرفر السحابية" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = portalState.subtitle.ifBlank {
                                    if (portalState.isAplusWifi) "لوحة تحكم APlusWiFi" else currentUrl
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.testTag("web_portal_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة لصفحة الاتصال"
                        )
                    }
                },
                actions = {
                    if (portalState.username.isNotBlank() || portalState.password.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { injectCredentialsAutoFill(webViewRef, showFeedback = true) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("web_portal_autofill_btn")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعبئة الدخول", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(
                        onClick = { printCurrentWebView(webViewRef) },
                        modifier = Modifier.testTag("web_portal_print_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة أو حفظ PDF")
                    }
                    IconButton(
                        onClick = {
                            val titleCandidate = pageTitle
                                .ifBlank { Uri.parse(currentUrl).path?.trim('/') ?: "صفحة" }
                                .take(24)
                            if (bookmarks.none { it.url == currentUrl }) {
                                val updated = (bookmarks + PortalBookmark(title = titleCandidate, url = currentUrl)).take(12)
                                bookmarks = updated
                                PortalBookmarkStore.save(context, portalState.url, updated)
                                Toast.makeText(context, "تم تثبيت الصفحة في الاختصارات السريعة", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "هذه الصفحة مثبتة بالفعل", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("web_portal_bookmark_btn")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "تثبيت الصفحة الحالية")
                    }
                    IconButton(
                        onClick = {
                            mainFrameError = null
                            webViewRef?.reload()
                        },
                        modifier = Modifier.testTag("web_portal_reload_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة تحميل الصفحة")
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
            if (isLoading && loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (portalState.isAplusWifi) "APlusWiFi" else "Web Console",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Target Path Quick Jump & User-Pinned Quick Bookmarks Strip
            val targetUrl = portalState.targetPathUrl?.takeIf { it.isNotBlank() && currentUrl.trimEnd('/') != it.trimEnd('/') }
            if (targetUrl != null || bookmarks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (targetUrl != null) {
                        val pathLabel = Uri.parse(targetUrl).path?.ifBlank { "/" } ?: targetUrl
                        FilterChip(
                            selected = false,
                            onClick = { webViewRef?.loadUrl(targetUrl) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = {
                                Text("الانتقال إلى $pathLabel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            },
                            modifier = Modifier.testTag("web_portal_target_path_chip")
                        )
                    }
                    bookmarks.forEach { bm ->
                        val isCurrent = currentUrl.trimEnd('/') == bm.url.trimEnd('/')
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = { webViewRef?.loadUrl(bm.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = bm.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = {
                                        val updated = bookmarks.filterNot { it.url == bm.url }
                                        bookmarks = updated
                                        PortalBookmarkStore.save(context, portalState.url, updated)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إزالة الاختصار",
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                            val bridge = WebPortalJsBridge(
                                onDataPayload = { dataUrl, fileName, mimeType ->
                                    saveDataUriToExports(dataUrl, fileName, mimeType)
                                },
                                onPrintRequested = {
                                    post { printCurrentWebView(this) }
                                }
                            )
                            addJavascriptInterface(bridge, "NetManagerBridge")

                            setDownloadListener { fileUrl, userAgent, contentDisposition, mimeType, _ ->
                                when {
                                    fileUrl.startsWith("data:", ignoreCase = true) -> {
                                        val name = URLUtil.guessFileName("export", contentDisposition, mimeType)
                                        saveDataUriToExports(fileUrl, name, mimeType.orEmpty())
                                    }
                                    fileUrl.startsWith("blob:", ignoreCase = true) -> {
                                        val quotedBlob = JSONObject.quote(fileUrl)
                                        val quotedName = JSONObject.quote(URLUtil.guessFileName("export", contentDisposition, mimeType))
                                        val quotedMime = JSONObject.quote(mimeType.orEmpty())
                                        evaluateJavascript(
                                            """
                                            (function(){
                                                fetch($quotedBlob).then(function(r){ return r.blob(); }).then(function(b){
                                                    var reader = new FileReader();
                                                    reader.onloadend = function(){
                                                        if (window.NetManagerBridge && reader.result) {
                                                            window.NetManagerBridge.saveBase64File(reader.result, $quotedName, b.type || $quotedMime);
                                                        }
                                                    };
                                                    reader.readAsDataURL(b);
                                                });
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }
                                    else -> downloadHttpFileWithCookies(fileUrl, contentDisposition, mimeType, userAgent)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    pendingUploadCallback?.onReceiveValue(null)
                                    pendingUploadCallback = filePathCallback
                                    return try {
                                        val accept = fileChooserParams?.acceptTypes
                                            ?.filter { it.isNotBlank() }
                                            ?.toTypedArray()
                                            ?.takeIf { it.isNotEmpty() }
                                            ?: arrayOf("*/*")
                                        fileChooserLauncher.launch(accept)
                                        true
                                    } catch (_: Exception) {
                                        pendingUploadCallback?.onReceiveValue(null)
                                        pendingUploadCallback = null
                                        false
                                    }
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    val tempWebView = WebView(ctx).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                popupView: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val target = request?.url?.toString()
                                                if (!target.isNullOrBlank()) {
                                                    view?.loadUrl(target)
                                                }
                                                popupView?.destroy()
                                                return true
                                            }
                                        }
                                    }
                                    transport.webView = tempWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url ?: return false
                                    val scheme = reqUrl.scheme?.lowercase().orEmpty()
                                    if (scheme == "http" || scheme == "https") {
                                        return false
                                    }
                                    return try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, reqUrl))
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    mainFrameError = null
                                    if (!url.isNullOrBlank()) currentUrl = url
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    // Wire window.print() inside portal pages directly to native Android Print/PDF
                                    view?.evaluateJavascript(
                                        "window.print = function(){ if(window.NetManagerBridge){ window.NetManagerBridge.triggerNativePrint(); } };",
                                        null
                                    )
                                    if (url != null && url != lastAutoFilledUrl) {
                                        lastAutoFilledUrl = url
                                        injectCredentialsAutoFill(view, showFeedback = false)
                                        view?.postDelayed({
                                            injectCredentialsAutoFill(view, showFeedback = false)
                                        }, 650L)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        val desc = error?.description?.toString()?.takeIf { it.isNotBlank() }
                                        mainFrameError = desc ?: "تعذر تحميل صفحة لوحة التحكم. تحقق من اتصال الإنترنت وحاول مجدداً."
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(portalState.url)
                        }
                    },
                    update = { wv ->
                        webViewRef = wv
                    }
                )

                val errMsg = mainFrameError
                if (errMsg != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "تعذر فتح لوحة التحكم",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = errMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            mainFrameError = null
                                            webViewRef?.reload()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
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
