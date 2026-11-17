package com.example.data.network

data class ParsedHostInput(
    val host: String,
    val explicitPort: Int? = null,
    val inferredHttps: Boolean? = null,
    val rawScheme: String? = null,
    val rawPath: String? = null,
    val isDomain: Boolean = false,
    val isPrivateIp: Boolean = false
)

data class SmartPastedCredentials(
    val host: String? = null,
    val port: Int? = null,
    val useHttps: Boolean? = null,
    val path: String? = null,
    val username: String? = null,
    val password: String? = null
)

object RouterConnectionParser {

    private val BIDI_AND_INVISIBLE_REGEX = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069\uFEFF]")
    private val WHATSAPP_PREFIX_REGEX = Regex("""^\[[^\]]{3,40}\]\s*[^:\n]{1,60}:\s*(.+)$""")
    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val DOMAIN_OR_HOST_REGEX = Regex("""^(?i)(?:https?://)?(?:[a-z0-9-]+\.)+[a-z]{2,}(?::\d{1,5})?(?:/.*)?$""")
    private val HOST_WITH_PORT_REGEX = Regex("""^(?i)(?:https?://)?[a-z0-9.-]+:\d{1,5}(?:/.*)?$""")

    fun stripInvisibleAndChatPrefix(raw: String): String {
        val cleaned = BIDI_AND_INVISIBLE_REGEX.replace(raw, "").trim()
        val waMatch = WHATSAPP_PREFIX_REGEX.matchEntire(cleaned)
        return (waMatch?.groupValues?.get(1) ?: cleaned).trim().trim('"', '\'', '<', '>')
    }

    fun isIpv4(value: String): Boolean {
        val m = IPV4_REGEX.matchEntire(value.trim()) ?: return false
        return (1..4).all { idx ->
            val part = m.groupValues[idx].toIntOrNull() ?: return false
            part in 0..255
        }
    }

    fun isPrivateOrLocalIpv4(host: String): Boolean {
        if (!isIpv4(host)) return false
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b, _, _) = parts
        return a == 10 ||
            a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254)
    }

    fun normalizeHostInput(raw: String): ParsedHostInput {
        var text = stripInvisibleAndChatPrefix(raw)
        if (text.isBlank()) {
            return ParsedHostInput(host = "")
        }

        var scheme: String? = null
        var inferredHttps: Boolean? = null

        when {
            text.startsWith("https://", ignoreCase = true) -> {
                scheme = "https"
                inferredHttps = true
                text = text.substring(8)
            }
            text.startsWith("http://", ignoreCase = true) -> {
                scheme = "http"
                inferredHttps = false
                text = text.substring(7)
            }
            text.startsWith("api-ssl://", ignoreCase = true) -> {
                scheme = "api-ssl"
                inferredHttps = true
                text = text.substring(10)
            }
            text.startsWith("api://", ignoreCase = true) -> {
                scheme = "api"
                inferredHttps = false
                text = text.substring(6)
            }
        }

        // Remove credentials embedded in URL if any (e.g., user@host)
        if (text.contains('@')) {
            text = text.substringAfterLast('@')
        }

        // Capture path/query before stripping it for socket/DNS host extraction
        val slashIdx = text.indexOf('/')
        val extractedPath = if (slashIdx >= 0) {
            val candidate = text.substring(slashIdx).substringBefore('#').trim()
            if (candidate == "/" || candidate.isBlank()) null else candidate
        } else {
            null
        }

        // Drop path, query, fragment
        text = text.substringBefore('/').substringBefore('?').substringBefore('#').trim()

        var explicitPort: Int? = null
        var host = text

        if (text.startsWith("[") && text.contains("]")) {
            val closing = text.indexOf(']')
            host = text.substring(1, closing).trim()
            val remainder = text.substring(closing + 1).trim()
            if (remainder.startsWith(":")) {
                explicitPort = remainder.substring(1).toIntOrNull()?.takeIf { it in 1..65535 }
            }
        } else {
            val lastColon = text.lastIndexOf(':')
            if (lastColon > 0) {
                val portPart = text.substring(lastColon + 1).trim()
                val parsedPort = portPart.toIntOrNull()
                if (parsedPort != null && parsedPort in 1..65535) {
                    host = text.substring(0, lastColon).trim()
                    explicitPort = parsedPort
                }
            }
        }

        if (explicitPort == 443 || explicitPort == 8729) {
            inferredHttps = true
        } else if (explicitPort == null && scheme == "https") {
            explicitPort = 443
        } else if (explicitPort == null && scheme == "http") {
            explicitPort = 80
        } else if (explicitPort == null && scheme == "api-ssl") {
            explicitPort = 8729
        } else if (explicitPort == null && scheme == "api") {
            explicitPort = 8728
        }

        val isIp = isIpv4(host)
        val isPrivate = isPrivateOrLocalIpv4(host)
        val isDomain = host.isNotBlank() && !isIp && (host.contains('.') || host.any { it.isLetter() })

        return ParsedHostInput(
            host = host,
            explicitPort = explicitPort,
            inferredHttps = inferredHttps,
            rawScheme = scheme,
            rawPath = extractedPath,
            isDomain = isDomain,
            isPrivateIp = isPrivate
        )
    }

    fun looksLikeHostToken(token: String): Boolean {
        val cleaned = stripInvisibleAndChatPrefix(token)
        if (cleaned.isBlank() || cleaned.contains(' ') || cleaned.contains('@')) return false
        val parsed = normalizeHostInput(cleaned)
        if (parsed.host.isBlank()) return false
        return isIpv4(parsed.host) ||
            DOMAIN_OR_HOST_REGEX.matches(cleaned) ||
            HOST_WITH_PORT_REGEX.matches(cleaned)
    }

    fun parseSmartCredentialsPaste(rawText: String): SmartPastedCredentials? {
        val rawLines = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { stripInvisibleAndChatPrefix(it) }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) return null

        var host: String? = null
        var port: Int? = null
        var useHttps: Boolean? = null
        var path: String? = null
        var username: String? = null
        var password: String? = null
        val unlabeledValues = mutableListOf<String>()

        val hostLabels = listOf("host", "server", "ip", "url", "domain", "address", "السيرفر", "الراوتر", "العنوان", "الرابط", "الدومين")
        val userLabels = listOf("user", "username", "email", "login", "account", "المستخدم", "اسم المستخدم", "البريد", "الايميل", "الإيميل", "الحساب")
        val passLabels = listOf("pass", "password", "pwd", "secret", "كلمة المرور", "كلمة السر", "المرور", "الرمز")

        for (line in rawLines) {
            val colonIdx = line.indexOf(':')
            val eqIdx = line.indexOf('=')
            val sepIdx = when {
                colonIdx > 0 && !line.startsWith("http", true) && !DOMAIN_OR_HOST_REGEX.matches(line) && !HOST_WITH_PORT_REGEX.matches(line) -> colonIdx
                eqIdx > 0 -> eqIdx
                else -> -1
            }

            if (sepIdx > 0) {
                val label = line.substring(0, sepIdx).trim().lowercase()
                val value = line.substring(sepIdx + 1).trim()
                when {
                    hostLabels.any { label.contains(it) } && value.isNotBlank() -> {
                        val parsed = normalizeHostInput(value)
                        if (parsed.host.isNotBlank()) {
                            host = parsed.host
                            if (parsed.explicitPort != null) port = parsed.explicitPort
                            if (parsed.inferredHttps != null) useHttps = parsed.inferredHttps
                            if (parsed.rawPath != null) path = parsed.rawPath
                        }
                    }
                    userLabels.any { label.contains(it) } && value.isNotBlank() -> {
                        username = value
                    }
                    passLabels.any { label.contains(it) } && value.isNotBlank() -> {
                        password = value
                    }
                    else -> unlabeledValues.add(line)
                }
            } else {
                unlabeledValues.add(line)
            }
        }

        // Process unlabeled lines (e.g., 3 WhatsApp lines: host, email/username, password)
        val remaining = mutableListOf<String>()
        for (item in unlabeledValues) {
            when {
                host == null && looksLikeHostToken(item) -> {
                    val parsed = normalizeHostInput(item)
                    host = parsed.host
                    if (parsed.explicitPort != null) port = parsed.explicitPort
                    if (parsed.inferredHttps != null) useHttps = parsed.inferredHttps
                    if (parsed.rawPath != null) path = parsed.rawPath
                }
                username == null && EMAIL_REGEX.matches(item) -> {
                    username = item
                }
                else -> remaining.add(item)
            }
        }

        // Assign remaining lines in natural order (username before password)
        for (item in remaining) {
            when {
                username == null -> username = item
                password == null -> password = item
            }
        }

        // If user pasted 2 items where first was host and second was a single token, treat second as username if needed
        if (host == null && username == null && password == null) return null

        return SmartPastedCredentials(
            host = host,
            port = port,
            useHttps = useHttps,
            path = path,
            username = username,
            password = password
        )
    }
}
