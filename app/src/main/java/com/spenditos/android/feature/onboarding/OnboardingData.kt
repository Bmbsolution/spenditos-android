package com.spenditos.android.feature.onboarding

import com.spenditos.android.data.model.OnboardingCategoryRequest

data class OnboardingCategorySeed(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val isIncome: Boolean,
    val sortOrder: Int,
    val group: String,
) {
    fun toRequest() = OnboardingCategoryRequest(name, color, isIncome, icon, sortOrder, isSystemSuggestion = true)
}

/** Seed data copied from iOS `Features/Onboarding/OnboardingView.swift:19-52`. */
object OnboardingData {
    val categorySeeds = listOf(
        OnboardingCategorySeed("SUGGESTED_INCOME_SALARY", "Salary", "#22c55e", "💰", true, 0, "Income"),
        OnboardingCategorySeed("SUGGESTED_INCOME_FREELANCE", "Freelance / Side Income", "#16a34a", "💼", true, 1, "Income"),
        OnboardingCategorySeed("SUGGESTED_EXP_RENT", "Rent / Mortgage", "#ef4444", "🏠", false, 10, "Housing & Utilities"),
        OnboardingCategorySeed("SUGGESTED_EXP_UTILITIES", "Utilities", "#3b82f6", "⚡", false, 11, "Housing & Utilities"),
        OnboardingCategorySeed("SUGGESTED_EXP_INTERNET", "Internet", "#0ea5e9", "🌐", false, 12, "Housing & Utilities"),
        OnboardingCategorySeed("SUGGESTED_EXP_GROCERIES", "Groceries", "#facc15", "🛒", false, 20, "Groceries & Food"),
        OnboardingCategorySeed("SUGGESTED_EXP_EATING_OUT", "Eating Out", "#f97316", "🍕", false, 21, "Groceries & Food"),
        OnboardingCategorySeed("SUGGESTED_EXP_COFFEE_SNACKS", "Coffee & Snacks", "#fb923c", "☕", false, 22, "Groceries & Food"),
        OnboardingCategorySeed("SUGGESTED_EXP_TRANSPORT_PUBLIC", "Public Transport", "#6366f1", "🚌", false, 30, "Transportation"),
        OnboardingCategorySeed("SUGGESTED_EXP_TRANSPORT_FUEL", "Fuel / Gas", "#a855f7", "⛽", false, 31, "Transportation"),
        OnboardingCategorySeed("SUGGESTED_EXP_TRANSPORT_MAINTENANCE", "Car Maintenance", "#7c3aed", "🔧", false, 32, "Transportation"),
        OnboardingCategorySeed("SUGGESTED_EXP_SUBSCRIPTIONS", "Subscriptions", "#06b6d4", "📺", false, 40, "Subscriptions & Entertainment"),
        OnboardingCategorySeed("SUGGESTED_EXP_ENTERTAINMENT", "Entertainment", "#ec4899", "🎮", false, 41, "Subscriptions & Entertainment"),
        OnboardingCategorySeed("SUGGESTED_EXP_SAVINGS", "Savings", "#22c55e", "🐷", false, 50, "Savings & Debt"),
        OnboardingCategorySeed("SUGGESTED_EXP_DEBT_PAYMENTS", "Debt Payments", "#f97316", "💳", false, 51, "Savings & Debt"),
        OnboardingCategorySeed("SUGGESTED_EXP_HEALTH", "Health & Medical", "#10b981", "❤️", false, 60, "Miscellaneous"),
        OnboardingCategorySeed("SUGGESTED_EXP_PERSONAL_CARE", "Personal Care", "#8b5cf6", "✨", false, 61, "Miscellaneous"),
        OnboardingCategorySeed("SUGGESTED_EXP_MISC", "Miscellaneous", "#64748b", "🏷️", false, 62, "Miscellaneous"),
    )

