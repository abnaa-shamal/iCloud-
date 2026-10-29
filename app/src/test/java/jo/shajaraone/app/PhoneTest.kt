package jo.shajaraone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phone formats accepted for riders and captains: 077 / 078 / 079, or the same with 00962 / 962 / without the leading zero. */
class PhoneTest {
    @Test fun acceptsLocalFormats() {
        listOf("0791234567", "0781234567", "0771234567").forEach { assertTrue(it, isValidPhone(it)) }
    }

    @Test fun acceptsInternationalFormats() {
        listOf("00962791234567", "00962781234567", "00962771234567", "962791234567", "791234567").forEach {
            assertTrue(it, isValidPhone(it))
        }
    }

    @Test fun normalizesToLocalFormat() {
        assertEquals("0791234567", normalizePhone("00962791234567"))
        assertEquals("0781234567", normalizePhone("+962 78 123 4567"))
        assertEquals("0771234567", normalizePhone("771234567"))
    }

    @Test fun rejectsWrongNumbers() {
        listOf("", "0761234567", "079123456", "07912345678", "abc", "0091791234567").forEach { assertFalse(it, isValidPhone(it)) }
    }
}
