package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.data.local.CachedPortalMeta
import com.example.data.local.WebPortalCacheManager
import com.example.data.model.ConnectionState
import com.example.data.network.NetworkUtils
import com.example.data.network.RouterConnectionParser
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
    private val isHostAuthorized: () -> Boolean,
    private val onDataPayload: (base64DataUrl: String, fileName: String, mimeType: String) -> Unit,
    private val onPrintRequested: () -> Unit
) {
    @JavascriptInterface
    fun saveBase64File(dataUrl: String, fileName: String, mimeType: String) {
        if (!isHostAuthorized()) return
        onDataPayload(dataUrl, fileName, mimeType)
    }

    @JavascriptInterface
    fun triggerNativePrint() {
        if (!isHostAuthorized()) return
        onPrintRequested()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPortalScreen(
    portalState: ConnectionState.WebPortal,
    onDisconnect: () -> Unit,
    onRetryApiConnection: (() -> Unit)? = null,
    onArchiveSaved: ((CachedPortalMeta) -> Unit)? = null,
    onActivityRecorded: ((actionType: String, target: String, description: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewKey by remember(portalState.url) { mutableIntStateOf(0) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isHydratingContent by remember { mutableStateOf(false) }
    var currentUrl by remember(portalState.url) { mutableStateOf(portalState.url) }
    val latestAuthorizedUrlRef = remember(portalState.url) { java.util.concurrent.atomic.AtomicReference(portalState.url) }
    var pageTitle by remember(portalState.url) { mutableStateOf(portalState.serverName) }
    var mainFrameError by remember(portalState.url) { mutableStateOf<String?>(null) }
    var partialLoadWarning by remember(portalState.url) { mutableStateOf<String?>(null) }
    var autoCacheRecoveryAttempted by remember(portalState.url) { mutableStateOf(false) }
    var lastAutoFilledUrl by remember(portalState.url) { mutableStateOf<String?>(null) }
    var lastSnapshotUrl by remember(portalState.url) { mutableStateOf<String?>(null) }
    var lastRecordedPathUrl by remember(portalState.url) { mutableStateOf<String?>(null) }
    var pendingUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var bookmarks by remember(portalState.url) {
        mutableStateOf(PortalBookmarkStore.load(context, portalState.url))
    }

    val hostName = remember(portalState.url) {
        runCatching { Uri.parse(portalState.url).host }.getOrNull().orEmpty()
    }
    val isLocalHost = remember(portalState.isMikroTikWebFig, hostName) {
        portalState.isMikroTikWebFig || RouterConnectionParser.isPrivateOrLocalIpv4(hostName)
    }
    val archiveFile = remember(portalState.url, portalState.cachedArchivePath) {
        portalState.cachedArchivePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: WebPortalCacheManager.getArchiveTargetFile(context, portalState.url)
    }
    fun isHostAuthorizedForUrl(candidateUrl: String?): Boolean {
        val raw = candidateUrl.orEmpty()
        if (raw.startsWith("file://", ignoreCase = true)) {
            return runCatching { Uri.parse(raw).path == archiveFile.absolutePath }.getOrDefault(false)
        }
        val candidateHost = runCatching { Uri.parse(raw).host?.lowercase() }.getOrNull().orEmpty()
        val expectedHost = hostName.lowercase()
        if (expectedHost.isBlank()) return candidateHost.isNotBlank()
        if (candidateHost.isBlank()) return false
        return candidateHost == expectedHost ||
            candidateHost.endsWith(".$expectedHost") ||
            (isLocalHost && RouterConnectionParser.isPrivateOrLocalIpv4(candidateHost))
    }
    var hasSavedArchive by remember(portalState.url) {
        mutableStateOf(archiveFile.exists() && archiveFile.length() > 0L)
    }
    var lastSavedTimeMs by remember(portalState.url) {
        mutableLongStateOf(portalState.lastSavedTime)
    }
    var isDeviceOnline by remember {
        mutableStateOf(NetworkUtils.isNetworkOnline(context))
    }
    var showingOfflineArchive by remember(portalState.url, portalState.isOfflinePreview) {
        mutableStateOf(portalState.isOfflinePreview || (!isLocalHost && !NetworkUtils.isNetworkOnline(context) && archiveFile.exists()))
    }
    val isEffectiveOffline = showingOfflineArchive || (!isLocalHost && !isDeviceOnline)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    isDeviceOnline = true
                }
            }

            override fun onLost(network: Network) {
                val onlineNow = NetworkUtils.isNetworkOnline(context)
                mainHandler.post {
                    isDeviceOnline = onlineNow
                }
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { cm?.unregisterNetworkCallback(callback) }
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    LaunchedEffect(isDeviceOnline) {
        if (isDeviceOnline && showingOfflineArchive) {
            val targetLiveUrl = currentUrl.takeIf { it.startsWith("http", ignoreCase = true) } ?: portalState.url
            showingOfflineArchive = false
            mainFrameError = null
            partialLoadWarning = null
            webViewRef?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
            webViewRef?.loadUrl(targetLiveUrl)
            Toast.makeText(context, "✅ عاد الاتصال بالإنترنت — جاري التحديث المباشر", Toast.LENGTH_SHORT).show()
        }
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

    fun recordPortalMovement(actionType: String, target: String, description: String) {
        onActivityRecorded?.invoke(actionType, target, description)
        scope.launch(Dispatchers.IO) {
            WebPortalCacheManager.appendPortalActivity(context, portalState.url, "$actionType: $target")
        }
    }

    fun printCurrentWebView(view: WebView?) {
        if (view == null) return
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val cleanName = pageTitle.ifBlank { portalState.serverName }.take(40).ifBlank { "PortalPage" }
        val adapter = view.createPrintDocumentAdapter(cleanName)
        printManager.print(cleanName, adapter, PrintAttributes.Builder().build())
        recordPortalMovement("طباعة", cleanName, "طباعة / تصدير PDF من لوحة الويب ($currentUrl)")
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
                recordPortalMovement("تصدير ملف", safeName, "تم تصدير وحفظ الملف ($safeName) من لوحة التحكم")
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
                    recordPortalMovement("تحميل ملف", guessed, "تم تنزيل الملف ($guessed) من ($fileUrl)")
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
        val activePageUrl = view.url.orEmpty().ifBlank { currentUrl }
        if (!isHostAuthorizedForUrl(activePageUrl)) {
            if (showFeedback) {
                Toast.makeText(context, "التعبئة التلقائية مقيدة بنطاق البوابة الأصلي لحمايتك", Toast.LENGTH_SHORT).show()
            }
            return
        }
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
                        meta.indexOf('account') >= 0 || meta.indexOf('name') >= 0 ||
                        meta.indexOf('بريد') >= 0 || meta.indexOf('مستخدم') >= 0) {
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
            if (result == "true") {
                recordPortalMovement("تعبئة الدخول", portalState.username, "تعبئة بيانات الدخول تلقائياً في صفحة ($currentUrl)")
            }
            if (showFeedback) {
                val msg = if (result == "true") "تمت تعبئة البريد/المستخدم وكلمة المرور في الصفحة" else "لم يتم العثور على حقول تسجيل دخول فارغة في الصفحة الحالية"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Waits until SPA main content (excluding header/nav/sidebar/footer chrome) has genuinely hydrated
     * so loading doesn't dismiss early and valid offline MHT archives are never overwritten by blank shells.
     */
    fun scheduleHydratedSnapshot(view: WebView?, targetUrl: String, attempt: Int = 0) {
        if (view == null || !targetUrl.startsWith("http", ignoreCase = true)) return
        val delayMs = when (attempt) {
            0 -> 900L
            1 -> 1500L
            2 -> 2200L
            else -> 2800L
        }
        view.postDelayed({
            val checkJs = """
                (function() {
                    var state = document.readyState || '';
                    var mainEl = document.querySelector('main, [role="main"], .main-content, .page-content, .content-wrapper, .content');
                    function stripChromeText(root) {
                        if (!root) return '';
                        try {
                            var clone = root.cloneNode(true);
                            var chrome = clone.querySelectorAll('nav, header, footer, aside, .sidebar, .navbar, .topbar, .app-footer, script, style');
                            for (var i = 0; i < chrome.length; i++) {
                                if (chrome[i] && chrome[i].parentNode) chrome[i].parentNode.removeChild(chrome[i]);
                            }
                            return (clone.innerText || '').trim();
                        } catch (e) {
                            return (root.innerText || '').trim();
                        }
                    }
                    function countContentInteractive(root) {
                        if (!root) return 0;
                        var nodes = root.querySelectorAll('table tr, form, input:not([type="hidden"]), canvas, .card, [class*="card"], [class*="stat"], [class*="chart"]');
                        var count = 0;
                        for (var i = 0; i < nodes.length; i++) {
                            if (!nodes[i].closest('nav, header, footer, aside, .sidebar, .navbar, .topbar')) {
                                count++;
                            }
                        }
                        return count;
                    }
                    var contentRoot = mainEl || document.querySelector('#app, #root') || document.body;
                    var strippedText = stripChromeText(contentRoot);
                    var contentText = strippedText.length;
                    var mainInteractive = countContentInteractive(contentRoot);
                    var loaderVisible = !!document.querySelector('[class*="spinner"]:not([style*="display: none"]), [class*="loading"]:not([style*="display: none"]), .v-progress-circular, .ant-spin-spinning');
                    return JSON.stringify({
                        state: state,
                        contentText: contentText,
                        interactive: mainInteractive,
                        loading: loaderVisible
                    });
                })();
            """.trimIndent()
            view.evaluateJavascript(checkJs) { raw ->
                val info = runCatching {
                    val unwrapped = JSONObject(raw?.trim()?.let {
                        if (it.startsWith("\"") && it.endsWith("\"")) JSONObject("{\"v\":$it}").getString("v") else it
                    } ?: "{}")
                    unwrapped
                }.getOrNull()
                val readyState = info?.optString("state").orEmpty()
                val contentTextLen = info?.optInt("contentText", 0) ?: 0
                val interactiveCount = info?.optInt("interactive", 0) ?: 0
                val spinnerVisible = info?.optBoolean("loading", false) ?: false

                val hasSubstantiveMainContent = contentTextLen >= 30 || interactiveCount >= 2
                val isHydrated = readyState == "complete" && !spinnerVisible && hasSubstantiveMainContent
                if (isHydrated) {
                    isHydratingContent = false
                    partialLoadWarning = null
                    lastSnapshotUrl = targetUrl
                    runCatching { CookieManager.getInstance().flush() }
                    val targetFile = WebPortalCacheManager.getArchiveTargetFile(context, portalState.url)
                    val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                    view.saveWebArchive(tempFile.absolutePath, false) { savedPath ->
                        val candidateFile = savedPath?.takeIf { it.isNotBlank() }?.let { File(it) } ?: tempFile
                        if (candidateFile.exists() && candidateFile.length() > 1024L) {
                            val promoted = runCatching {
                                if (candidateFile.absolutePath != targetFile.absolutePath) {
                                    candidateFile.copyTo(targetFile, overwrite = true)
                                    candidateFile.delete()
                                }
                                targetFile
                            }.getOrNull()
                            if (promoted != null && promoted.exists() && promoted.length() > 0L) {
                                val now = System.currentTimeMillis()
                                hasSavedArchive = true
                                lastSavedTimeMs = now
                                val meta = CachedPortalMeta(
                                    host = hostName.ifBlank { portalState.url },
                                    serverName = portalState.serverName,
                                    baseUrl = portalState.url,
                                    lastVisitedUrl = currentUrl,
                                    lastPageTitle = pageTitle,
                                    targetPathUrl = portalState.targetPathUrl,
                                    username = portalState.username,
                                    isAplusWifi = portalState.isAplusWifi,
                                    isMikroTikWebFig = portalState.isMikroTikWebFig,
                                    archiveFilePath = promoted.absolutePath,
                                    lastSavedTime = now
                                )
                                WebPortalCacheManager.saveMeta(context, meta)
                                onArchiveSaved?.invoke(meta)
                            }
                        } else {
                            runCatching { candidateFile.delete() }
                        }
                    }
                } else if (attempt < 3) {
                    scheduleHydratedSnapshot(view, targetUrl, attempt + 1)
                } else {
                    isHydratingContent = false
                    // Content stayed blank after multiple hydration checks on a non-login route: preserve existing valid MHT archive and show recovery banner
                    val pathLower = runCatching { Uri.parse(targetUrl).path?.lowercase().orEmpty() }.getOrDefault("")
                    if (pathLower.isNotBlank() && pathLower != "/" && !pathLower.contains("login") && !hasSubstantiveMainContent) {
                        partialLoadWarning = "يبدو أن محتوى الصفحة لم يكتمل بسبب بطء الشبكة أو ملفات الكاش المؤقتة"
                    }
                }
            }
        }, delayMs)
    }

    fun reloadPortalWithoutCache(view: WebView?) {
        if (view == null) return
        mainFrameError = null
        partialLoadWarning = null
        isLoading = true
        isHydratingContent = true
        val liveUrl = currentUrl.takeIf { it.startsWith("http", ignoreCase = true) } ?: portalState.url
        showingOfflineArchive = false
        view.clearCache(false)
        view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        view.loadUrl(liveUrl)
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
                                recordPortalMovement("تثبيت اختصار", titleCandidate, "تثبيت الصفحة ($currentUrl) في الاختصارات السريعة")
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
                            partialLoadWarning = null
                            val wv = webViewRef
                            if (wv != null) {
                                recordPortalMovement("تحديث الصفحة", currentUrl, "إعادة تحميل صفحة الويب ($currentUrl)")
                                if (showingOfflineArchive || wv.url?.startsWith("file://", ignoreCase = true) == true) {
                                    val liveUrl = currentUrl.takeIf { it.startsWith("http", ignoreCase = true) } ?: portalState.url
                                    showingOfflineArchive = false
                                    wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    wv.loadUrl(liveUrl)
                                } else {
                                    wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    wv.reload()
                                }
                            }
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
            Surface(
                color = if (isEffectiveOffline)
                    Color(0xFFFFF3E0)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
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
                        text = when {
                            showingOfflineArchive -> "نسخة محفوظة: $currentUrl"
                            isHydratingContent && !isLoading -> "جاري استكمال تحميل عناصر الصفحة..."
                            else -> currentUrl
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEffectiveOffline) Color(0xFF8D4E00) else MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isEffectiveOffline) Color(0xFFE65100) else StatusActiveGreen,
                        modifier = Modifier.testTag("web_portal_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isEffectiveOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = when {
                                    isEffectiveOffline -> "أوفلاين • نسخة محفوظة"
                                    hasSavedArchive -> "متصل • حفظ تلقائي"
                                    portalState.isAplusWifi -> "APlusWiFi مباشر"
                                    portalState.isMikroTikWebFig -> "WebFig مباشر"
                                    else -> "متصل مباشر"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Dedicated Offline Alert Banner (تنبيه واضح لوضع الأوفلاين والنسخة المؤقتة)
            AnimatedVisibility(visible = isEffectiveOffline) {
                Surface(
                    color = Color(0xFFFFE0B2),
                    border = BorderStroke(1.dp, Color(0xFFFB8C00)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_portal_offline_alert_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE65100)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "تنبيه: وضع التصفح المؤقت (بدون إنترنت)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6D3500)
                                )
                                Text(
                                    text = if (lastSavedTimeMs > 0L) {
                                        "تعرض الآن آخر نسخة محفوظة بتاريخ: ${WebPortalCacheManager.formatSavedTimeAr(lastSavedTimeMs)}"
                                    } else {
                                        "انقطع الاتصال — يتم التصفح من الذاكرة المؤقتة المحلية"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Color(0xFF7A3E00)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val onlineNow = NetworkUtils.isNetworkOnline(context)
                                isDeviceOnline = onlineNow
                                val targetLiveUrl = currentUrl.takeIf { it.startsWith("http", true) } ?: portalState.url
                                if (onlineNow || isLocalHost) {
                                    showingOfflineArchive = false
                                    mainFrameError = null
                                    partialLoadWarning = null
                                    webViewRef?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
                                    webViewRef?.loadUrl(targetLiveUrl)
                                } else {
                                    Toast.makeText(context, "ما زال الجهاز بدون اتصال إنترنت — متاح التصفح المحلي للنسخة المحفوظة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, Color(0xFFE65100)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("تحديث", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D3500))
                        }
                    }
                }
            }

            // Recovery Strip when SPA subresources / JS chunks fail or inner body looks blank
            AnimatedVisibility(visible = partialLoadWarning != null && !isEffectiveOffline) {
                Surface(
                    color = Color(0xFFFFF8E1),
                    border = BorderStroke(1.dp, Color(0xFFFFB300)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_portal_partial_recovery_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = partialLoadWarning.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = Color(0xFF5D4037),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { reloadPortalWithoutCache(webViewRef) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحديث كامل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Optional 1-tap upgrade strip when inside MikroTik WebFig fallback mode
            if (portalState.isMikroTikWebFig && onRetryApiConnection != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
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
                            text = "واجهة WebFig للراوتر مفتوحة • فعّل API من IP -> Services للإدارة الكاملة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onRetryApiConnection,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("web_portal_retry_api_btn")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تجربة API (8728)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                key(webViewKey) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                isFocusable = true
                                isFocusableInTouchMode = true
                                scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                // Strip Android "; wv" token so modern cloud dashboards/SPAs & WAFs treat WebView as standard Chrome Mobile
                                val rawUa = settings.userAgentString.orEmpty()
                                if (rawUa.isNotBlank()) {
                                    settings.userAgentString = rawUa
                                        .replace("; wv)", ")")
                                        .replace("; wv;", ";")
                                }

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.allowContentAccess = true
                                settings.loadsImagesAutomatically = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.textZoom = 100
                                // Allow local MHT WebArchive file:// loading without opening file-to-file or universal access
                                settings.allowFileAccess = true
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.cacheMode = if (showingOfflineArchive || (!isLocalHost && !isDeviceOnline)) {
                                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                                } else {
                                    WebSettings.LOAD_DEFAULT
                                }
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                // Keep target="_blank" & window.open() navigation inside the same authenticated WebView session
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                val bridge = WebPortalJsBridge(
                                    isHostAuthorized = { isHostAuthorizedForUrl(latestAuthorizedUrlRef.get()) },
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
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                        if (currentUrl.startsWith("http", ignoreCase = true)) {
                                            WebPortalCacheManager.updateLastVisitedUrl(context, portalState.url, currentUrl, title)
                                        }
                                    }
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    if (consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                        val msg = consoleMessage.message().orEmpty()
                                        if (msg.contains("ChunkLoadError", ignoreCase = true) ||
                                            msg.contains("Loading chunk", ignoreCase = true) ||
                                            msg.contains("Failed to fetch dynamically imported module", ignoreCase = true)
                                        ) {
                                            if (!autoCacheRecoveryAttempted) {
                                                autoCacheRecoveryAttempted = true
                                                post { reloadPortalWithoutCache(this@apply) }
                                            } else {
                                                partialLoadWarning = "تعذر تحميل وحدة من الصفحة — اضغط تحديث كامل بدون كاش"
                                            }
                                        }
                                    }
                                    return super.onConsoleMessage(consoleMessage)
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
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url ?: return false
                                    val scheme = reqUrl.scheme?.lowercase().orEmpty()
                                    if (scheme == "http" || scheme == "https" || scheme == "about" ||
                                        scheme == "blob" || scheme == "data" || scheme == "javascript"
                                    ) {
                                        return false
                                    }
                                    if (scheme == "file" && reqUrl.path == archiveFile.absolutePath) {
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
                                    isHydratingContent = url?.startsWith("http", ignoreCase = true) == true
                                    mainFrameError = null
                                    partialLoadWarning = null
                                    if (!url.isNullOrBlank() && !url.startsWith("file://", ignoreCase = true)) {
                                        currentUrl = url
                                        latestAuthorizedUrlRef.set(url)
                                    } else if (!url.isNullOrBlank()) {
                                        latestAuthorizedUrlRef.set(url)
                                    }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    if (url.isNullOrBlank()) return
                                    latestAuthorizedUrlRef.set(url)
                                    if (url.startsWith("file://", ignoreCase = true)) return
                                    currentUrl = url
                                    showingOfflineArchive = false
                                    runCatching { CookieManager.getInstance().flush() }
                                    if (url != lastRecordedPathUrl && url.startsWith("http", ignoreCase = true)) {
                                        lastRecordedPathUrl = url
                                        val pathSeg = runCatching { Uri.parse(url).path?.ifBlank { "/" } }.getOrNull() ?: url
                                        WebPortalCacheManager.updateLastVisitedUrl(context, portalState.url, url, pageTitle)
                                        onActivityRecorded?.invoke(
                                            "تصفح لوحة التحكم",
                                            pathSeg,
                                            "الانتقال إلى الصفحة: $url"
                                        )
                                        isHydratingContent = true
                                        scheduleHydratedSnapshot(view, url, attempt = 0)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (!url.isNullOrBlank()) {
                                        latestAuthorizedUrlRef.set(url)
                                    }
                                    if (url != null && url.startsWith("file://", ignoreCase = true)) {
                                        isHydratingContent = false
                                        showingOfflineArchive = true
                                        return
                                    }
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                        showingOfflineArchive = false
                                        runCatching { CookieManager.getInstance().flush() }
                                        if (url != lastRecordedPathUrl && url.startsWith("http", ignoreCase = true)) {
                                            lastRecordedPathUrl = url
                                            val pathSeg = runCatching { Uri.parse(url).path?.ifBlank { "/" } }.getOrNull() ?: url
                                            WebPortalCacheManager.updateLastVisitedUrl(context, portalState.url, url, pageTitle)
                                            onActivityRecorded?.invoke(
                                                "تصفح لوحة التحكم",
                                                pathSeg,
                                                "فتح وتحميل الصفحة: $url"
                                            )
                                        }
                                    }
                                    // Restore standard cache mode after a forced cache-bypass reload
                                    if (view?.settings?.cacheMode == WebSettings.LOAD_NO_CACHE) {
                                        view.settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    }
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
                                    if (url != null && url.startsWith("http", ignoreCase = true) && url != lastSnapshotUrl) {
                                        isHydratingContent = true
                                        scheduleHydratedSnapshot(view, url, attempt = 0)
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    val status = errorResponse?.statusCode ?: return
                                    val reqUrl = request?.url?.toString().orEmpty()
                                    if (status >= 400 && (reqUrl.endsWith(".js", true) ||
                                            reqUrl.endsWith(".mjs", true) ||
                                            reqUrl.endsWith(".css", true) ||
                                            reqUrl.contains("/assets/", true) ||
                                            reqUrl.contains("chunk", true))
                                    ) {
                                        if (!autoCacheRecoveryAttempted) {
                                            autoCacheRecoveryAttempted = true
                                            post { reloadPortalWithoutCache(view) }
                                        } else {
                                            partialLoadWarning = "تعذر تحميل ملفات واجهة الصفحة (خطأ $status) — اضغط تحديث كامل"
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    // A LAN IP is not proof of identity; never ignore a certificate error.
                                    handler?.cancel()
                                    mainFrameError = "شهادة HTTPS غير موثوقة لهذا الراوتر؛ ثبّت شهادة صالحة قبل إدخال بيانات الإدارة."
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    webViewRef = null
                                    runCatching { (view?.parent as? ViewGroup)?.removeView(view) }
                                    runCatching { view?.destroy() }
                                    partialLoadWarning = "تمت إعادة تنشيط محرك العرض تلقائياً"
                                    webViewKey++
                                    return true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    val failingUrl = request?.url?.toString().orEmpty()
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        isHydratingContent = false
                                        if (!failingUrl.startsWith("file://", ignoreCase = true) &&
                                            archiveFile.exists() && archiveFile.length() > 0L
                                        ) {
                                            showingOfflineArchive = true
                                            mainFrameError = null
                                            view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                                            view?.loadUrl("file://${archiveFile.absolutePath}")
                                            Toast.makeText(
                                                context,
                                                "🟠 انقطع الاتصال — تم التحويل لآخر نسخة محفوظة تلقائياً",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            val desc = error?.description?.toString()?.takeIf { it.isNotBlank() }
                                            mainFrameError = desc ?: "تعذر تحميل صفحة لوحة التحكم. تحقق من اتصال الإنترنت وحاول مجدداً."
                                        }
                                    } else if (failingUrl.endsWith(".js", true) ||
                                        failingUrl.endsWith(".mjs", true) ||
                                        failingUrl.endsWith(".css", true) ||
                                        failingUrl.contains("/api/", true)
                                    ) {
                                        partialLoadWarning = "تعذر تحميل بعض بيانات الصفحة من الشبكة — اضغط تحديث كامل"
                                    }
                                }
                            }

                            webViewRef = this
                            val initialUrl = if (showingOfflineArchive && archiveFile.exists() && archiveFile.length() > 0L) {
                                "file://${archiveFile.absolutePath}"
                            } else {
                                currentUrl.takeIf { it.startsWith("http", ignoreCase = true) } ?: portalState.url
                            }
                            loadUrl(initialUrl)
                        }
                    },
                    update = { wv ->
                        webViewRef = wv
                    }
                    )
                }

                // Overlay progress indicator so showing/hiding it never causes WebView layout resize
                if (isLoading || isHydratingContent) {
                    if (isLoading && loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }

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
                                        onClick = { reloadPortalWithoutCache(webViewRef) },
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