    /** ISO 3166-1 alpha-2 to ISO 4217, from iOS `OnboardingView.swift:59-149`. */
    val countryCurrencyMap: Map<String, String> = mapOf(
        "AD" to "EUR", "AE" to "AED", "AF" to "AFN", "AG" to "XCD", "AI" to "XCD",
        "AL" to "ALL", "AM" to "AMD", "AO" to "AOA", "AQ" to "USD", "AR" to "ARS",
        "AS" to "USD", "AT" to "EUR", "AU" to "AUD", "AW" to "AWG", "AX" to "EUR",
        "AZ" to "AZN", "BA" to "BAM", "BB" to "BBD", "BD" to "BDT", "BE" to "EUR",
        "BF" to "XOF", "BG" to "BGN", "BH" to "BHD", "BI" to "BIF", "BJ" to "XOF",
        "BL" to "EUR", "BM" to "BMD", "BN" to "BND", "BO" to "BOB", "BQ" to "USD",
        "BR" to "BRL", "BS" to "BSD", "BT" to "BTN", "BV" to "NOK", "BW" to "BWP",
        "BY" to "BYN", "BZ" to "BZD", "CA" to "CAD", "CC" to "AUD", "CD" to "CDF",
        "CF" to "XAF", "CG" to "XAF", "CH" to "CHF", "CI" to "XOF", "CK" to "NZD",
        "CL" to "CLP", "CM" to "XAF", "CN" to "CNY", "CO" to "COP", "CR" to "CRC",
        "CU" to "CUP", "CV" to "CVE", "CW" to "ANG", "CX" to "AUD", "CY" to "EUR",
        "CZ" to "CZK", "DE" to "EUR", "DJ" to "DJF", "DK" to "DKK", "DM" to "XCD",
        "DO" to "DOP", "DZ" to "DZD", "EC" to "USD", "EE" to "EUR", "EG" to "EGP",
        "EH" to "MAD", "ER" to "ERN", "ES" to "EUR", "ET" to "ETB", "EU" to "EUR",
        "FI" to "EUR", "FJ" to "FJD", "FK" to "FKP", "FM" to "USD", "FO" to "DKK",
        "FR" to "EUR", "GA" to "XAF", "GB" to "GBP", "GD" to "XCD", "GE" to "GEL",
        "GF" to "EUR", "GG" to "GBP", "GH" to "GHS", "GI" to "GIP", "GL" to "DKK",
        "GM" to "GMD", "GN" to "GNF", "GP" to "EUR", "GQ" to "XAF", "GR" to "EUR",
        "GS" to "FKP", "GT" to "GTQ", "GU" to "USD", "GW" to "XOF", "GY" to "GYD",
        "HK" to "HKD", "HM" to "AUD", "HN" to "HNL", "HR" to "EUR", "HT" to "HTG",
        "HU" to "HUF", "ID" to "IDR", "IE" to "EUR", "IL" to "ILS", "IM" to "GBP",
        "IN" to "INR", "IO" to "USD", "IQ" to "IQD", "IR" to "IRR", "IS" to "ISK",
        "IT" to "EUR", "JE" to "GBP", "JM" to "JMD", "JO" to "JOD", "JP" to "JPY",
        "KE" to "KES", "KG" to "KGS", "KH" to "KHR", "KI" to "AUD", "KM" to "KMF",
        "KN" to "XCD", "KP" to "KPW", "KR" to "KRW", "KW" to "KWD", "KY" to "KYD",
        "KZ" to "KZT", "LA" to "LAK", "LB" to "LBP", "LC" to "XCD", "LI" to "CHF",
        "LK" to "LKR", "LR" to "LRD", "LS" to "LSL", "LT" to "EUR", "LU" to "EUR",
        "LV" to "EUR", "LY" to "LYD", "MA" to "MAD", "MC" to "EUR", "MD" to "MDL",
        "ME" to "EUR", "MF" to "EUR", "MG" to "MGA", "MH" to "USD", "MK" to "MKD",
        "ML" to "XOF", "MM" to "MMK", "MN" to "MNT", "MO" to "MOP", "MP" to "USD",
        "MQ" to "EUR", "MR" to "MRU", "MS" to "XCD", "MT" to "EUR", "MU" to "MUR",
        "MV" to "MVR", "MW" to "MWK", "MX" to "MXN", "MY" to "MYR", "MZ" to "MZN",
        "NA" to "NAD", "NC" to "XPF", "NE" to "XOF", "NF" to "AUD", "NG" to "NGN",
        "NI" to "NIO", "NL" to "EUR", "NO" to "NOK", "NP" to "NPR", "NR" to "AUD",
        "NU" to "NZD", "NZ" to "NZD", "OM" to "OMR", "PA" to "PAB", "PE" to "PEN",
        "PF" to "XPF", "PG" to "PGK", "PH" to "PHP", "PK" to "PKR", "PL" to "PLN",
        "PM" to "EUR", "PN" to "NZD", "PR" to "USD", "PS" to "ILS", "PT" to "EUR",
        "PW" to "USD", "PY" to "PYG", "QA" to "QAR", "RE" to "EUR", "RO" to "RON",
        "RS" to "RSD", "RU" to "RUB", "RW" to "RWF", "SA" to "SAR", "SB" to "SBD",
        "SC" to "SCR", "SD" to "SDG", "SE" to "SEK", "SG" to "SGD", "SH" to "SHP",
        "SI" to "EUR", "SJ" to "NOK", "SK" to "EUR", "SL" to "SLE", "SM" to "EUR",
        "SN" to "XOF", "SO" to "SOS", "SR" to "SRD", "SS" to "SSP", "ST" to "STN",
        "SV" to "USD", "SX" to "ANG", "SY" to "SYP", "SZ" to "SZL", "TC" to "USD",
        "TD" to "XAF", "TF" to "EUR", "TG" to "XOF", "TH" to "THB", "TJ" to "TJS",
        "TK" to "NZD", "TL" to "USD", "TM" to "TMT", "TN" to "TND", "TO" to "TOP",
        "TR" to "TRY", "TT" to "TTD", "TV" to "AUD", "TW" to "TWD", "TZ" to "TZS",
        "UA" to "UAH", "UG" to "UGX", "UM" to "USD", "US" to "USD", "UY" to "UYU",
        "UZ" to "UZS", "VA" to "EUR", "VC" to "XCD", "VE" to "VES", "VG" to "USD",
        "VI" to "USD", "VN" to "VND", "VU" to "VUV", "WF" to "XPF", "WS" to "WST",
        "YE" to "YER", "YT" to "EUR", "ZA" to "ZAR", "ZM" to "ZMW", "ZW" to "ZWL",
    )

    /** Backend `packages/core/src/utils/currency.ts` rejects these iOS-map values. */
    private val unsupportedByBackend = setOf(
        "ANG", "AWG", "BBD", "BIF", "BSD", "CVE", "FKP", "GIP", "KPW", "KYD",
        "LRD", "MOP", "MRU", "SHP", "SLE", "SSP", "STN", "ZWL",
    )

    fun currencyForCountry(countryCode: String): String =
        countryCurrencyMap[countryCode.uppercase()]?.takeUnless { it in unsupportedByBackend } ?: "USD"
}
