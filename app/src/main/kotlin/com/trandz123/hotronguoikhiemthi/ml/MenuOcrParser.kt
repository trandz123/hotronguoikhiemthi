package com.trandz123.hotronguoikhiemthi.ml

import com.google.mlkit.vision.text.Text

/**
 * 1 muc tren menu: ten mon + gia (neu phat hien duoc).
 *
 *  - [rawText]: noi dung doc tu menu (raw, dung de doc lai nguyen ban neu can)
 *  - [name]: ten mon da clean (bo so gia khoi text)
 *  - [priceVnd]: gia da parse ra VND nguyen (null neu khong tim ra)
 */
data class MenuItem(
    val rawText: String,
    val name: String,
    val priceVnd: Long?,
) {
    val hasPrice: Boolean get() = priceVnd != null
}

/**
 * Parser menu tu output ML Kit. Quy trinh:
 *  1. Lay tat ca Text.Line, sort theo y-center
 *  2. Gom line co y-center gan nhau (< 30% chieu cao line) thanh 1 logical row
 *  3. Trong moi row, tach phan "ten mon" va "gia" qua regex
 *
 * Edge case:
 *  - Menu nhieu cot: ML Kit thuong tra line theo thu tu tu trai-phai, tren-xuong.
 *    Parser nay khong tach cot tu dong — du thu tu doc tu nhien neu cot khong sai too much.
 *  - Mon khong co gia: tra null cho [priceVnd], van them vao danh sach.
 */
object MenuOcrParser {

    /**
     * Regex gia VND:
     *  - 1-3 chu so dau, sau co the co phan ngan 3 chu so cach boi `.`, `,` hoac space
     *  - Optional don vi: k, nghin, đong, đ, ₫, VND, vnd (case-insensitive)
     *  - Vi du match: "50.000", "50 000", "50,000", "50k", "200 nghìn", "200.000 đ"
     */
    private val PRICE_REGEX = Regex(
        pattern = """(\d{1,3}(?:[.,\s]\d{3})+|\d{1,4})\s*(k|nghìn|nghin|đồng|dong|đ|d|₫|VND|vnd|VNĐ)?""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parse(text: Text): List<MenuItem> {
        val lines = text.textBlocks.flatMap { it.lines }
        if (lines.isEmpty()) return emptyList()

        // Sort theo y-center
        val sorted = lines.sortedBy { it.boundingBox?.centerY() ?: 0 }
        // Gom thanh logical rows
        val rows = mutableListOf<MutableList<Text.Line>>()
        var currentRow = mutableListOf<Text.Line>()
        var lastY = -1f
        var avgHeight = sorted.firstOrNull()?.boundingBox?.height() ?: 30

        for (line in sorted) {
            val box = line.boundingBox ?: continue
            val cy = box.centerY().toFloat()
            if (lastY < 0 || kotlin.math.abs(cy - lastY) < avgHeight * 0.5f) {
                currentRow.add(line)
            } else {
                if (currentRow.isNotEmpty()) rows.add(currentRow)
                currentRow = mutableListOf(line)
            }
            lastY = cy
            avgHeight = (avgHeight + box.height()) / 2
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        return rows.mapNotNull { row -> parseRow(row) }
    }

    private fun parseRow(lines: List<Text.Line>): MenuItem? {
        // Ghep line trong cung row tu trai sang phai
        val sortedLeft = lines.sortedBy { it.boundingBox?.left ?: 0 }
        val rawText = sortedLeft.joinToString(" ") { it.text }.trim()
        if (rawText.isBlank()) return null

        val priceMatch = findBestPriceMatch(rawText)
        return if (priceMatch != null) {
            val name = (rawText.substring(0, priceMatch.range.first) +
                rawText.substring(priceMatch.range.last + 1)).cleanName()
            MenuItem(
                rawText = rawText,
                name = name.ifBlank { rawText },
                priceVnd = priceMatch.priceVnd,
            )
        } else {
            MenuItem(rawText = rawText, name = rawText, priceVnd = null)
        }
    }

    private fun String.cleanName(): String =
        trim().trim('-', ':', '.', ',', ' ', '\t').trim()

    private data class PriceMatch(val range: IntRange, val priceVnd: Long)

    private fun findBestPriceMatch(text: String): PriceMatch? {
        // Tim TAT CA match. Chon match co gia tri "tien" hop ly nhat:
        //  - Co don vi (k/nghìn/đồng) → uu tien
        //  - Gia tri trong khoang 1_000..10_000_000 VND
        val candidates = PRICE_REGEX.findAll(text).mapNotNull { match ->
            val numStr = match.groupValues[1]
            val unit = match.groupValues[2].lowercase()
            val parsed = parseAmount(numStr, unit) ?: return@mapNotNull null
            if (parsed !in 1_000L..10_000_000L) return@mapNotNull null
            Triple(match.range, parsed, unit.isNotEmpty())
        }.toList()

        if (candidates.isEmpty()) return null
        // Uu tien co unit, sau do gia tri lon nhat (gia thuong la token cuoi cung)
        val best = candidates.maxByOrNull { (_, _, hasUnit) -> if (hasUnit) 1 else 0 }
            ?: candidates.last()
        return PriceMatch(best.first, best.second)
    }

    private fun parseAmount(numStr: String, unit: String): Long? {
        val cleaned = numStr.replace(Regex("[.,\\s]"), "")
        val n = cleaned.toLongOrNull() ?: return null
        return when (unit) {
            "k", "nghìn", "nghin" -> n * 1_000
            else -> n
        }
    }
}
