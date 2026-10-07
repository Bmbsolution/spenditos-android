package com.spenditos.android.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

data class CurrencyOption(val code: String, val name: String)

private val popularCurrencyCodes = listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR")

private val currencyOptions = listOf(
    CurrencyOption("AED", "UAE Dirham"), CurrencyOption("AFN", "Afghan Afghani"), CurrencyOption("ALL", "Albanian Lek"),
    CurrencyOption("AMD", "Armenian Dram"), CurrencyOption("AOA", "Angolan Kwanza"),
    CurrencyOption("ARS", "Argentine Peso"), CurrencyOption("AUD", "Australian Dollar"),
    CurrencyOption("AZN", "Azerbaijani Manat"), CurrencyOption("BAM", "Bosnia-Herzegovina Convertible Mark"),
    CurrencyOption("BDT", "Bangladeshi Taka"), CurrencyOption("BGN", "Bulgarian Lev"),
    CurrencyOption("BHD", "Bahraini Dinar"), CurrencyOption("BMD", "Bermudian Dollar"),
    CurrencyOption("BND", "Brunei Dollar"), CurrencyOption("BOB", "Bolivian Boliviano"), CurrencyOption("BRL", "Brazilian Real"),
    CurrencyOption("BTN", "Bhutanese Ngultrum"), CurrencyOption("BWP", "Botswana Pula"),
    CurrencyOption("BYN", "Belarusian Ruble"), CurrencyOption("BZD", "Belize Dollar"), CurrencyOption("CAD", "Canadian Dollar"),
    CurrencyOption("CDF", "Congolese Franc"), CurrencyOption("CHF", "Swiss Franc"), CurrencyOption("CLP", "Chilean Peso"),
    CurrencyOption("CNY", "Chinese Yuan"), CurrencyOption("COP", "Colombian Peso"), CurrencyOption("CRC", "Costa Rican Colón"),
    CurrencyOption("CUP", "Cuban Peso"), CurrencyOption("CZK", "Czech Koruna"),
    CurrencyOption("DJF", "Djiboutian Franc"), CurrencyOption("DKK", "Danish Krone"), CurrencyOption("DOP", "Dominican Peso"),
    CurrencyOption("DZD", "Algerian Dinar"), CurrencyOption("EGP", "Egyptian Pound"), CurrencyOption("ERN", "Eritrean Nakfa"),
    CurrencyOption("ETB", "Ethiopian Birr"), CurrencyOption("EUR", "Euro"), CurrencyOption("FJD", "Fijian Dollar"),
    CurrencyOption("GBP", "British Pound"), CurrencyOption("GEL", "Georgian Lari"),
    CurrencyOption("GHS", "Ghanaian Cedi"), CurrencyOption("GMD", "Gambian Dalasi"),
    CurrencyOption("GNF", "Guinean Franc"), CurrencyOption("GTQ", "Guatemalan Quetzal"), CurrencyOption("GYD", "Guyanese Dollar"),
    CurrencyOption("HKD", "Hong Kong Dollar"), CurrencyOption("HNL", "Honduran Lempira"), CurrencyOption("HTG", "Haitian Gourde"),
    CurrencyOption("HUF", "Hungarian Forint"), CurrencyOption("IDR", "Indonesian Rupiah"), CurrencyOption("ILS", "Israeli Shekel"),
    CurrencyOption("INR", "Indian Rupee"), CurrencyOption("IQD", "Iraqi Dinar"), CurrencyOption("IRR", "Iranian Rial"),
    CurrencyOption("ISK", "Icelandic Króna"), CurrencyOption("JMD", "Jamaican Dollar"), CurrencyOption("JOD", "Jordanian Dinar"),
    CurrencyOption("JPY", "Japanese Yen"), CurrencyOption("KES", "Kenyan Shilling"), CurrencyOption("KGS", "Kyrgystani Som"),
    CurrencyOption("KHR", "Cambodian Riel"), CurrencyOption("KMF", "Comorian Franc"),
    CurrencyOption("KRW", "South Korean Won"), CurrencyOption("KWD", "Kuwaiti Dinar"),
    CurrencyOption("KZT", "Kazakhstani Tenge"), CurrencyOption("LAK", "Lao Kip"), CurrencyOption("LBP", "Lebanese Pound"),
    CurrencyOption("LKR", "Sri Lankan Rupee"), CurrencyOption("LSL", "Lesotho Loti"),
    CurrencyOption("LYD", "Libyan Dinar"), CurrencyOption("MAD", "Moroccan Dirham"), CurrencyOption("MDL", "Moldovan Leu"),
    CurrencyOption("MGA", "Malagasy Ariary"), CurrencyOption("MKD", "Macedonian Denar"), CurrencyOption("MMK", "Myanmar Kyat"),
    CurrencyOption("MNT", "Mongolian Tugrik"),
    CurrencyOption("MUR", "Mauritian Rupee"), CurrencyOption("MVR", "Maldivian Rufiyaa"), CurrencyOption("MWK", "Malawian Kwacha"),
    CurrencyOption("MXN", "Mexican Peso"), CurrencyOption("MYR", "Malaysian Ringgit"), CurrencyOption("MZN", "Mozambican Metical"),
    CurrencyOption("NAD", "Namibian Dollar"), CurrencyOption("NGN", "Nigerian Naira"), CurrencyOption("NIO", "Nicaraguan Córdoba"),
    CurrencyOption("NOK", "Norwegian Krone"), CurrencyOption("NPR", "Nepalese Rupee"), CurrencyOption("NZD", "New Zealand Dollar"),
    CurrencyOption("OMR", "Omani Rial"), CurrencyOption("PAB", "Panamanian Balboa"), CurrencyOption("PEN", "Peruvian Sol"),
    CurrencyOption("PGK", "Papua New Guinean Kina"), CurrencyOption("PHP", "Philippine Peso"), CurrencyOption("PKR", "Pakistani Rupee"),
    CurrencyOption("PLN", "Polish Zloty"), CurrencyOption("PYG", "Paraguayan Guaraní"), CurrencyOption("QAR", "Qatari Riyal"),
    CurrencyOption("RON", "Romanian Leu"), CurrencyOption("RSD", "Serbian Dinar"), CurrencyOption("RUB", "Russian Ruble"),
    CurrencyOption("RWF", "Rwandan Franc"), CurrencyOption("SAR", "Saudi Riyal"), CurrencyOption("SBD", "Solomon Islands Dollar"),
    CurrencyOption("SCR", "Seychellois Rupee"), CurrencyOption("SDG", "Sudanese Pound"), CurrencyOption("SEK", "Swedish Krona"),
    CurrencyOption("SGD", "Singapore Dollar"),
    CurrencyOption("SOS", "Somali Shilling"), CurrencyOption("SRD", "Surinamese Dollar"),
    CurrencyOption("SYP", "Syrian Pound"),
    CurrencyOption("SZL", "Swazi Lilangeni"), CurrencyOption("THB", "Thai Baht"), CurrencyOption("TJS", "Tajikistani Somoni"),
    CurrencyOption("TMT", "Turkmenistani Manat"), CurrencyOption("TND", "Tunisian Dinar"), CurrencyOption("TOP", "Tongan Paʻanga"),
    CurrencyOption("TRY", "Turkish Lira"), CurrencyOption("TTD", "Trinidad and Tobago Dollar"),
    CurrencyOption("TWD", "New Taiwan Dollar"), CurrencyOption("TZS", "Tanzanian Shilling"), CurrencyOption("UAH", "Ukrainian Hryvnia"),
    CurrencyOption("UGX", "Ugandan Shilling"), CurrencyOption("USD", "US Dollar"), CurrencyOption("UYU", "Uruguayan Peso"),
    CurrencyOption("UZS", "Uzbekistani Som"), CurrencyOption("VES", "Venezuelan Bolívar Soberano"),
    CurrencyOption("VND", "Vietnamese Đồng"), CurrencyOption("VUV", "Vanuatu Vatu"), CurrencyOption("WST", "Samoan Tala"),
    CurrencyOption("XAF", "Central African CFA Franc"), CurrencyOption("XCD", "East Caribbean Dollar"),
    CurrencyOption("XOF", "West African CFA Franc"), CurrencyOption("XPF", "CFP Franc"), CurrencyOption("YER", "Yemeni Rial"),
    CurrencyOption("ZAR", "South African Rand"), CurrencyOption("ZMW", "Zambian Kwacha"),
)

