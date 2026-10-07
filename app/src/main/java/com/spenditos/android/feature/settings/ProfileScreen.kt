package com.spenditos.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

data class CurrencyOption(val code: String, val name: String)

private val popularCurrencyCodes = listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR")

private val currencyOptions = listOf(
    CurrencyOption("AED", "UAE Dirham"), CurrencyOption("AFN", "Afghan Afghani"), CurrencyOption("ALL", "Albanian Lek"),
    CurrencyOption("AMD", "Armenian Dram"), CurrencyOption("ANG", "Netherlands Antillean Guilder"), CurrencyOption("AOA", "Angolan Kwanza"),
    CurrencyOption("ARS", "Argentine Peso"), CurrencyOption("AUD", "Australian Dollar"),
    CurrencyOption("AWG", "Aruban Florin"), CurrencyOption("AZN", "Azerbaijani Manat"), CurrencyOption("BAM", "Bosnia-Herzegovina Convertible Mark"),
    CurrencyOption("BBD", "Barbadian Dollar"),
    CurrencyOption("BDT", "Bangladeshi Taka"), CurrencyOption("BGN", "Bulgarian Lev"),
    CurrencyOption("BHD", "Bahraini Dinar"), CurrencyOption("BIF", "Burundian Franc"), CurrencyOption("BMD", "Bermudian Dollar"),
    CurrencyOption("BND", "Brunei Dollar"), CurrencyOption("BOB", "Bolivian Boliviano"), CurrencyOption("BRL", "Brazilian Real"), CurrencyOption("BSD", "Bahamian Dollar"),
    CurrencyOption("BTN", "Bhutanese Ngultrum"), CurrencyOption("BWP", "Botswana Pula"),
    CurrencyOption("BYN", "Belarusian Ruble"), CurrencyOption("BZD", "Belize Dollar"), CurrencyOption("CAD", "Canadian Dollar"),
    CurrencyOption("CDF", "Congolese Franc"), CurrencyOption("CHF", "Swiss Franc"), CurrencyOption("CLP", "Chilean Peso"),
    CurrencyOption("CNY", "Chinese Yuan"), CurrencyOption("COP", "Colombian Peso"), CurrencyOption("CRC", "Costa Rican Colón"),
    CurrencyOption("CUP", "Cuban Peso"), CurrencyOption("CVE", "Cape Verdean Escudo"), CurrencyOption("CZK", "Czech Koruna"),
    CurrencyOption("DJF", "Djiboutian Franc"), CurrencyOption("DKK", "Danish Krone"), CurrencyOption("DOP", "Dominican Peso"),
    CurrencyOption("DZD", "Algerian Dinar"), CurrencyOption("EGP", "Egyptian Pound"), CurrencyOption("ERN", "Eritrean Nakfa"),
    CurrencyOption("ETB", "Ethiopian Birr"), CurrencyOption("EUR", "Euro"), CurrencyOption("FJD", "Fijian Dollar"),
    CurrencyOption("FKP", "Falkland Islands Pound"), CurrencyOption("GBP", "British Pound"), CurrencyOption("GEL", "Georgian Lari"),
    CurrencyOption("GHS", "Ghanaian Cedi"), CurrencyOption("GMD", "Gambian Dalasi"),
    CurrencyOption("GIP", "Gibraltar Pound"), CurrencyOption("GNF", "Guinean Franc"), CurrencyOption("GTQ", "Guatemalan Quetzal"), CurrencyOption("GYD", "Guyanese Dollar"),
    CurrencyOption("HKD", "Hong Kong Dollar"), CurrencyOption("HNL", "Honduran Lempira"), CurrencyOption("HTG", "Haitian Gourde"),
    CurrencyOption("HUF", "Hungarian Forint"), CurrencyOption("IDR", "Indonesian Rupiah"), CurrencyOption("ILS", "Israeli Shekel"),
    CurrencyOption("INR", "Indian Rupee"), CurrencyOption("IQD", "Iraqi Dinar"), CurrencyOption("IRR", "Iranian Rial"),
    CurrencyOption("ISK", "Icelandic Króna"), CurrencyOption("JMD", "Jamaican Dollar"), CurrencyOption("JOD", "Jordanian Dinar"),
    CurrencyOption("JPY", "Japanese Yen"), CurrencyOption("KES", "Kenyan Shilling"), CurrencyOption("KGS", "Kyrgystani Som"),
    CurrencyOption("KHR", "Cambodian Riel"), CurrencyOption("KMF", "Comorian Franc"), CurrencyOption("KPW", "North Korean Won"),
    CurrencyOption("KRW", "South Korean Won"), CurrencyOption("KWD", "Kuwaiti Dinar"),
    CurrencyOption("KYD", "Cayman Islands Dollar"), CurrencyOption("KZT", "Kazakhstani Tenge"), CurrencyOption("LAK", "Lao Kip"), CurrencyOption("LBP", "Lebanese Pound"),
    CurrencyOption("LKR", "Sri Lankan Rupee"), CurrencyOption("LRD", "Liberian Dollar"), CurrencyOption("LSL", "Lesotho Loti"),
    CurrencyOption("LYD", "Libyan Dinar"), CurrencyOption("MAD", "Moroccan Dirham"), CurrencyOption("MDL", "Moldovan Leu"),
    CurrencyOption("MGA", "Malagasy Ariary"), CurrencyOption("MKD", "Macedonian Denar"), CurrencyOption("MMK", "Myanmar Kyat"),
    CurrencyOption("MNT", "Mongolian Tugrik"), CurrencyOption("MOP", "Macanese Pataca"), CurrencyOption("MRU", "Mauritanian Ouguiya"),
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
    CurrencyOption("SGD", "Singapore Dollar"), CurrencyOption("SHP", "Saint Helena Pound"), CurrencyOption("SLE", "Sierra Leonean Leone"),
    CurrencyOption("SLL", "Sierra Leonean Leone (old)"),
    CurrencyOption("SOS", "Somali Shilling"), CurrencyOption("SRD", "Surinamese Dollar"),
    CurrencyOption("SSP", "South Sudanese Pound"), CurrencyOption("STN", "São Tomé Dobra"),
    CurrencyOption("SYP", "Syrian Pound"),
    CurrencyOption("SZL", "Swazi Lilangeni"), CurrencyOption("THB", "Thai Baht"), CurrencyOption("TJS", "Tajikistani Somoni"),
    CurrencyOption("TMT", "Turkmenistani Manat"), CurrencyOption("TND", "Tunisian Dinar"), CurrencyOption("TOP", "Tongan Paʻanga"),
    CurrencyOption("TRY", "Turkish Lira"), CurrencyOption("TTD", "Trinidad and Tobago Dollar"),
    CurrencyOption("TVD", "Tuvaluan Dollar"), CurrencyOption("TWD", "New Taiwan Dollar"), CurrencyOption("TZS", "Tanzanian Shilling"), CurrencyOption("UAH", "Ukrainian Hryvnia"),
    CurrencyOption("UGX", "Ugandan Shilling"), CurrencyOption("USD", "US Dollar"), CurrencyOption("UYU", "Uruguayan Peso"),
    CurrencyOption("UZS", "Uzbekistani Som"), CurrencyOption("VED", "Venezuelan Bolívar"), CurrencyOption("VES", "Venezuelan Bolívar Soberano"),
    CurrencyOption("VND", "Vietnamese Đồng"), CurrencyOption("VUV", "Vanuatu Vatu"), CurrencyOption("WST", "Samoan Tala"),
    CurrencyOption("XAF", "Central African CFA Franc"), CurrencyOption("XCD", "East Caribbean Dollar"),
    CurrencyOption("XOF", "West African CFA Franc"), CurrencyOption("XPF", "CFP Franc"), CurrencyOption("YER", "Yemeni Rial"),
    CurrencyOption("ZAR", "South African Rand"), CurrencyOption("ZMW", "Zambian Kwacha"), CurrencyOption("ZWL", "Zimbabwean Dollar"),
)

