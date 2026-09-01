package com.riteldevelopment.carriertestoverride.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideUiStateTest {
    @Test
    fun explicitSimSelectionSurvivesATransientMissingScan() {
        val selectedWhileMissing = resolveSelectedSubIdAfterScan(
            currentSelectedSubId = 11,
            scannedSubIds = listOf(22),
            defaultDataSubId = 22,
            selectionIsExplicit = true,
        )
        val selectedAfterReturn = resolveSelectedSubIdAfterScan(
            currentSelectedSubId = selectedWhileMissing,
            scannedSubIds = listOf(11, 22),
            defaultDataSubId = 22,
            selectionIsExplicit = true,
        )

        assertEquals(11, selectedWhileMissing)
        assertEquals(11, selectedAfterReturn)
    }

    @Test
    fun initialSimSelectionPrefersTheVisibleDataSimThenTheFirstVisibleSim() {
        assertEquals(
            22,
            resolveSelectedSubIdAfterScan(
                currentSelectedSubId = -1,
                scannedSubIds = listOf(11, 22),
                defaultDataSubId = 22,
                selectionIsExplicit = false,
            ),
        )
        assertEquals(
            11,
            resolveSelectedSubIdAfterScan(
                currentSelectedSubId = -1,
                scannedSubIds = listOf(11),
                defaultDataSubId = 22,
                selectionIsExplicit = false,
            ),
        )
        assertEquals(
            -1,
            resolveSelectedSubIdAfterScan(
                currentSelectedSubId = -1,
                scannedSubIds = emptyList(),
                defaultDataSubId = 22,
                selectionIsExplicit = false,
            ),
        )
    }

    @Test
    fun automaticFallbackPromotesTheDataSimWhenItAppearsInALaterScan() {
        val firstScan = resolveSelectedSubIdAfterScan(
            currentSelectedSubId = -1,
            scannedSubIds = listOf(11),
            defaultDataSubId = 22,
            selectionIsExplicit = false,
        )
        val secondScan = resolveSelectedSubIdAfterScan(
            currentSelectedSubId = firstScan,
            scannedSubIds = listOf(11, 22),
            defaultDataSubId = 22,
            selectionIsExplicit = false,
        )

        assertEquals(11, firstScan)
        assertEquals(22, secondScan)
    }

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
        // Word order differs from English, so a cached format string would fail here.
        val german = headline.resolveWith { id, args ->
            when (id) {
                10 -> "${args.single()} meldet einen Fehler"
                11 -> "SIM ${args.single()}"
                else -> error("Unexpected resource $id")
            }
        }

        assertEquals("Error on SIM 1", english)
        assertEquals("SIM 1 meldet einen Fehler", german)
    }
}
