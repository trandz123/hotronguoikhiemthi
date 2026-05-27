package com.trandz123.hotronguoikhiemthi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NumberToVietnameseTest {

    // 9 menh gia tien VND - test case bat buoc pass cho de tai
    @Test fun `9 banknote denominations are read correctly`() {
        assertEquals("một nghìn", 1_000L.toVietnameseWords())
        assertEquals("hai nghìn", 2_000L.toVietnameseWords())
        assertEquals("năm nghìn", 5_000L.toVietnameseWords())
        assertEquals("mười nghìn", 10_000L.toVietnameseWords())
        assertEquals("hai mươi nghìn", 20_000L.toVietnameseWords())
        assertEquals("năm mươi nghìn", 50_000L.toVietnameseWords())
        assertEquals("một trăm nghìn", 100_000L.toVietnameseWords())
        assertEquals("hai trăm nghìn", 200_000L.toVietnameseWords())
        assertEquals("năm trăm nghìn", 500_000L.toVietnameseWords())
    }

    @Test fun `toVietnameseMoney appends dong and capitalizes`() {
        assertEquals("Hai trăm nghìn đồng", 200_000L.toVietnameseMoney())
        assertEquals("Năm trăm nghìn đồng", 500_000L.toVietnameseMoney())
        assertEquals("Một nghìn đồng", 1_000L.toVietnameseMoney())
    }

    // Edge case: hang don vi
    @Test fun `digits 0-9`() {
        assertEquals("không", 0L.toVietnameseWords())
        assertEquals("một", 1L.toVietnameseWords())
        assertEquals("chín", 9L.toVietnameseWords())
    }

    @Test fun `10-19 use muoi`() {
        assertEquals("mười", 10L.toVietnameseWords())
        assertEquals("mười một", 11L.toVietnameseWords())
        assertEquals("mười lăm", 15L.toVietnameseWords())  // lam khong phai nam
        assertEquals("mười chín", 19L.toVietnameseWords())
    }

    @Test fun `20-99 use muoi mot lam`() {
        assertEquals("hai mươi", 20L.toVietnameseWords())
        assertEquals("hai mươi mốt", 21L.toVietnameseWords())  // mot khong phai mo
        assertEquals("hai mươi lăm", 25L.toVietnameseWords())  // lam khong phai nam
        assertEquals("ba mươi mốt", 31L.toVietnameseWords())
        assertEquals("chín mươi chín", 99L.toVietnameseWords())
    }

    @Test fun `100-999 with linh`() {
        assertEquals("một trăm", 100L.toVietnameseWords())
        assertEquals("một trăm linh một", 101L.toVietnameseWords())   // linh khi hang chuc=0
        assertEquals("một trăm linh năm", 105L.toVietnameseWords())   // 5 lan dau van la "nam"
        assertEquals("một trăm mười", 110L.toVietnameseWords())
        assertEquals("một trăm mười lăm", 115L.toVietnameseWords())
        assertEquals("hai trăm hai mươi mốt", 221L.toVietnameseWords())
        assertEquals("chín trăm chín mươi chín", 999L.toVietnameseWords())
    }

    @Test fun `thousands with leading zero groups`() {
        assertEquals("một nghìn", 1_000L.toVietnameseWords())
        assertEquals("một nghìn không trăm linh một", 1_001L.toVietnameseWords())
        assertEquals("một nghìn không trăm mười", 1_010L.toVietnameseWords())
        assertEquals("mười nghìn không trăm linh một", 10_001L.toVietnameseWords())
        assertEquals("hai mươi mốt nghìn", 21_000L.toVietnameseWords())
    }

    @Test fun `millions and tỷ`() {
        assertEquals("một triệu", 1_000_000L.toVietnameseWords())
        assertEquals("một triệu năm trăm nghìn", 1_500_000L.toVietnameseWords())
        assertEquals(
            "một triệu không trăm linh một",
            1_000_001L.toVietnameseWords()
        )
        assertEquals("một tỷ", 1_000_000_000L.toVietnameseWords())
        assertEquals(
            "một tỷ năm trăm triệu",
            1_500_000_000L.toVietnameseWords()
        )
    }

    // Use case "dem tien" - tong cua nhieu to
    @Test fun `realistic counting totals`() {
        // 3 to 200k + 1 to 50k = 650_000
        assertEquals("Sáu trăm năm mươi nghìn đồng", 650_000L.toVietnameseMoney())
        // 9 to 500k = 4_500_000
        assertEquals("Bốn triệu năm trăm nghìn đồng", 4_500_000L.toVietnameseMoney())
        // 2 to 500k + 1 to 200k + 1 to 50k + 1 to 5k = 1_255_000
        assertEquals(
            "Một triệu hai trăm năm mươi lăm nghìn đồng",
            1_255_000L.toVietnameseMoney()
        )
    }

    @Test fun `zero edge case`() {
        assertEquals("không", 0L.toVietnameseWords())
    }

    @Test fun `negative numbers throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            (-1L).toVietnameseWords()
        }
    }

    @Test fun `numbers above 999 ti throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            1_000_000_000_000L.toVietnameseWords()
        }
    }

    @Test fun `Int extension delegates to Long`() {
        assertEquals("hai trăm nghìn", 200_000.toVietnameseWords())
        assertEquals("Hai trăm nghìn đồng", 200_000.toVietnameseMoney())
    }
}
