package com.riteldevelopment.carriertestoverride.data

/**
 * The country an MCC belongs to.
 *
 * This exists because the two layers assert a region in two different ways, and only one of them writes
 * a country code the platform will hand back. The SIM identity layer rewrites MCC/MNC and nothing else,
 * so `getSimCountryIso()` keeps returning the *real* country while the operator numeric already says
 * 23430 — and a screen that read the two straight out of the platform would put "23430" and "CN" side
 * by side, which is not a region anyone is pretending to be.
 *
 * Reading the country off the MCC is not a guess to paper over that. The first three digits of an IMSI
 * *are* the country under ITU-T E.212: 234 is the United Kingdom whether or not anything else on the
 * phone has caught up, and the region checks this tool exists to influence read exactly those digits.
 * So the table below is the same derivation the platform does internally, done where this app can reach
 * it — `MccTable` lives in the telephony service, not in an app process, and there is no public API for
 * the question.
 *
 * Assignments only, no MNCs: which *carrier* holds a code changes with every merger, while which
 * country holds an MCC effectively does not. Codes with no single country — 901 and the rest of the
 * shared 9xx range — are left out, so they return empty and the caller shows the digits alone rather
 * than inventing a flag for a satellite network.
 */
fun countryIsoForMccMnc(operatorNumeric: String): String {
    if (operatorNumeric.length < MCC_LENGTH) return ""
    val mcc = operatorNumeric.substring(0, MCC_LENGTH)
    if (!mcc.all { it in '0'..'9' }) return ""
    return MCC_COUNTRY[mcc].orEmpty()
}

private const val MCC_LENGTH = 3

private val MCC_COUNTRY: Map<String, String> = mapOf(
    // Europe
    "202" to "gr", "204" to "nl", "206" to "be", "208" to "fr", "212" to "mc",
    "213" to "ad", "214" to "es", "216" to "hu", "218" to "ba", "219" to "hr",
    "220" to "rs", "221" to "xk", "222" to "it", "226" to "ro", "228" to "ch",
    "230" to "cz", "231" to "sk", "232" to "at", "234" to "gb", "235" to "gb",
    "238" to "dk", "240" to "se", "242" to "no", "244" to "fi", "246" to "lt",
    "247" to "lv", "248" to "ee", "250" to "ru", "255" to "ua", "257" to "by",
    "259" to "md", "260" to "pl", "262" to "de", "266" to "gi", "268" to "pt",
    "270" to "lu", "272" to "ie", "274" to "is", "276" to "al", "278" to "mt",
    "280" to "cy", "282" to "ge", "283" to "am", "284" to "bg", "286" to "tr",
    "288" to "fo", "290" to "gl", "292" to "sm", "293" to "si", "294" to "mk",
    "295" to "li", "297" to "me",

    // North America and the Caribbean
    "302" to "ca", "308" to "pm", "310" to "us", "311" to "us", "312" to "us",
    "313" to "us", "314" to "us", "315" to "us", "316" to "us", "330" to "pr",
    "332" to "vi", "334" to "mx", "338" to "jm", "340" to "gp", "342" to "bb",
    "344" to "ag", "346" to "ky", "348" to "vg", "350" to "bm", "352" to "gd",
    "354" to "ms", "356" to "kn", "358" to "lc", "360" to "vc", "362" to "cw",
    "363" to "aw", "364" to "bs", "365" to "ai", "366" to "dm", "368" to "cu",
    "370" to "do", "372" to "ht", "374" to "tt", "376" to "tc",

    // Asia and the Middle East
    "400" to "az", "401" to "kz", "402" to "bt", "404" to "in", "405" to "in",
    "406" to "in", "410" to "pk", "412" to "af", "413" to "lk", "414" to "mm",
    "415" to "lb", "416" to "jo", "417" to "sy", "418" to "iq", "419" to "kw",
    "420" to "sa", "421" to "ye", "422" to "om", "424" to "ae", "425" to "il",
    "426" to "bh", "427" to "qa", "428" to "mn", "429" to "np", "430" to "ae",
    "431" to "ae", "432" to "ir", "434" to "uz", "436" to "tj", "437" to "kg",
    "438" to "tm", "440" to "jp", "441" to "jp", "450" to "kr", "452" to "vn",
    "454" to "hk", "455" to "mo", "456" to "kh", "457" to "la", "460" to "cn",
    "461" to "cn", "466" to "tw", "467" to "kp", "470" to "bd", "472" to "mv",

    // Oceania and South-East Asia
    "502" to "my", "505" to "au", "510" to "id", "514" to "tl", "515" to "ph",
    "520" to "th", "525" to "sg", "528" to "bn", "530" to "nz", "536" to "nr",
    "537" to "pg", "539" to "to", "540" to "sb", "541" to "vu", "542" to "fj",
    "543" to "wf", "544" to "as", "545" to "ki", "546" to "nc", "547" to "pf",
    "548" to "ck", "549" to "ws", "550" to "fm", "551" to "mh", "552" to "pw",
    "553" to "tv", "554" to "tk", "555" to "nu",

    // Africa
    "602" to "eg", "603" to "dz", "604" to "ma", "605" to "tn", "606" to "ly",
    "607" to "gm", "608" to "sn", "609" to "mr", "610" to "ml", "611" to "gn",
    "612" to "ci", "613" to "bf", "614" to "ne", "615" to "tg", "616" to "bj",
    "617" to "mu", "618" to "lr", "619" to "sl", "620" to "gh", "621" to "ng",
    "622" to "td", "623" to "cf", "624" to "cm", "625" to "cv", "626" to "st",
    "627" to "gq", "628" to "ga", "629" to "cg", "630" to "cd", "631" to "ao",
    "632" to "gw", "633" to "sc", "634" to "sd", "635" to "rw", "636" to "et",
    "637" to "so", "638" to "dj", "639" to "ke", "640" to "tz", "641" to "ug",
    "642" to "bi", "643" to "mz", "645" to "zm", "646" to "mg", "647" to "re",
    "648" to "zw", "649" to "na", "650" to "mw", "651" to "ls", "652" to "bw",
    "653" to "sz", "654" to "km", "655" to "za", "657" to "er", "658" to "sh",
    "659" to "ss",

    // Central and South America
    "702" to "bz", "704" to "gt", "706" to "sv", "708" to "hn", "710" to "ni",
    "712" to "cr", "714" to "pa", "716" to "pe", "722" to "ar", "724" to "br",
    "730" to "cl", "732" to "co", "734" to "ve", "736" to "bo", "738" to "gy",
    "740" to "ec", "742" to "gf", "744" to "py", "746" to "sr", "748" to "uy",
    "750" to "fk",
)
