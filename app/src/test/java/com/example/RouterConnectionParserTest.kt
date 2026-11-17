package com.example

import com.example.data.model.RouterConnectionConfig
import com.example.data.network.RouterConnectionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterConnectionParserTest {

    @Test
    fun parseSmartCredentialsPaste_extractsWhatsappThreeLineCredentialsWithRtlMarks() {
        val whatsappPaste = """
            [١٦/‏١١ ٨:٣٦ م] محمد توفيق: aslamnet-taz-ac.apluswifi.com
            [١٦/‏١١ ٨:٣٧ م] محمد توفيق: ahmed771233700@gmail.com
            [١٦/‏١١ ٨:٣٧ م] محمد توفيق: eslam771233700
        """.trimIndent()

        val parsed = RouterConnectionParser.parseSmartCredentialsPaste(whatsappPaste)

        assertNotNull(parsed)
        assertEquals("aslamnet-taz-ac.apluswifi.com", parsed?.host)
        assertEquals("ahmed771233700@gmail.com", parsed?.username)
        assertEquals("eslam771233700", parsed?.password)
    }

    @Test
    fun normalizeHostInput_handlesHttpsUrlWithPath() {
        val parsed = RouterConnectionParser.normalizeHostInput("https://aslamnet-taz-ac.apluswifi.com/login?next=/admin")

        assertEquals("aslamnet-taz-ac.apluswifi.com", parsed.host)
        assertEquals(443, parsed.explicitPort)
        assertEquals(true, parsed.inferredHttps)
        assertEquals("https", parsed.rawScheme)
        assertTrue(parsed.isDomain)
        assertFalse(parsed.isPrivateIp)
    }

    @Test
    fun normalizeHostInput_handlesHttpUrlAndCustomPort() {
        val parsed = RouterConnectionParser.normalizeHostInput("http://192.168.88.1:8080/rest/system")

        assertEquals("192.168.88.1", parsed.host)
        assertEquals(8080, parsed.explicitPort)
        assertEquals(false, parsed.inferredHttps)
        assertFalse(parsed.isDomain)
        assertTrue(parsed.isPrivateIp)
    }

    @Test
    fun normalizeHostInput_handlesDomainAndApiSslSchemeOrPort() {
        val fromPort = RouterConnectionParser.normalizeHostInput("aslamnet-taz-ac.apluswifi.com:8729")
        assertEquals("aslamnet-taz-ac.apluswifi.com", fromPort.host)
        assertEquals(8729, fromPort.explicitPort)
        assertEquals(true, fromPort.inferredHttps)
        assertTrue(fromPort.isDomain)

        val fromScheme = RouterConnectionParser.normalizeHostInput("api-ssl://router.mynetname.net")
        assertEquals("router.mynetname.net", fromScheme.host)
        assertEquals(8729, fromScheme.explicitPort)
        assertEquals(true, fromScheme.inferredHttps)
    }

    @Test
    fun normalizeHostInput_handlesIpv4WithPortAndIpv6BracketPort() {
        val ipv4Port = RouterConnectionParser.normalizeHostInput("192.168.88.1:8728")
        assertEquals("192.168.88.1", ipv4Port.host)
        assertEquals(8728, ipv4Port.explicitPort)
        assertTrue(ipv4Port.isPrivateIp)

        val ipv6Port = RouterConnectionParser.normalizeHostInput("[2001:db8::1]:8728")
        assertEquals("2001:db8::1", ipv6Port.host)
        assertEquals(8728, ipv6Port.explicitPort)
    }

    @Test
    fun parseSmartCredentialsPaste_handlesUnlabeledHostWithPort() {
        val paste = """
            192.168.88.1:8728
            admin
            secretPass123
        """.trimIndent()

        val parsed = RouterConnectionParser.parseSmartCredentialsPaste(paste)
        assertNotNull(parsed)
        assertEquals("192.168.88.1", parsed?.host)
        assertEquals(8728, parsed?.port)
        assertEquals("admin", parsed?.username)
        assertEquals("secretPass123", parsed?.password)
    }

    @Test
    fun parseSmartCredentialsPaste_handlesArabicAndEnglishLabeledLines() {
        val paste = """
            السيرفر: https://my-ac.apluswifi.com
            البريد: operator@network.net
            كلمة المرور: pass#9988
        """.trimIndent()

        val parsed = RouterConnectionParser.parseSmartCredentialsPaste(paste)
        assertNotNull(parsed)
        assertEquals("my-ac.apluswifi.com", parsed?.host)
        assertEquals(443, parsed?.port)
        assertEquals(true, parsed?.useHttps)
        assertEquals("operator@network.net", parsed?.username)
        assertEquals("pass#9988", parsed?.password)
    }

    @Test
    fun parseSmartCredentialsPaste_returnsNullOnBlankInput_andDefaultConfigEnablesAutoDetect() {
        assertNull(RouterConnectionParser.parseSmartCredentialsPaste("   \n  \n "))

        val config = RouterConnectionConfig()
        assertTrue(config.autoDetectProtocol)
    }

    @Test
    fun normalizeHostInput_and_smartPaste_preserveDeepPortalPath() {
        val normalized = RouterConnectionParser.normalizeHostInput("https://portal.example-wisp.net/cards/cards")
        assertEquals("portal.example-wisp.net", normalized.host)
        assertEquals(443, normalized.explicitPort)
        assertEquals(true, normalized.inferredHttps)
        assertEquals("/cards/cards", normalized.rawPath)

        val paste = """
            https://portal.example-wisp.net/cards/cards?tab=active
            owner@example-wisp.net
            pass123456
        """.trimIndent()
        val smart = RouterConnectionParser.parseSmartCredentialsPaste(paste)
        assertNotNull(smart)
        assertEquals("portal.example-wisp.net", smart?.host)
        assertEquals("/cards/cards?tab=active", smart?.path)
        assertEquals("owner@example-wisp.net", smart?.username)
        assertEquals("pass123456", smart?.password)
    }
}
