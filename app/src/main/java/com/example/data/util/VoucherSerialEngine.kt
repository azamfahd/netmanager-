package com.example.data.util

import com.example.data.model.HotspotCard
import java.util.Locale

enum class SerialCorner(val labelAr: String) {
    TOP_START("أعلى اليمين"),
    TOP_END("أعلى اليسار"),
    BOTTOM_START("أسفل اليمين"),
    BOTTOM_END("أسفل اليسار")
}

data class SerialCardEntry(
    val card: HotspotCard,
    val extractedSerial: Long?,
    val effectiveSerial: Long,
    val formattedSerial: String,
    val sortTimestamp: Long,
    val sortIdOrder: Long
)

data class StockSliceSummary(
    val totalInScope: Int,
    val totalDisabledStock: Int,
    val highestUsedOrActiveSerial: Long?,
    val lastRecordedSerial: Long?,
    val effectiveLastSerial: Long?,
    val nextAvailableSerial: Long?,
    val selectedEntries: List<SerialCardEntry>,
    val startSerialLabel: String,
    val endSerialLabel: String,
    val exceedsAvailableStock: Boolean
) {
    val selectedCards: List<HotspotCard>
        get() = selectedEntries.map { it.card }
}

data class StockActivationResult(
    val totalRequested: Int,
    val activatedCards: List<HotspotCard>,
    val alreadyActiveCards: List<HotspotCard> = emptyList(),
    val failedCards: List<HotspotCard> = emptyList(),
    val errorMessages: List<String> = emptyList(),
    val highestActivatedSerial: Long? = null
) {
    val succeededCount: Int get() = activatedCards.size + alreadyActiveCards.size
    val isCompleteSuccess: Boolean get() = failedCards.isEmpty()
}

object VoucherSerialEngine {

    private val COMMENT_SERIAL_REGEX = Regex(
        """(?:^|[\s|,;(\[])(?:seq|sn|s/n|serial|تسلسل|رقم)\s*[:=#-]?\s*(\d{1,12})""",
        RegexOption.IGNORE_CASE
    )
    private val HASH_SERIAL_REGEX = Regex("""(?:^|[\s|,;(\[])#(\d{1,12})(?:\b|$)""")
    private val TRAILING_DIGITS_REGEX = Regex("""(\d{1,14})$""")
    private val ANY_DIGITS_REGEX = Regex("""\d{1,14}""")