@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenChangePassword: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var currencySearch by remember { mutableStateOf("") }
    val finance = LocalFinanceColors.current

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
                            Text(message, style = IosText.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.m).testTag("profile.error"))
                        }
                    }
                }
                if (state.isOffline) {
                    item {
                        Text(
                            "Check your connection and try again.",
                            style = IosText.footnote,
                            color = IosColors.textSecondary,
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("profile.offline"),
                        )
                    }
                }
                item { SectionHeader("Personal", Modifier.padding(top = Spacing.s)) }
                item {
                    GroupedCard {
                        IosRow(
                            title = "Display Name",
                            icon = SfSymbols.icon("person.fill"),
                            iconTint = IosColors.spendOrange,
                            chevron = false,
                            trailing = {
                                BasicTextField(
                                    value = state.displayName,
                                    onValueChange = viewModel::onDisplayNameChange,
                                    singleLine = true,
                                    textStyle = IosText.subheadline.copy(color = IosColors.textPrimary, textAlign = TextAlign.End),
                                    modifier = Modifier.width(160.dp).testTag("profile.displayName"),
                                    decorationBox = { inner ->
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                            if (state.displayName.isEmpty()) {
                                                Text("Not set", style = IosText.subheadline, color = IosColors.textTertiary, modifier = Modifier.testTag("profile.empty"))
                                            }
                                            inner()
                                        }
                                    },
                                )
                            },
                        )
                        IosRow(
                            title = "Currency",
                            value = state.currency,
                            icon = SfSymbols.icon("dollarsign.circle.fill"),
                            iconTint = finance.income,
                            onClick = { currencySearch = ""; showCurrencyPicker = true },
                            divider = false,
                            modifier = Modifier.testTag("profile.currency"),
                        )
                    }
                }
                item { SectionHeader("Security") }
                item {
                    GroupedCard {
                        IosRow(
                            "Change Password",
                            icon = SfSymbols.icon("lock.fill"),
                            iconTint = IosColors.info,
                            onClick = onOpenChangePassword,
                            divider = false,
                            modifier = Modifier.testTag("profile.changePassword"),
                        )
                    }
                }
                item { SectionHeader("Account") }
                item {
                    GroupedCard {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            if (state.success) {
                                Text("Profile saved.", style = IosText.footnote, color = finance.income, modifier = Modifier.testTag("profile.success"))
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
                Text("Currency", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s))
                Row(
                    Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
                    BasicTextField(
                    value = currencySearch,
                    onValueChange = { currencySearch = it },
                    singleLine = true,
                    textStyle = IosText.body.copy(color = IosColors.textPrimary),
                    modifier = Modifier.weight(1f).testTag("profile.currency.search"),
                    decorationBox = { inner ->
                        Box {
                            if (currencySearch.isEmpty()) Text("Search 180+ currencies", style = IosText.body, color = IosColors.textTertiary)
                            inner()
                        }
                    },
                    )
                }
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
                        item { SectionHeader("All Currencies", Modifier.padding(start = 0.dp, top = Spacing.s)) }
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
                        if (matches.isEmpty()) item { Text("No currencies found.", style = IosText.body, color = IosColors.textSecondary, modifier = Modifier.padding(Spacing.m).testTag("profile.currency.empty")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun currencyRow(option: CurrencyOption, selectedCode: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs).testTag("profile.currency.option.${option.code}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(option.code, style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.width(50.dp))
        Text(option.name, style = IosText.callout, color = IosColors.textSecondary, modifier = Modifier.weight(1f))
        if (option.code == selectedCode) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = IosColors.spendOrange)
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text("Profile", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
    }
}
