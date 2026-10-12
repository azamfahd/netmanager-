package com.example.data.util

import com.example.data.model.DeviceCategory
import com.example.data.model.DeviceIdentification

object DeviceDetector {

    /**
     * Determines device brand, model, category and OS based on MAC address and optional hostname / username
     */
    fun detectDevice(macAddress: String, hostname: String = "", username: String = ""): DeviceIdentification {
        val cleanMac = macAddress.uppercase().replace("-", ":").trim()
        val prefix3 = if (cleanMac.length >= 8) cleanMac.substring(0, 8) else ""

        // Check Apple
        if (isAppleMac(prefix3) || hostname.contains("iPhone", ignoreCase = true) || hostname.contains("iPad", ignoreCase = true) || hostname.contains("Apple", ignoreCase = true)) {
            val model = if (hostname.isNotBlank()) hostname else "Apple iPhone"
            return DeviceIdentification(
                brand = "Apple",
                modelName = model,
                osName = "iOS",
                category = DeviceCategory.IPHONE_IPAD,
                macVendor = "Apple Inc."
            )
        }

        // Check Samsung
        if (isSamsungMac(prefix3) || hostname.contains("Galaxy", ignoreCase = true) || hostname.contains("Samsung", ignoreCase = true)) {
            val model = if (hostname.isNotBlank()) hostname else "Samsung Galaxy"
            return DeviceIdentification(
                brand = "Samsung",
                modelName = model,
                osName = "Android (One UI)",
                category = DeviceCategory.ANDROID_PHONE,
                macVendor = "Samsung Electronics"
            )
        }

        // Check Xiaomi / Redmi / Poco
        if (isXiaomiMac(prefix3) || hostname.contains("Redmi", ignoreCase = true) || hostname.contains("Xiaomi", ignoreCase = true) || hostname.contains("POCO", ignoreCase = true)) {
            val model = if (hostname.isNotBlank()) hostname else "Xiaomi Redmi"
            return DeviceIdentification(
                brand = "Xiaomi",
                modelName = model,
                osName = "Android (HyperOS / MIUI)",
                category = DeviceCategory.ANDROID_PHONE,
                macVendor = "Xiaomi Communications"
            )
        }

        // Check Huawei / Honor
        if (isHuaweiMac(prefix3) || hostname.contains("HUAWEI", ignoreCase = true) || hostname.contains("HONOR", ignoreCase = true)) {
            val model = if (hostname.isNotBlank()) hostname else "Huawei Phone"
            return DeviceIdentification(
                brand = "Huawei",
                modelName = model,
                osName = "HarmonyOS / Android",
                category = DeviceCategory.ANDROID_PHONE,
                macVendor = "Huawei Technologies"
            )
        }

        // Check Transsion (Infinix / Tecno / itel)
        if (isTranssionMac(prefix3) || hostname.contains("Infinix", ignoreCase = true) || hostname.contains("Tecno", ignoreCase = true) || hostname.contains("itel", ignoreCase = true)) {
            val brand = when {
                hostname.contains("Infinix", ignoreCase = true) -> "Infinix"
                hostname.contains("Tecno", ignoreCase = true) -> "Tecno"
                else -> "Infinix / Tecno"
            }
            return DeviceIdentification(
                brand = brand,
                modelName = if (hostname.isNotBlank()) hostname else "$brand Smart Phone",
                osName = "Android (XOS / HiOS)",
                category = DeviceCategory.ANDROID_PHONE,
                macVendor = "Transsion Holdings"
            )
        }

        // Check Oppo / Realme / OnePlus
        if (isOppoMac(prefix3) || hostname.contains("OPPO", ignoreCase = true) || hostname.contains("Realme", ignoreCase = true) || hostname.contains("OnePlus", ignoreCase = true)) {
            val brand = when {
                hostname.contains("Realme", ignoreCase = true) -> "Realme"
                hostname.contains("OnePlus", ignoreCase = true) -> "OnePlus"
                else -> "Oppo"
            }
            return DeviceIdentification(
                brand = brand,
                modelName = if (hostname.isNotBlank()) hostname else "$brand Phone",
                osName = "Android (ColorOS / RealmeUI)",
                category = DeviceCategory.ANDROID_PHONE,
                macVendor = "Oppo Electronics"
            )
        }

        // Check Windows / PC / Laptop
        if (isWindowsPcMac(prefix3) || hostname.contains("DESKTOP", ignoreCase = true) || hostname.contains("LAPTOP", ignoreCase = true) || hostname.contains("WIN", ignoreCase = true)) {
            val model = if (hostname.isNotBlank()) hostname else "Windows PC / Laptop"
            return DeviceIdentification(
                brand = "Microsoft / Intel",
                modelName = model,
                osName = "Windows 11 / 10",
                category = DeviceCategory.LAPTOP_PC,
                macVendor = "Intel / Realtek"
            )
        }

        // Check MikroTik / Router / AP
        if (cleanMac.startsWith("48:8F:5A") || cleanMac.startsWith("6C:3B:6B") || cleanMac.startsWith("CC:2D:E0") || hostname.contains("MikroTik", ignoreCase = true)) {
            return DeviceIdentification(
                brand = "MikroTik",
                modelName = "RouterBOARD / Access Point",
                osName = "RouterOS",
                category = DeviceCategory.ROUTER_AP,
                macVendor = "MikroTikls SIA"
            )
        }

        // Check TP-Link
        if (cleanMac.startsWith("00:1D:0F") || cleanMac.startsWith("14:CC:20") || cleanMac.startsWith("50:C7:BF") || cleanMac.startsWith("60:32:B1") || hostname.contains("TP-Link", ignoreCase = true)) {
            return DeviceIdentification(
                brand = "TP-Link",
                modelName = "TP-Link Wireless Device",
                osName = "Embedded OS",
                category = DeviceCategory.ROUTER_AP,
                macVendor = "TP-Link Corporation"
            )
        }

        // Handle randomized / private MAC addresses (bit 1 of first byte is set: 2, 6, A, E)
        val isRandomizedMac = if (cleanMac.length >= 2) {
            val secondChar = cleanMac[1].uppercaseChar()
            secondChar == '2' || secondChar == '6' || secondChar == 'A' || secondChar == 'E'
        } else false

        if (isRandomizedMac) {
            val model = if (hostname.isNotBlank()) hostname else "جهاز خاص (Private MAC)"
            return DeviceIdentification(
                brand = "جهاز ذكي",
                modelName = model,
                osName = "هاتف ذكي / جهاز محمول",
                category = DeviceCategory.ANDROID_PHONE,
                macVendor = "عنوان MAC عشوائي للحماية"
            )
        }

        // Real genuine fallback without inventing fictional device brands
        val realModelName = if (hostname.isNotBlank()) hostname else "جهاز متصل ($cleanMac)"
        return DeviceIdentification(
            brand = "جهاز شبكة",
            modelName = realModelName,
            osName = "نظام تشغيل الجهاز",
            category = DeviceCategory.SMART_DEVICE,
            macVendor = if (cleanMac.isNotBlank()) "معرّف MAC: $cleanMac" else "جهاز متصل"
        )
    }

