package com.riteldevelopment.carriertestoverride.ui

import java.util.Locale

/**
 * A ready-made target: one carrier in one country.
 *
 * The catalog is a convenience, not an authority. Every value here can be typed by hand, and a preset
 * only ever fills the three fields — it is never consulted again once applied, so a stale entry costs
 * the user a correction rather than a wrong write.
 */
data class RegionPreset(
    val country: String,
    val countryIso: String,
    val carrier: String,
    val mccMnc: String,
    /** True only where this exact triple has been applied and restored on real hardware. */
    val verified: Boolean = false,
    /**
     * Offered as a one-tap chip above the picker.
     *
     * A flag on the entry rather than a separate list of ids elsewhere: a list would be a second place
     * to keep the same facts, and a typo in it would drop a chip silently instead of failing.
     */
    val common: Boolean = false,
    /** Extra search terms: what people actually type, which is rarely the formal country name. */
    val aliases: String = "",
) {
    /** Stable across list reordering, unlike an index — the selection survives a filtered list. */
    val id: String get() = "$mccMnc@$countryIso"

    val label: String get() = "$country · $carrier"

    /** The country's flag, so a row or chip can be recognised without being read word by word. */
    val flag: String get() = flagEmoji(countryIso)

    private val haystack: String by lazy(LazyThreadSafetyMode.NONE) {
        "$country $carrier $countryIso $mccMnc $aliases".lowercase(Locale.ROOT)
    }

    /** Every whitespace-separated term must appear, so "uk ee" narrows instead of widening. */
    fun matches(query: String): Boolean {
        val terms = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
        return terms.all { haystack.contains(it) }
    }
}

/**
 * Public MCC/MNC assignments for the carriers people actually target, ordered by country.
 *
 * Large networks own several MNCs — Indian circles and US regional codes especially — so where a
 * carrier has many, the one listed is its most widely reported code. That is enough for the region
 * checks this tool exists to influence, which read the MCC and the country far more often than they
 * read the exact network.
 */
object RegionPresets {

