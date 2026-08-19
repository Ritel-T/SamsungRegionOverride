package com.riteldevelopment.carriertestoverride.ui

/**
 * The flag for a two-letter country code.
 *
 * Computed from the ISO letters rather than stored, so the catalog carries no flag column to fall out
 * of step with the code beside it, and a country added later gets its flag for free. A flag emoji is
 * just its two letters written as Unicode regional indicators — 'G','B' becomes the pair at
 * [REGIONAL_INDICATOR_A] + 6, 1 — which the font then draws as one glyph.
 *
 * Deriving it also keeps this file ASCII: the alternative is 200-odd literal flag characters in source,
 * which survive editors and diff tools far less reliably than the arithmetic does.
 *
 * Anything that is not two ASCII letters returns empty rather than a placeholder. A row with no flag
 * reads as a row with no flag; a row with a tofu box reads as a bug.
 */
private const val REGIONAL_INDICATOR_A = 0x1F1E6

fun flagEmoji(countryIso: String): String {
    if (countryIso.length != 2) return ""
    val first = countryIso[0].uppercaseChar()
    val second = countryIso[1].uppercaseChar()
    if (first !in 'A'..'Z' || second !in 'A'..'Z') return ""
    return buildString {
        appendCodePoint(REGIONAL_INDICATOR_A + (first - 'A'))
        appendCodePoint(REGIONAL_INDICATOR_A + (second - 'A'))
    }
}