@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var currencySearch by remember { mutableStateOf("") }

    IosScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = Spacing.l),
        ) {
            item { ProfileHeader(onBack) }
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("profile.loading"), color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                state.error?.let { message ->
                    item {
                        GlassCard(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.s)) {
                            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.m).testTag("profile.error"))
                        }
                    }
                }
                if (state.isOffline) {
                    item {
                        Text(
                            "Check your connection and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("profile.offline"),
                        )
                    }
                }
                if (state.displayName.isEmpty()) {
                    item {
                        Text(
                            "No display name set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("profile.empty"),
                        )
                    }
                }
                item { SectionHeader("Personal", Modifier.padding(top = Spacing.s)) }
                item {
                    GroupedCard {
                        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text("Display name", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = state.displayName,
                                onValueChange = viewModel::onDisplayNameChange,
                                label = { Text("Display name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("profile.displayName"),
                            )
                        }
                        IosRow(
                            title = "Currency",
                            value = state.currency,
                            icon = Icons.Filled.AttachMoney,
                            iconTint = Color(0xFF1F7F47),
                            onClick = { currencySearch = ""; showCurrencyPicker = true },
                            divider = false,
                            modifier = Modifier.testTag("profile.currency"),
                        )
                    }
                }
                item { SectionHeader("Account") }
                item {
                    GroupedCard {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            if (state.success) {
                                Text("Profile saved.", color = Color(0xFF1F7F47), modifier = Modifier.testTag("profile.success"))
                            }
                            if (state.error != null) {
                                TextButton(onClick = viewModel::load, modifier = Modifier.testTag("profile.retry")) { Text("Try again") }
                            }
                            Box(Modifier.fillMaxWidth()) {
                                PillButton(
                                    text = "Save",
                                    onClick = viewModel::save,
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().testTag("profile.save"),
                                )
                                if (state.isSaving) CircularProgressIndicator(
                                    Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCurrencyPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCurrencyPicker = false },
            shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
                Text("Currency", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = Spacing.s))
                OutlinedTextField(
                    value = currencySearch,
                    onValueChange = { currencySearch = it },
                    label = { Text("Search currencies") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s).testTag("profile.currency.search"),
                )
                val query = currencySearch.trim()
                val matches = currencyOptions.filter {
                    it.code.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
                }
                LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    if (query.isEmpty()) {
                        item { SectionHeader("Popular", Modifier.padding(start = 0.dp, top = Spacing.s)) }
                        items(popularCurrencyCodes.mapNotNull { code -> currencyOptions.firstOrNull { it.code == code } }) { option ->
                            currencyRow(option, state.currency) {
                                viewModel.onCurrencyChange(option.code)
                                showCurrencyPicker = false
                            }
                        }
                        item { SectionHeader("All currencies", Modifier.padding(start = 0.dp, top = Spacing.s)) }
                        items(currencyOptions.filterNot { it.code in popularCurrencyCodes }) { option ->
                            currencyRow(option, state.currency) {
                                viewModel.onCurrencyChange(option.code)
                                showCurrencyPicker = false
                            }
                        }
                    } else {
                        items(matches) { option ->
                            currencyRow(option, state.currency) {
                                viewModel.onCurrencyChange(option.code)
                                showCurrencyPicker = false
                            }
                        }
                        if (matches.isEmpty()) item { Text("No currencies found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.m).testTag("profile.currency.empty")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun currencyRow(option: CurrencyOption, selectedCode: String, onClick: () -> Unit) {
    IosRow(
        title = option.code,
        subtitle = option.name,
        icon = Icons.Filled.AttachMoney,
        iconTint = Color(0xFF1F7F47),
        trailing = { if (option.code == selectedCode) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        onClick = onClick,
        chevron = false,
        modifier = Modifier.testTag("profile.currency.option.${option.code}"),
    )
}

@Composable
private fun ProfileHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Text("Profile", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
    }
}