    val ALL: List<RegionPreset> = listOf(
        RegionPreset("Argentina", "ar", "Claro", "722310"),
        RegionPreset("Argentina", "ar", "Movistar", "722070"),
        RegionPreset("Argentina", "ar", "Personal", "722341"),
        RegionPreset("Australia", "au", "Telstra", "50501"),
        RegionPreset("Australia", "au", "Optus", "50502"),
        RegionPreset("Australia", "au", "Vodafone", "50503"),
        RegionPreset("Austria", "at", "A1", "23201"),
        RegionPreset("Austria", "at", "Magenta", "23203"),
        RegionPreset("Austria", "at", "Drei", "23210"),
        RegionPreset("Bangladesh", "bd", "Grameenphone", "47001"),
        RegionPreset("Bangladesh", "bd", "Robi", "47002"),
        RegionPreset("Belarus", "by", "A1", "25701"),
        RegionPreset("Belarus", "by", "MTS", "25702"),
        RegionPreset("Belgium", "be", "Proximus", "20601"),
        RegionPreset("Belgium", "be", "Orange", "20610"),
        RegionPreset("Belgium", "be", "BASE", "20620"),
        RegionPreset("Brazil", "br", "TIM", "72402"),
        RegionPreset("Brazil", "br", "Claro", "72405"),
        RegionPreset("Brazil", "br", "Vivo", "72406"),
        RegionPreset("Bulgaria", "bg", "A1", "28401"),
        RegionPreset("Bulgaria", "bg", "Vivacom", "28403"),
        RegionPreset("Bulgaria", "bg", "Yettel", "28405"),
        RegionPreset("Cambodia", "kh", "Cellcard", "45601"),
        RegionPreset("Cambodia", "kh", "Smart", "45606"),
        RegionPreset("Canada", "ca", "Telus", "302220"),
        RegionPreset("Canada", "ca", "Bell", "302610"),
        RegionPreset("Canada", "ca", "Rogers", "302720"),
        RegionPreset("Chile", "cl", "Entel", "73001"),
        RegionPreset("Chile", "cl", "Movistar", "73002"),
        RegionPreset("Chile", "cl", "Claro", "73003"),
        RegionPreset("China", "cn", "China Mobile", "46000", aliases = "prc"),
        RegionPreset("China", "cn", "China Unicom", "46001", aliases = "prc"),
        RegionPreset("China", "cn", "China Telecom", "46011", aliases = "prc"),
        RegionPreset("Colombia", "co", "Movistar", "732123"),
        RegionPreset("Colombia", "co", "Claro", "732101"),
        RegionPreset("Colombia", "co", "Tigo", "732103"),
        RegionPreset("Croatia", "hr", "Hrvatski Telekom", "21901"),
        RegionPreset("Croatia", "hr", "A1", "21910"),
        RegionPreset("Czechia", "cz", "T-Mobile", "23001", aliases = "czech republic"),
        RegionPreset("Czechia", "cz", "O2", "23002", aliases = "czech republic"),
        RegionPreset("Czechia", "cz", "Vodafone", "23003", aliases = "czech republic"),
        RegionPreset("Denmark", "dk", "TDC", "23801"),
        RegionPreset("Denmark", "dk", "Telenor", "23802"),
        RegionPreset("Denmark", "dk", "Telia", "23820"),
        RegionPreset("Egypt", "eg", "Orange", "60201"),
        RegionPreset("Egypt", "eg", "Vodafone", "60202"),
        RegionPreset("Egypt", "eg", "Etisalat", "60203"),
        RegionPreset("Estonia", "ee", "Telia", "24801"),
        RegionPreset("Estonia", "ee", "Elisa", "24802"),
        RegionPreset("Finland", "fi", "Elisa", "24405"),
        RegionPreset("Finland", "fi", "DNA", "24412"),
        RegionPreset("Finland", "fi", "Telia", "24491"),
        RegionPreset("France", "fr", "Orange", "20801"),
        RegionPreset("France", "fr", "SFR", "20810"),
        RegionPreset("France", "fr", "Free", "20815"),
        RegionPreset("France", "fr", "Bouygues", "20820"),
        RegionPreset("Germany", "de", "Telekom", "26201", common = true, aliases = "deutschland"),
        RegionPreset("Germany", "de", "Vodafone", "26202", aliases = "deutschland"),
        RegionPreset("Germany", "de", "O2", "26203", aliases = "deutschland"),
        RegionPreset("Greece", "gr", "Cosmote", "20201"),
        RegionPreset("Greece", "gr", "Vodafone", "20205"),
        RegionPreset("Greece", "gr", "Nova", "20210"),
        RegionPreset("Hong Kong", "hk", "CSL", "45400", common = true, aliases = "hk"),
        RegionPreset("Hong Kong", "hk", "3 HK", "45403", aliases = "hk"),
        RegionPreset("Hong Kong", "hk", "SmarTone", "45406", aliases = "hk"),
        RegionPreset("Hungary", "hu", "Yettel", "21601"),
        RegionPreset("Hungary", "hu", "Telekom", "21630"),
        RegionPreset("Hungary", "hu", "Vodafone", "21670"),
        RegionPreset("Iceland", "is", "Siminn", "27401"),
        RegionPreset("Iceland", "is", "Vodafone", "27402"),
        RegionPreset("India", "in", "Airtel", "40410"),
        RegionPreset("India", "in", "Vodafone Idea", "40411", aliases = "vi"),
        RegionPreset("India", "in", "Jio", "405840", aliases = "reliance"),
        RegionPreset("Indonesia", "id", "Indosat", "51001"),
        RegionPreset("Indonesia", "id", "Telkomsel", "51010"),
        RegionPreset("Indonesia", "id", "XL", "51011"),
        RegionPreset("Ireland", "ie", "Vodafone", "27201", aliases = "eire"),
        RegionPreset("Ireland", "ie", "Eir", "27203", aliases = "eire"),
        RegionPreset("Ireland", "ie", "Three", "27205", aliases = "eire"),
        RegionPreset("Israel", "il", "Partner", "42501"),
        RegionPreset("Israel", "il", "Cellcom", "42502"),
        RegionPreset("Israel", "il", "Pelephone", "42503"),
        RegionPreset("Italy", "it", "TIM", "22201", aliases = "italia"),
        RegionPreset("Italy", "it", "Vodafone", "22210", aliases = "italia"),
        RegionPreset("Italy", "it", "WindTre", "22288", aliases = "italia"),
        RegionPreset("Japan", "jp", "NTT Docomo", "44010", common = true),
        RegionPreset("Japan", "jp", "Rakuten", "44011"),
        RegionPreset("Japan", "jp", "SoftBank", "44020"),
        RegionPreset("Japan", "jp", "au KDDI", "44051"),
        RegionPreset("Kazakhstan", "kz", "Beeline", "40101"),
        RegionPreset("Kazakhstan", "kz", "Kcell", "40102"),
        RegionPreset("Kenya", "ke", "Safaricom", "63902"),
        RegionPreset("Kuwait", "kw", "Zain", "41902"),
        RegionPreset("Kuwait", "kw", "Ooredoo", "41903"),
        RegionPreset("Kuwait", "kw", "stc", "41904"),
        RegionPreset("Latvia", "lv", "LMT", "24701"),
        RegionPreset("Latvia", "lv", "Tele2", "24702"),
        RegionPreset("Lithuania", "lt", "Telia", "24601"),
        RegionPreset("Lithuania", "lt", "Bite", "24602"),
        RegionPreset("Luxembourg", "lu", "POST", "27001"),
        RegionPreset("Luxembourg", "lu", "Orange", "27099"),
        RegionPreset("Macau", "mo", "CTM", "45501", aliases = "macao"),
        RegionPreset("Macau", "mo", "China Telecom", "45505", aliases = "macao"),
        RegionPreset("Malaysia", "my", "Maxis", "50212"),
        RegionPreset("Malaysia", "my", "Digi", "50216"),
        RegionPreset("Malaysia", "my", "Celcom", "50219"),
        RegionPreset("Mexico", "mx", "Telcel", "334020"),
        RegionPreset("Mexico", "mx", "Movistar", "334030"),
        RegionPreset("Mexico", "mx", "AT&T", "334050"),
        RegionPreset("Morocco", "ma", "Orange", "60400"),
        RegionPreset("Morocco", "ma", "Maroc Telecom", "60401"),
        RegionPreset("Morocco", "ma", "inwi", "60402"),
        RegionPreset("Myanmar", "mm", "MPT", "41401", aliases = "burma"),
        RegionPreset("Myanmar", "mm", "ATOM", "41405", aliases = "burma"),
        RegionPreset("Nepal", "np", "NTC", "42901"),
        RegionPreset("Nepal", "np", "Ncell", "42902"),
        RegionPreset("Netherlands", "nl", "Vodafone", "20404", aliases = "holland"),
        RegionPreset("Netherlands", "nl", "KPN", "20408", aliases = "holland"),
        RegionPreset("Netherlands", "nl", "Odido", "20416", aliases = "holland t-mobile"),
        RegionPreset("New Zealand", "nz", "One NZ", "53001", aliases = "vodafone"),
        RegionPreset("New Zealand", "nz", "Spark", "53005"),
        RegionPreset("New Zealand", "nz", "2degrees", "53024"),
        RegionPreset("Nigeria", "ng", "Airtel", "62120"),
        RegionPreset("Nigeria", "ng", "MTN", "62130"),
        RegionPreset("Nigeria", "ng", "Glo", "62150"),
        RegionPreset("Norway", "no", "Telenor", "24201"),
        RegionPreset("Norway", "no", "Telia", "24202"),
        RegionPreset("Pakistan", "pk", "Jazz", "41001"),
        RegionPreset("Pakistan", "pk", "Zong", "41004"),
        RegionPreset("Pakistan", "pk", "Telenor", "41006"),
        RegionPreset("Peru", "pe", "Movistar", "71606"),
        RegionPreset("Peru", "pe", "Claro", "71610"),
        RegionPreset("Peru", "pe", "Entel", "71617"),
        RegionPreset("Philippines", "ph", "Globe", "51502"),
        RegionPreset("Philippines", "ph", "Smart", "51503"),
        RegionPreset("Poland", "pl", "Plus", "26001"),
        RegionPreset("Poland", "pl", "T-Mobile", "26002"),
        RegionPreset("Poland", "pl", "Orange", "26003"),
        RegionPreset("Poland", "pl", "Play", "26006"),
        RegionPreset("Portugal", "pt", "Vodafone", "26801"),
        RegionPreset("Portugal", "pt", "NOS", "26803"),
        RegionPreset("Portugal", "pt", "MEO", "26806"),
        RegionPreset("Qatar", "qa", "Ooredoo", "42701"),
        RegionPreset("Qatar", "qa", "Vodafone", "42702"),
        RegionPreset("Romania", "ro", "Vodafone", "22601"),
        RegionPreset("Romania", "ro", "Digi", "22605"),
        RegionPreset("Romania", "ro", "Orange", "22610"),
        RegionPreset("Russia", "ru", "MTS", "25001"),
        RegionPreset("Russia", "ru", "MegaFon", "25002"),
        RegionPreset("Russia", "ru", "Tele2", "25020"),
        RegionPreset("Russia", "ru", "Beeline", "25099"),
        RegionPreset("Saudi Arabia", "sa", "STC", "42001", aliases = "ksa"),
        RegionPreset("Saudi Arabia", "sa", "Mobily", "42003", aliases = "ksa"),
        RegionPreset("Saudi Arabia", "sa", "Zain", "42004", aliases = "ksa"),
        RegionPreset("Serbia", "rs", "Telekom Srbija", "22003"),
        RegionPreset("Serbia", "rs", "A1", "22005"),
        RegionPreset("Singapore", "sg", "Singtel", "52501", common = true),
        RegionPreset("Singapore", "sg", "M1", "52503"),
        RegionPreset("Singapore", "sg", "StarHub", "52505"),
        RegionPreset("Slovakia", "sk", "Orange", "23101"),
        RegionPreset("Slovakia", "sk", "Telekom", "23102"),
        RegionPreset("Slovakia", "sk", "O2", "23106"),
        RegionPreset("Slovenia", "si", "A1", "29340"),
        RegionPreset("Slovenia", "si", "Telekom Slovenije", "29341"),
        RegionPreset("South Africa", "za", "Vodacom", "65501"),
        RegionPreset("South Africa", "za", "MTN", "65510"),
        RegionPreset("South Korea", "kr", "SKT", "45005", common = true, aliases = "korea"),
        RegionPreset("South Korea", "kr", "LG U+", "45006", aliases = "korea"),
        RegionPreset("South Korea", "kr", "KT", "45008", aliases = "korea"),
        RegionPreset("Spain", "es", "Vodafone", "21401", aliases = "espana"),
        RegionPreset("Spain", "es", "Orange", "21403", aliases = "espana"),
        RegionPreset("Spain", "es", "Movistar", "21407", aliases = "espana"),
        RegionPreset("Sri Lanka", "lk", "Mobitel", "41301"),
        RegionPreset("Sri Lanka", "lk", "Dialog", "41302"),
        RegionPreset("Sweden", "se", "Telia", "24001"),
        RegionPreset("Sweden", "se", "Tele2", "24007"),
        RegionPreset("Sweden", "se", "Telenor", "24008"),
        RegionPreset("Switzerland", "ch", "Swisscom", "22801"),
        RegionPreset("Switzerland", "ch", "Sunrise", "22802"),
        RegionPreset("Switzerland", "ch", "Salt", "22803"),
        RegionPreset("Taiwan", "tw", "FarEasTone", "46601"),
        RegionPreset("Taiwan", "tw", "Chunghwa", "46692", common = true),
        RegionPreset("Taiwan", "tw", "Taiwan Mobile", "46697"),
        RegionPreset("Thailand", "th", "TrueMove H", "52000"),
        RegionPreset("Thailand", "th", "AIS", "52003"),
        RegionPreset("Thailand", "th", "dtac", "52005"),
        RegionPreset("Turkey", "tr", "Turkcell", "28601", aliases = "turkiye"),
        RegionPreset("Turkey", "tr", "Vodafone", "28602", aliases = "turkiye"),
        RegionPreset("Turkey", "tr", "Turk Telekom", "28603", aliases = "turkiye"),
        RegionPreset("Ukraine", "ua", "Vodafone", "25501"),
        RegionPreset("Ukraine", "ua", "Kyivstar", "25503"),
        RegionPreset("Ukraine", "ua", "lifecell", "25506"),
        RegionPreset("United Arab Emirates", "ae", "Etisalat", "42402", aliases = "uae dubai"),
        RegionPreset("United Arab Emirates", "ae", "du", "42403", aliases = "uae dubai"),
        RegionPreset(
            "United Kingdom", "gb", "EE", "23430",
            verified = true, common = true, aliases = "uk britain england",
        ),
        RegionPreset("United Kingdom", "gb", "O2", "23410", aliases = "uk britain england"),
        RegionPreset("United Kingdom", "gb", "Vodafone", "23415", aliases = "uk britain england"),
        RegionPreset("United Kingdom", "gb", "Three", "23420", aliases = "uk britain england"),
        RegionPreset("United States", "us", "T-Mobile", "310260", common = true, aliases = "usa america"),
        RegionPreset("United States", "us", "AT&T", "310410", aliases = "usa america"),
        RegionPreset("United States", "us", "Verizon", "311480", aliases = "usa america"),
        RegionPreset("Vietnam", "vn", "MobiFone", "45201"),
        RegionPreset("Vietnam", "vn", "Vinaphone", "45202"),
        RegionPreset("Vietnam", "vn", "Viettel", "45204"),
    )

    /** What a fresh install starts on: the only entry proven end to end on real hardware. */
    val DEFAULT: RegionPreset = ALL.first { it.verified }

    /**
     * The one-tap set, in catalog order.
     *
     * Chosen for how often people target them rather than by subscriber count, and held to one carrier
     * per country: the chips exist so the common case skips the search box, and two carriers from the
     * same country would spend a row on a distinction the region checks do not make.
     */
    val COMMON: List<RegionPreset> = ALL.filter { it.common }

    fun byId(id: String?): RegionPreset? = id?.let { wanted -> ALL.firstOrNull { it.id == wanted } }

    fun search(query: String): List<RegionPreset> =
        if (query.isBlank()) ALL else ALL.filter { it.matches(query) }
}