    private fun isAppleMac(prefix: String): Boolean {
        val applePrefixes = setOf(
            "00:03:93", "00:05:02", "00:0A:95", "00:0D:93", "00:10:FA", "00:11:24", "00:14:51", "00:16:CB",
            "00:17:F2", "00:19:E3", "00:1B:63", "00:1C:B3", "00:1D:4F", "00:1E:52", "00:1F:5B", "00:1F:F3",
            "00:21:E9", "00:22:41", "00:23:12", "00:23:32", "00:23:6C", "00:23:DF", "00:24:36", "00:25:00",
            "28:6A:B8", "3C:D0:F8", "40:A6:D9", "44:2A:60", "48:D7:05", "50:EA:D6", "54:26:96", "58:55:CA",
            "64:20:0C", "68:AB:1E", "70:11:24", "78:31:C1", "80:E6:50", "84:38:35", "88:66:5A", "8C:85:90",
            "90:B2:1F", "94:94:26", "98:01:A7", "9C:20:7B", "A4:C3:61", "AC:BC:32", "B0:34:95", "BC:D1:D3",
            "C8:6F:1D", "D4:90:9C", "DC:2B:61", "E4:CE:8F", "EC:35:86", "F0:18:98", "F4:37:B7", "FC:FC:48"
        )
        return applePrefixes.contains(prefix)
    }

    private fun isSamsungMac(prefix: String): Boolean {
        val samsungPrefixes = setOf(
            "00:07:AB", "00:12:47", "00:15:B9", "00:16:6C", "00:17:C9", "00:1A:8A", "00:1B:98", "00:1C:43",
            "00:1D:25", "00:1E:7D", "00:1F:CC", "00:21:19", "00:23:D6", "00:24:90", "00:26:37", "34:23:BA",
            "50:B7:C3", "5C:E8:EB", "78:47:1D", "7C:91:22", "84:25:DB", "88:32:9B", "8C:77:12", "90:F1:AA",
            "98:52:B1", "A8:7C:01", "A8:9C:ED", "B4:79:A7", "BC:72:B9", "C4:42:02", "D0:17:6A", "DC:71:44",
            "E4:7C:F9", "F0:72:EA", "F8:04:2E"
        )
        return samsungPrefixes.contains(prefix)
    }

    private fun isXiaomiMac(prefix: String): Boolean {
        val prefixes = setOf(
            "18:65:90", "20:47:DA", "34:80:B3", "50:8F:4C", "64:CC:2E", "7C:1D:D9", "8C:BE:BE",
            "9C:99:A0", "A4:C4:94", "C4:0B:D3", "D4:97:0B", "E4:46:DA", "F4:F5:DB"
        )
        return prefixes.contains(prefix)
    }

    private fun isHuaweiMac(prefix: String): Boolean {
        val prefixes = setOf(
            "00:18:82", "00:1E:10", "00:25:9E", "20:08:89", "3C:DF:BD", "40:4D:8E", "48:46:FB",
            "70:54:F5", "88:53:95", "A4:99:9B", "C4:F0:81", "DC:08:0F", "E0:19:54", "F8:3D:FF"
        )
        return prefixes.contains(prefix)
    }

    private fun isTranssionMac(prefix: String): Boolean {
        val prefixes = setOf(
            "00:EC:0A", "2C:22:8B", "38:D2:69", "60:21:C0", "70:8C:8B", "84:2C:80", "AC:E3:42", "C4:6E:7B"
        )
        return prefixes.contains(prefix)
    }

    private fun isOppoMac(prefix: String): Boolean {
        val prefixes = setOf(
            "24:4B:FE", "50:65:83", "68:7A:B4", "70:89:CC", "94:77:2B", "A0:C9:A0", "C4:9F:4C", "D4:61:9D", "F4:60:E2"
        )
        return prefixes.contains(prefix)
    }

    private fun isWindowsPcMac(prefix: String): Boolean {
        val prefixes = setOf(
            "00:13:E8", "00:15:00", "00:16:76", "00:1B:21", "00:1C:C0", "00:1E:67", "00:21:6A", "00:23:14", "00:24:D7", "00:26:C7", "B4:2E:99", "F8:63:3F"
        )
        return prefixes.contains(prefix)
    }
}
