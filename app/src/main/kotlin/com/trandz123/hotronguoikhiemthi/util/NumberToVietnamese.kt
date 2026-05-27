package com.trandz123.hotronguoikhiemthi.util

/**
 * Doc so → tieng Viet cho TTS. Dung cho module "Doc tien":
 *   200_000.toVietnameseMoney() == "Hai tram nghin dong"
 *
 * Cover edge case:
 *  - 21 = "hai muoi mot" (mot khong phai mo)
 *  - 25 = "hai muoi lam" (lam khong phai nam)
 *  - 101 = "mot tram linh mot"
 *  - 1_000_001 = "mot trieu khong tram linh mot"
 *
 * Khong xu ly so am cho money use case (so am bao loi). Ho tro toi 999 ti.
 */

private val DIGITS = arrayOf(
    "không", "một", "hai", "ba", "bốn",
    "năm", "sáu", "bảy", "tám", "chín",
)

// Don vi tu phai sang trai: don vi, nghin, trieu, ti.
// Quy uoc don gian: tren tỷ se cong gop thanh "X tỷ Y trieu...".
private val GROUP_UNITS = arrayOf("", "nghìn", "triệu", "tỷ")

/**
 * Doc 1 nhom 3 chu so (0..999) thanh tieng Viet.
 *
 * @param forceShowHundred neu true, nhom co hang tram = 0 van phai noi "khong tram"
 *   (dung cho cac nhom KHONG phai nhom leading, vd "mot trieu khong tram linh mot").
 */
private fun readThreeDigits(n: Int, forceShowHundred: Boolean): String {
    require(n in 0..999) { "Group must be 0..999, got $n" }
    if (n == 0) return ""

    val hundreds = n / 100
    val tens = (n / 10) % 10
    val ones = n % 10
    val parts = mutableListOf<String>()

    when {
        hundreds > 0 -> parts.add("${DIGITS[hundreds]} trăm")
        forceShowHundred -> parts.add("không trăm")
    }

    when {
        tens == 0 -> {
            // Co hang tram (hoac forceShow) + co hang don vi → noi "linh"
            if ((hundreds > 0 || forceShowHundred) && ones > 0) parts.add("linh")
        }
        tens == 1 -> parts.add("mười")
        else -> parts.add("${DIGITS[tens]} mươi")
    }

    if (ones > 0) {
        val word = when {
            tens >= 2 && ones == 1 -> "mốt"   // 21 = hai muoi mot
            tens >= 1 && ones == 5 -> "lăm"   // 15, 25,... = muoi/hai muoi lam
            else -> DIGITS[ones]
        }
        parts.add(word)
    }

    return parts.joinToString(" ")
}

/**
 * Chuyen so nguyen duong → tieng Viet. Tra "không" neu n == 0.
 *
 * Vi du:
 *  - 1_000.toVietnameseWords() = "một nghìn"
 *  - 200_000.toVietnameseWords() = "hai trăm nghìn"
 *  - 1_500_000.toVietnameseWords() = "một triệu năm trăm nghìn"
 */
fun Long.toVietnameseWords(): String {
    require(this >= 0) { "Negative number not supported: $this" }
    require(this < 1_000_000_000_000L) { "Number too large (max 999 tỷ): $this" }
    if (this == 0L) return "không"

    // Split thanh cac nhom 3 chu so tu phai sang trai
    val groups = mutableListOf<Int>()
    var remaining = this
    while (remaining > 0) {
        groups.add((remaining % 1000).toInt())
        remaining /= 1000
    }

    // Doc tu nhom co bac cao nhat → bac thap nhat
    val parts = mutableListOf<String>()
    var seenNonZero = false
    for (i in groups.indices.reversed()) {
        val groupValue = groups[i]
        if (groupValue == 0) {
            // Bo qua nhom 000 o giua/dau. Sau khi seenNonZero, mot nhom 0 van phai bo
            // (vd 1_000_500 = "mot trieu nam tram", khong noi "khong nghin").
            continue
        }
        val groupText = readThreeDigits(groupValue, forceShowHundred = seenNonZero)
        val unit = GROUP_UNITS[i]
        parts.add(if (unit.isEmpty()) groupText else "$groupText $unit")
        seenNonZero = true
    }

    return parts.joinToString(" ")
}

/**
 * Convenience: doc so tien VND → "X dong". Dau capitalize cho TTS doc dau cau ro hon.
 *
 * Vi du:
 *  - 200_000.toVietnameseMoney() = "Hai trăm nghìn đồng"
 *  - 1_500_000.toVietnameseMoney() = "Một triệu năm trăm nghìn đồng"
 */
fun Long.toVietnameseMoney(): String {
    val words = toVietnameseWords()
    val capitalized = words.replaceFirstChar { it.uppercaseChar() }
    return "$capitalized đồng"
}

fun Int.toVietnameseWords(): String = toLong().toVietnameseWords()
fun Int.toVietnameseMoney(): String = toLong().toVietnameseMoney()
