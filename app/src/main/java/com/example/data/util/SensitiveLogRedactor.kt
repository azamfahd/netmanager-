package com.example.data.util

/** Never persist voucher or router credentials in operator-visible/shareable event logs. */
object SensitiveLogRedactor {
    private val jsonPassword = Regex("""(?i)("(?:password|pass)"\s*:\s*")[^"]*(")""")
    private val labeledPassword = Regex("""(?i)(\bpassword\s*[=:]\s*|=password=|كلمة المرور\s*:\s*)[^\s,|;}]+""")
    private val basicAuthorization = Regex("""(?i)(\bBasic\s+)[A-Za-z0-9+/=]+""")

    fun redact(value: String): String = value
        .replace(jsonPassword, "${'$'}1[REDACTED]${'$'}2")
        .replace(labeledPassword, "${'$'}1[REDACTED]")
        .replace(basicAuthorization, "${'$'}1[REDACTED]")
}
