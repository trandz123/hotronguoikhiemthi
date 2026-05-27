package com.trandz123.hotronguoikhiemthi.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Method

/**
 * Test rieng cho ham parseAmount / findBestPriceMatch (private). Goi qua reflection
 * vi day la phan logic pure-Kotlin khong phu thuoc ML Kit nen test JVM duoc.
 *
 * Test full parse() can ML Kit Text mock — skip o day, day test bang instrumentation
 * khi co thiet bi.
 */
class MenuOcrParserParseAmountTest {

    private val parserClass = MenuOcrParser::class.java
    private val parseAmount: Method = parserClass.getDeclaredMethod(
        "parseAmount", String::class.java, String::class.java
    ).apply { isAccessible = true }

    private fun parse(numStr: String, unit: String): Long? =
        parseAmount.invoke(MenuOcrParser, numStr, unit) as Long?

    @Test fun `parse 50,000 dong`() {
        assertEquals(50_000L, parse("50,000", ""))
    }

    @Test fun `parse 50_000 dong`() {
        assertEquals(50_000L, parse("50.000", ""))
    }

    @Test fun `parse 50 000 dong`() {
        assertEquals(50_000L, parse("50 000", ""))
    }

    @Test fun `parse 50k = 50000`() {
        assertEquals(50_000L, parse("50", "k"))
    }

    @Test fun `parse 200 nghin = 200000`() {
        assertEquals(200_000L, parse("200", "nghìn"))
        assertEquals(200_000L, parse("200", "nghin"))
    }

    @Test fun `parse 1_500_000`() {
        assertEquals(1_500_000L, parse("1.500.000", ""))
    }

    @Test fun `parse invalid returns null`() {
        assertNull(parse("abc", ""))
    }
}