    /**
     * Smart serial number extraction with priority:
     * 1. Explicit serial tags in card comment (e.g. `seq:0049`, `S/N:49`, `#49`, `تسلسل:49`)
     * 2. Numeric or trailing numeric portion inside card name/code (e.g. `VIP0049`, `10049`)
     * 3. Returns null so caller falls back cleanly to creation timestamp & internal ID order
     */
    fun extractCardSerial(card: HotspotCard): Long? {
        val commentNorm = FinanceAnalyticsEngine.normalizeDigits(card.comment)
        if (commentNorm.isNotBlank()) {
            COMMENT_SERIAL_REGEX.find(commentNorm)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
                return it
            }
            HASH_SERIAL_REGEX.find(commentNorm)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
                return it
            }
        }

        val nameNorm = FinanceAnalyticsEngine.normalizeDigits(card.name).trim()
        if (nameNorm.isNotBlank()) {
            if (nameNorm.all { it.isDigit() }) {
                nameNorm.toLongOrNull()?.let { return it }
            }
            TRAILING_DIGITS_REGEX.find(nameNorm)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
                return it
            }
            ANY_DIGITS_REGEX.findAll(nameNorm)
                .mapNotNull { it.value.toLongOrNull() }
                .maxByOrNull { it.toString().length }
                ?.let { return it }
        }

        return null
    }

    /**
     * Parses MikroTik internal IDs such as `*1A3` or `um_*20` into stable numeric order.
     */
    fun parseRouterIdOrder(cardId: String): Long {
        val trimmed = cardId.trim()
            .removePrefix("um_")
            .removePrefix("tum_")
            .removePrefix("*")
        trimmed.toLongOrNull(16)?.let { return it }
        val digits = trimmed.filter { it.isDigit() }
        return digits.toLongOrNull() ?: Long.MAX_VALUE
    }

    fun formatSerialDigits(serial: Long, padWidth: Int = 4): String {
        val width = padWidth.coerceIn(1, 10)
        return serial.toString().padStart(width, '0')
    }

    /**
     * Orders cards sequentially by extracted serial (ascending), then creation timestamp (oldest first),
     * then RouterOS internal ID, and finally card name.
     */
    fun sortCardsSequentially(
        cards: List<HotspotCard>,
        timestampProvider: (HotspotCard) -> Long = { it.createdAtHint }
    ): List<SerialCardEntry> {
        if (cards.isEmpty()) return emptyList()

        data class RawEntry(
            val card: HotspotCard,
            val extracted: Long?,
            val ts: Long,
            val idOrder: Long
        )

        val rawList = cards.map { card ->
            RawEntry(
                card = card,
                extracted = extractCardSerial(card),
                ts = timestampProvider(card),
                idOrder = parseRouterIdOrder(card.id)
            )
        }

        val sortedRaw = rawList.sortedWith(
            compareBy<RawEntry> { it.extracted == null }
                .thenBy { it.extracted ?: Long.MAX_VALUE }
                .thenBy { it.ts }
                .thenBy { it.idOrder }
                .thenBy { it.card.name }
        )

        val maxExplicit = sortedRaw.mapNotNull { it.extracted }.maxOrNull() ?: 0L
        val estimatedMax = maxOf(maxExplicit, sortedRaw.size.toLong())
        val padWidth = maxOf(4, estimatedMax.toString().length)

        var rollingFallbackSerial = if (maxExplicit > 0L) maxExplicit else 0L
        return sortedRaw.mapIndexed { idx, raw ->
            val effectiveSerial = raw.extracted ?: run {
                rollingFallbackSerial++
                rollingFallbackSerial
            }
            val formatted = formatSerialDigits(effectiveSerial, padWidth)
            SerialCardEntry(
                card = raw.card,
                extractedSerial = raw.extracted,
                effectiveSerial = effectiveSerial,
                formattedSerial = formatted,
                sortTimestamp = raw.ts,
                sortIdOrder = raw.idOrder
            )
        }
    }

    /**
     * Analyzes all cards in the selected scope (e.g. a batch, profile, price tier, or full stock),
     * determines the highest active/enabled/recorded serial, and returns the next [requestedCount]
     * consecutive manually disabled stock cards starting right after the last active serial.
     */
    fun analyzeStockAndSelectSlice(
        scopedCards: List<HotspotCard>,
        requestedCount: Int,
        lastSavedSerial: Long? = null,
        timestampProvider: (HotspotCard) -> Long = { it.createdAtHint }
    ): StockSliceSummary {
        val sortedAll = sortCardsSequentially(scopedCards, timestampProvider)
        val disabledEntries = sortedAll.filter { it.card.isManuallyDisabled }

        // Cards in scope that have already been activated or used/expired determine current serial watermark
        val enabledOrUsedEntries = sortedAll.filter { !it.card.isManuallyDisabled }
        val highestUsedOrActive = enabledOrUsedEntries
            .mapNotNull { it.extractedSerial ?: it.effectiveSerial }
            .maxOrNull()

        val effectiveWatermark = listOfNotNull(highestUsedOrActive, lastSavedSerial).maxOrNull()

        val orderedRemaining = if (effectiveWatermark != null) {
            val afterWatermark = disabledEntries.filter { entry ->
                val serial = entry.extractedSerial ?: entry.effectiveSerial
                serial > effectiveWatermark
            }
            // If all remaining disabled stock sits below/at watermark (e.g., an admin re-disabled older cards),
            // serve those remaining disabled entries in sequence instead of returning an empty slice.
            afterWatermark.ifEmpty { disabledEntries }
        } else {
            disabledEntries
        }

        val safeCount = requestedCount.coerceAtLeast(0)
        val selected = if (safeCount == 0) emptyList() else orderedRemaining.take(safeCount)
        val nextSerial = orderedRemaining.firstOrNull()?.effectiveSerial
        val startLabel = selected.firstOrNull()?.formattedSerial.orEmpty()
        val endLabel = selected.lastOrNull()?.formattedSerial.orEmpty()

        return StockSliceSummary(
            totalInScope = scopedCards.size,
            totalDisabledStock = disabledEntries.size,
            highestUsedOrActiveSerial = highestUsedOrActive,
            lastRecordedSerial = lastSavedSerial,
            effectiveLastSerial = effectiveWatermark,
            nextAvailableSerial = nextSerial,
            selectedEntries = selected,
            startSerialLabel = startLabel,
            endSerialLabel = endLabel,
            exceedsAvailableStock = safeCount > orderedRemaining.size && orderedRemaining.isNotEmpty()
        )
    }

    /**
     * Builds a quick lookup map from card.name & card.id -> formatted serial string for printing.
     */
    fun buildSerialMap(
        cards: List<HotspotCard>,
        timestampProvider: (HotspotCard) -> Long = { it.createdAtHint }
    ): Map<String, String> {
        val entries = sortCardsSequentially(cards, timestampProvider)
        val map = LinkedHashMap<String, String>(entries.size * 2)
        entries.forEach { entry ->
            map[entry.card.name] = entry.formattedSerial
            if (entry.card.id.isNotBlank()) {
                map[entry.card.id] = entry.formattedSerial
            }
        }
        return map
    }

    /**
     * Formats an appended `seq:XXXX` marker cleanly onto a batch comment without duplicating markers.
     */
    fun appendSerialToComment(baseComment: String, serialNumber: Long, padWidth: Int = 4): String {
        val cleanBase = baseComment.trim()
        val tag = "seq:${formatSerialDigits(serialNumber, padWidth)}"
        return if (cleanBase.isBlank()) tag else "$cleanBase | $tag"
    }

    /**
     * Suggests the next starting serial number for generating a new sequential stock batch.
     */
    fun suggestNextStartSerial(existingCards: List<HotspotCard>, savedLastSerial: Long? = null): Long {
        val maxExisting = existingCards.mapNotNull { extractCardSerial(it) }.maxOrNull() ?: 0L
        return maxOf(maxExisting, savedLastSerial ?: 0L) + 1L
    }
}
