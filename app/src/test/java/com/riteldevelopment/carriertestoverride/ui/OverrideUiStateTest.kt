package com.riteldevelopment.carriertestoverride.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideUiStateTest {
    @Test
    fun mccMncInputNormalizesEveryDecimalDigitToAscii() {
        val input = "\u0662\u06F3\u096A\uFF14\u0E55\uD835\uDFD4A\u2167\u00B2-7"

        assertEquals("234456", normalizeMccMncInput(input))
        assertTrue(isValidMccMnc(normalizeMccMncInput(input)))
        assertFalse(isValidMccMnc("\u0662\u0663\u0664\u0663\u0660"))
    }

    @Test
    fun headlineResourceResolvesAgainForEachLocale() {
        val headline = LocalizedText.resource(
            10,
            LocalizedText.resource(11, 1),
        )

        val english = headline.resolveWith { id, args ->
            when (id) {
                10 -> "Error on ${args.single()}"
                11 -> "SIM ${args.single()}"
                else -> error("Unexpected resource $id")
            }
        }
        val chinese = headline.resolveWith { id, args ->
            when (id) {
                10 -> "${args.single()} 出错"
                11 -> "SIM ${args.single()}"
                else -> error("Unexpected resource $id")
            }
        }

        assertEquals("Error on SIM 1", english)
        assertEquals("SIM 1 出错", chinese)
    }
}
