package dev.namn.steady_fetch.impl.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantsTest {

    @Test
    fun httpCodeRegex_matchesThreeDigitCodes() {
        val match = Constants.HTTP_CODE_REGEX.find("HTTP 503 upstream unavailable")
        assertEquals("503", match?.groupValues?.get(1))
    }

    @Test
    fun httpCodeRegex_returnsNull_whenPatternMissing() {
        val match = Constants.HTTP_CODE_REGEX.find("Something else entirely")
        assertTrue(match == null)
    }
}

