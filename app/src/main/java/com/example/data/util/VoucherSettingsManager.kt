package com.example.data.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CodeCharType

data class SavedVoucherSettings(
    val hasCustomDefaults: Boolean = false,
    val profile: String = "",
    val price: String = "500 ر.ي",
    val timeLimit: String = "1d",
    val dataLimitMB: String = "2560",
    val isSingleCode: Boolean = true, // Default: Single code / Username only
    val batchCount: String = "20",
    val codeLength: String = "6",
    val charType: CodeCharType = CodeCharType.NUMBERS_ONLY,
    val prefix: String = "",
    val batchComment: String = "دفعة كروت هوتسبوت",
    val posAgentId: Long = 0L
)

class VoucherSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voucher_generation_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): SavedVoucherSettings {
        val hasCustom = prefs.getBoolean("has_custom_defaults", false)
        val charTypeStr = prefs.getString("char_type", CodeCharType.NUMBERS_ONLY.name) ?: CodeCharType.NUMBERS_ONLY.name
        val charType = try {
            CodeCharType.valueOf(charTypeStr)
        } catch (_: Exception) {
            CodeCharType.NUMBERS_ONLY
        }

        return SavedVoucherSettings(
            hasCustomDefaults = hasCustom,
            profile = prefs.getString("profile", "") ?: "",
            price = prefs.getString("price", "500 ر.ي") ?: "500 ر.ي",
            timeLimit = prefs.getString("time_limit", "1d") ?: "1d",
            dataLimitMB = prefs.getString("data_limit_mb", "2560") ?: "2560",
            isSingleCode = prefs.getBoolean("is_single_code", true),
            batchCount = prefs.getString("batch_count", "20") ?: "20",
            codeLength = prefs.getString("code_length", "6") ?: "6",
            charType = charType,
            prefix = prefs.getString("prefix", "") ?: "",
            batchComment = prefs.getString("batch_comment", "دفعة كروت هوتسبوت") ?: "دفعة كروت هوتسبوت",
            posAgentId = prefs.getLong("pos_agent_id", 0L)
        )
    }

    fun saveSettings(settings: SavedVoucherSettings) {
        prefs.edit()
            .putBoolean("has_custom_defaults", true)
            .putString("profile", settings.profile)
            .putString("price", settings.price)
            .putString("time_limit", settings.timeLimit)
            .putString("data_limit_mb", settings.dataLimitMB)
            .putBoolean("is_single_code", settings.isSingleCode)
            .putString("batch_count", settings.batchCount)
            .putString("codeLength", settings.codeLength)
            .putString("char_type", settings.charType.name)
            .putString("prefix", settings.prefix)
            .putString("batch_comment", settings.batchComment)
            .putLong("pos_agent_id", settings.posAgentId)
            .apply()
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
