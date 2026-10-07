package com.spenditos.android.feature.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.CategorySymbolBadge
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

private val categoryColors = listOf(
    "#B85F42", "#B86A6A", "#7E6A8D", "#5D738B", "#3F7D70",
    "#6F8C76", "#B59A63", "#8D6D58", "#747A83", "#8A7BA8",
)

private val categoryIcons = listOf("🍽️", "🛒", "🏠", "🚗", "🎁", "💼", "💰", "❤️", "📚", "✈️", "🐾", "🏷️")

private data class IconLibraryItem(val value: String, val name: String, val group: String, val keywords: String = "")

private fun legacyIcons(group: String, values: List<String>) =
    values.map { IconLibraryItem(it, "Other $group icon", group, group) }

private val legacyIconLibrary =
    legacyIcons("Money", listOf("💹", "💸", "💲", "🧮")) +
        legacyIcons("Food & Drink", listOf("🍛", "🍱", "🥘", "🍝", "🍺", "🧃", "🍵", "🥛", "🧁", "🍩", "🍫", "🥑", "🍌", "🌽", "🍇", "🥝", "🍋", "🍊", "🍐", "🍍", "🥩", "🍗", "🌯", "🌮", "🥙", "🍤")) +
        legacyIcons("Home & Life", listOf("🏡", "🧽", "🚪", "🪞", "🧯", "🕯️", "🪣", "🪥", "🧻")) +
        legacyIcons("Transport", listOf("🚂", "🛩️", "🚀", "🚁", "🚤", "⛵", "🗺️", "⛺", "🌍", "🌋", "🏔️", "🏎️", "🏍️", "🛵", "🚐", "🚚")) +
        legacyIcons("Shopping", listOf("👠", "🧥", "👔", "🎩", "👜", "🧣", "🧤", "🧦", "👞")) +
        legacyIcons("Health & Care", listOf("👁️", "🧘‍♂️", "🧘‍♀️", "🚴", "⛹️", "🤸", "🎾", "🏸", "🏓")) +
        legacyIcons("Entertainment", listOf("🎪", "🎢", "🎰", "🕹️", "🧩", "♟️", "🖼️", "🎥", "🎶", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸", "🎻")) +
        legacyIcons("People & Learning", listOf("📝", "🏫", "🔭")) +
        legacyIcons("Nature & Pets", listOf("🐰", "🐹", "🦊", "🐻", "🐼", "🦁", "🐯", "🦄", "🐘", "🦋", "🐢", "🐬", "🦜", "🐸", "🐝", "🌺", "🌻", "🌹", "🌷", "🍀", "🌴", "🌲", "🍁", "🌈")) +
        legacyIcons("Symbols", listOf("💫", "❄️", "🌀", "☮️", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "🟤", "⚫", "⚪", "🔶", "🔷", "🏁", "🚩", "⚓", "🌈", "🎆", "🎊"))

private val iconLibrary = listOf(
    IconLibraryItem("🏷️", "Tag", "Symbols", "label other"), IconLibraryItem("💰", "Money", "Money", "income salary pay earnings"),
    IconLibraryItem("💳", "Card", "Money", "credit debit bank"), IconLibraryItem("🏦", "Bank", "Money", "banking account finance"),
    IconLibraryItem("💵", "Cash", "Money", "dollar payment"), IconLibraryItem("🪙", "Coins", "Money", "change savings"),
    IconLibraryItem("📊", "Planning", "Money", "spending"), IconLibraryItem("📈", "Growth", "Money", "investment stocks"),
    IconLibraryItem("📉", "Downturn", "Money", "loss debt"), IconLibraryItem("🧾", "Receipt", "Money", "bill invoice purchase"),
    IconLibraryItem("🎁", "Gift", "Money", "present giving charity"), IconLibraryItem("💼", "Work", "Work & Tech", "business office salary"),
    IconLibraryItem("🧑‍💻", "Developer", "Work & Tech", "apps software code"), IconLibraryItem("💻", "Computer", "Work & Tech", "apps technology"),
    IconLibraryItem("🖥️", "Desktop", "Work & Tech", "computer software"), IconLibraryItem("⌨️", "Code", "Work & Tech", "developer software"),
    IconLibraryItem("📡", "Network", "Work & Tech", "internet wifi"), IconLibraryItem("📁", "Files", "Work & Tech", "documents office"),
    IconLibraryItem("🍔", "Burger", "Food & Drink", "fast food lunch meal"), IconLibraryItem("🍕", "Pizza", "Food & Drink", "restaurant takeout"),
    IconLibraryItem("🍣", "Sushi", "Food & Drink", "restaurant meal"), IconLibraryItem("🍜", "Noodles", "Food & Drink", "restaurant"),
    IconLibraryItem("🍲", "Meal", "Food & Drink", "food dinner lunch"), IconLibraryItem("🥗", "Salad", "Food & Drink", "healthy"),
    IconLibraryItem("🍎", "Fruit", "Food & Drink", "produce snack"), IconLibraryItem("🥕", "Produce", "Food & Drink", "vegetables healthy"),
    IconLibraryItem("🛒", "Groceries", "Food & Drink", "grocery shopping"), IconLibraryItem("☕", "Coffee", "Food & Drink", "cafe drink"),
    IconLibraryItem("🍪", "Snack", "Food & Drink", "treat coffee"), IconLibraryItem("🍰", "Dessert", "Food & Drink", "cake treat"),
    IconLibraryItem("🏠", "Home", "Home & Life", "rent mortgage housing"), IconLibraryItem("🏢", "Building", "Home & Life", "rent office"),
    IconLibraryItem("🔑", "Key", "Home & Life", "locksmith"), IconLibraryItem("💡", "Utilities", "Home & Life", "electricity bills"),
    IconLibraryItem("🛋️", "Furniture", "Home & Life", "decor"), IconLibraryItem("🛏️", "Bedroom", "Home & Life", "furniture"),
    IconLibraryItem("🧹", "Cleaning", "Home & Life", "household supplies"), IconLibraryItem("🪴", "Plants", "Home & Life", "garden"),
    IconLibraryItem("🚗", "Car", "Transport", "vehicle driving"), IconLibraryItem("⛽", "Fuel", "Transport", "gas petrol"),
    IconLibraryItem("🚌", "Bus", "Transport", "transit public"), IconLibraryItem("🚆", "Train", "Transport", "rail transit"),
    IconLibraryItem("🚕", "Taxi", "Transport", "ride rideshare"), IconLibraryItem("🚲", "Bike", "Transport", "cycling"),
    IconLibraryItem("✈️", "Flight", "Transport", "airfare travel vacation"), IconLibraryItem("🧳", "Travel", "Transport", "vacation trip hotel"),
    IconLibraryItem("🛍️", "Shopping", "Shopping", "store retail"), IconLibraryItem("👕", "Clothing", "Shopping", "apparel fashion"),
    IconLibraryItem("👟", "Shoes", "Shopping", "apparel fashion"), IconLibraryItem("💄", "Beauty", "Shopping", "makeup cosmetics"),
    IconLibraryItem("💍", "Jewelry", "Shopping", "accessories luxury"), IconLibraryItem("📦", "Delivery", "Shopping", "package online"),
    IconLibraryItem("📱", "Phone", "Shopping", "mobile technology"), IconLibraryItem("❤️", "Health", "Health & Care", "medical wellness doctor"),
    IconLibraryItem("💊", "Medicine", "Health & Care", "pharmacy prescription"), IconLibraryItem("🏥", "Hospital", "Health & Care", "clinic doctor"),
    IconLibraryItem("🩺", "Doctor", "Health & Care", "medical clinic"), IconLibraryItem("🏋️", "Fitness", "Health & Care", "gym exercise"),
    IconLibraryItem("🧘", "Wellness", "Health & Care", "meditation yoga"), IconLibraryItem("🧠", "Mental Health", "Health & Care", "therapy"),
    IconLibraryItem("🎮", "Games", "Entertainment", "gaming console"), IconLibraryItem("🎬", "Movies", "Entertainment", "cinema film"),
    IconLibraryItem("🎵", "Music", "Entertainment", "audio concert"), IconLibraryItem("🎧", "Audio", "Entertainment", "music podcast"),
    IconLibraryItem("🎨", "Art", "Entertainment", "creative hobby"), IconLibraryItem("🎟️", "Tickets", "Entertainment", "event show"),
    IconLibraryItem("📚", "Books", "People & Learning", "reading education"), IconLibraryItem("👨‍👩‍👧‍👦", "Family", "People & Learning", "children care"),
    IconLibraryItem("👶", "Childcare", "People & Learning", "baby daycare"), IconLibraryItem("🎓", "School", "People & Learning", "education tuition"),
    IconLibraryItem("🐾", "Pets", "Nature & Pets", "animals dog cat"), IconLibraryItem("🐕", "Dog", "Nature & Pets", "pet animal"),
    IconLibraryItem("🐈", "Cat", "Nature & Pets", "pet animal"), IconLibraryItem("🌿", "Nature", "Nature & Pets", "outdoors garden"),
    IconLibraryItem("🌸", "Flowers", "Nature & Pets", "plants garden"), IconLibraryItem("🌊", "Water", "Nature & Pets", "beach swimming"),
    IconLibraryItem("⭐", "Favorite", "Symbols", "special important"), IconLibraryItem("✨", "Sparkles", "Symbols", "special beauty"),
    IconLibraryItem("🔥", "Streak", "Symbols", "challenge progress"), IconLibraryItem("⚡", "Energy", "Symbols", "electricity power"),
    IconLibraryItem("🛡️", "Insurance", "Symbols", "coverage protection"), IconLibraryItem("🔒", "Security", "Symbols", "privacy"),
    IconLibraryItem("🏧", "ATM", "Money", "cash withdrawal"), IconLibraryItem("💎", "Luxury", "Money", "jewelry premium"),
    IconLibraryItem("🌐", "Internet", "Money", "wifi online web phone"), IconLibraryItem("📶", "Wi-Fi", "Money", "internet wireless network"),
    IconLibraryItem("🖨️", "Printer", "Work & Tech", "office work"), IconLibraryItem("🧰", "Tools", "Work & Tech", "repair technology"),
    IconLibraryItem("🧺", "Basket", "Food & Drink", "groceries grocery shopping"), IconLibraryItem("🧋", "Bubble Tea", "Food & Drink", "drink cafe snacks"),
    IconLibraryItem("🍷", "Drinks", "Food & Drink", "bar alcohol restaurant"), IconLibraryItem("🥤", "Takeaway Drink", "Food & Drink", "drink restaurant fast food"),
    IconLibraryItem("🔌", "Electricity", "Home & Life", "power utilities bills"), IconLibraryItem("🚿", "Shower", "Home & Life", "bathroom home"),
    IconLibraryItem("🧴", "Household", "Home & Life", "home supplies"), IconLibraryItem("🛁", "Bath", "Home & Life", "bathroom home"),
    IconLibraryItem("🔨", "Repairs", "Home & Life", "maintenance handyman"), IconLibraryItem("📺", "Television", "Home & Life", "streaming subscription entertainment"),
    IconLibraryItem("🧸", "Toys", "Home & Life", "children family"), IconLibraryItem("🔧", "Car Maintenance", "Transport", "repair service vehicle"),
    IconLibraryItem("🚇", "Subway", "Transport", "transit metro public transport"), IconLibraryItem("🛴", "Scooter", "Transport", "cycling transport"),
    IconLibraryItem("🚢", "Boat", "Transport", "travel cruise"), IconLibraryItem("🅿️", "Parking", "Transport", "car vehicle"),
    IconLibraryItem("🏕️", "Camping", "Transport", "travel outdoors vacation"), IconLibraryItem("👗", "Fashion", "Shopping", "clothing apparel"),
    IconLibraryItem("🎒", "Bags", "Shopping", "accessories school"), IconLibraryItem("🧸", "Toys", "Shopping", "children gifts"),
    IconLibraryItem("🖥️", "Electronics", "Shopping", "computer technology"), IconLibraryItem("🪑", "Home Goods", "Shopping", "furniture home"),
    IconLibraryItem("🩹", "First Aid", "Health & Care", "medical care"), IconLibraryItem("🦷", "Dental", "Health & Care", "dentist medical"),
    IconLibraryItem("👓", "Vision", "Health & Care", "glasses optometrist"), IconLibraryItem("🏃", "Running", "Health & Care", "exercise sport"),
    IconLibraryItem("💪", "Strength", "Health & Care", "gym exercise fitness"), IconLibraryItem("🚑", "Emergency", "Health & Care", "medical ambulance care"),
    IconLibraryItem("🧼", "Personal Care", "Health & Care", "hygiene grooming self care"), IconLibraryItem("🎤", "Concert", "Entertainment", "music event"),
    IconLibraryItem("🎭", "Theatre", "Entertainment", "show event"), IconLibraryItem("🎲", "Board Games", "Entertainment", "games hobby"),
    IconLibraryItem("🎯", "Hobbies", "Entertainment", "games activity"), IconLibraryItem("🎳", "Bowling", "Entertainment", "sport activity"),
    IconLibraryItem("📸", "Photography", "Entertainment", "camera hobby"), IconLibraryItem("👥", "People", "People & Learning", "family friends social"),
    IconLibraryItem("🤝", "Community", "People & Learning", "support giving charity"), IconLibraryItem("🧑‍🏫", "Education", "People & Learning", "school teacher learning"),
    IconLibraryItem("✏️", "Supplies", "People & Learning", "school office education"), IconLibraryItem("💌", "Card", "People & Learning", "letter gift social"),
    IconLibraryItem("💬", "Communication", "People & Learning", "phone messages social"), IconLibraryItem("🍼", "Baby", "People & Learning", "children family care"),
    IconLibraryItem("🧑‍🤝‍🧑", "Friends", "People & Learning", "social people"), IconLibraryItem("🫶", "Giving", "People & Learning", "donation charity support"),
    IconLibraryItem("🐦", "Bird", "Nature & Pets", "pet animals"), IconLibraryItem("🐟", "Fish", "Nature & Pets", "pet animals"),
    IconLibraryItem("🌳", "Outdoors", "Nature & Pets", "nature park hiking"), IconLibraryItem("🏖️", "Beach", "Nature & Pets", "travel vacation outdoors"),
    IconLibraryItem("⛰️", "Hiking", "Nature & Pets", "travel outdoors sport"), IconLibraryItem("☀️", "Sun", "Nature & Pets", "weather outdoors"),
    IconLibraryItem("🌱", "Garden", "Nature & Pets", "plants home outdoors"), IconLibraryItem("🌟", "Star", "Symbols", "favorite special"),
    IconLibraryItem("💧", "Water", "Symbols", "utilities bills"), IconLibraryItem("🎉", "Celebration", "Symbols", "party event"),
    IconLibraryItem("🎈", "Party", "Symbols", "event celebration"), IconLibraryItem("☂️", "Protection", "Symbols", "insurance coverage policy"),
    IconLibraryItem("✅", "Covered", "Symbols", "insurance protection done"), IconLibraryItem("❓", "Other", "Symbols", "miscellaneous unknown"),
) + legacyIconLibrary

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEditCategorySheet(
    category: Category?,
    initialIsIncome: Boolean,
    lockedType: Boolean? = null,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (CategoryDraft) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var color by remember(category?.id) { mutableStateOf(category?.color ?: categoryColors.first()) }
    var icon by remember(category?.id) { mutableStateOf(category?.icon) }
    var isIncome by remember(category?.id, lockedType) { mutableStateOf(lockedType ?: category?.isIncome ?: initialIsIncome) }
    var showIconLibrary by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    val quickIcons = remember(name, icon) {
        val tokens = name.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        val matched = iconLibrary.filter { item ->
            tokens.any { token -> item.name.contains(token, ignoreCase = true) || item.keywords.contains(token, ignoreCase = true) }
        }.map { it.value }
        (listOfNotNull(icon) + matched + categoryIcons).distinct().take(10)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("categories.editor.cancel")) { Text("Cancel", style = IosText.body, color = IosColors.textSecondary) }
                Text(
                    if (category == null) "New Category" else "Edit Category",
                    style = IosText.headline,
                    color = IosColors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(64.dp))
            }

            error?.let { ErrorBanner(it, Modifier.testTag("categories.editor.error")) }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Name")
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = IosText.body.copy(color = IosColors.textPrimary),
                    modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m).testTag("categories.editor.name"),
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) Text("Category name", style = IosText.body, color = IosColors.textTertiary)
                            inner()
                        }
                    },
                )
            }

            if (lockedType == null) Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Type")
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().then(subtleGlassModifier()).clickable { typeMenu = true }
                            .padding(Spacing.m).testTag(if (isIncome) "categories.editor.type.income" else "categories.editor.type.expense"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Icon(
                            SfSymbols.icon(if (isIncome) "arrow.down" else "arrow.up"),
                            contentDescription = null,
                            tint = IosColors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(if (isIncome) "Income" else "Expense", style = IosText.body, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                        Icon(SfSymbols.icon("chevron.down"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Expense", style = IosText.body) },
                            leadingIcon = { Icon(SfSymbols.icon("arrow.up"), contentDescription = null) },
                            onClick = { isIncome = false; typeMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Income", style = IosText.body) },
                            leadingIcon = { Icon(SfSymbols.icon("arrow.down"), contentDescription = null) },
                            onClick = { isIncome = true; typeMenu = false },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Color")
                Column(Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    categoryColors.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            row.forEach { option ->
                                ColorChoice(option, selected = color == option, modifier = Modifier.weight(1f)) { color = option }
                            }
                            repeat(5 - row.size) { Box(Modifier.weight(1f).size(44.dp)) }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Icon")
                Column(Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        quickIcons.forEach { option ->
                            IconChoice(option, selected = icon == option, modifier = Modifier.testTag("categories.editor.icon.$option")) { icon = option }
                        }
                        IconChoice("None", selected = icon == null, modifier = Modifier.testTag("categories.editor.icon.none")) { icon = null }
                    }
                }
                TextButton(onClick = { showIconLibrary = true }, modifier = Modifier.testTag("categories.editor.browseIcons")) {
                    Icon(SfSymbols.icon("square.grid.3x3"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(16.dp))
                    Text("Browse all icons", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.spendOrange, modifier = Modifier.padding(start = Spacing.s))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Preview")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    CategorySymbolBadge(icon ?: CategoryIcons.DEFAULT, name.ifEmpty { "Category" }, color, size = 40.dp)
                    Text(name.ifEmpty { "Category Name" }, style = IosText.callout, color = IosColors.textPrimary)
                }
            }

            val saveLabel = if (category == null) "Create Category" else "Save Changes"
            if (isSaving) {
                SavingPill(saveLabel, Modifier.fillMaxWidth().testTag("categories.editor.save"))
            } else {
                PillButton(
                    text = saveLabel,
                    onClick = { onSave(CategoryDraft(name, color, icon, isIncome)) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("categories.editor.save"),
                )
            }
        }
    }

    if (showIconLibrary) {
        CategoryIconBrowser(
            categoryName = name,
            selectedIcon = icon,
            onSelect = { icon = it; showIconLibrary = false },
            onDismiss = { showIconLibrary = false },
        )
    }
}

@Composable
private fun SavingPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(Radius.m)).background(IosColors.spendOrange.copy(alpha = 0.72f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        Text(text, style = IosText.headline, color = Color.White, modifier = Modifier.padding(start = Spacing.s))
    }
}

@Composable
private fun ColorChoice(hex: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = categorySwatch(hex)
    Box(
        modifier.size(44.dp).clip(CircleShape).background(tint)
            .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }
            .testTag("categories.editor.color.$hex"),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun IconChoice(value: String, selected: Boolean, modifier: Modifier = Modifier, showsLabel: Boolean = false, onClick: () -> Unit) {
    Column(Modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box {
            Box(
                modifier.size(52.dp).clip(RoundedCornerShape(Radius.s))
                    .background(if (selected) IosColors.spendOrange.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(if (selected) 1.5.dp else 0.5.dp, if (selected) IosColors.spendOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.s))
                    .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                if (value == "None") Text("None", style = IosText.caption2, color = IosColors.textSecondary)
                else Icon(CategoryIcons.symbol(value, iconLibrary.firstOrNull { it.value == value }?.name), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(24.dp))
            }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (showsLabel && value != "None") {
            Text(
                iconLibrary.firstOrNull { it.value == value }?.name ?: "Current icon",
                style = IosText.caption2,
                color = IosColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CategoryIconBrowser(categoryName: String, selectedIcon: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var searchText by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val groups = listOf("All", "Money", "Work & Tech", "Food & Drink", "Home & Life", "Transport", "Shopping", "Health & Care", "Entertainment", "People & Learning", "Nature & Pets", "Symbols")
    val query = searchText.trim()
    val icons = iconLibrary.filter { item ->
        (selectedGroup == null || item.group == selectedGroup) &&
            (query.isEmpty() || listOf(item.name, item.group, item.keywords).any { it.contains(query, ignoreCase = true) } || query.split(' ').all { token -> listOf(item.name, item.group, item.keywords).any { it.contains(token, ignoreCase = true) } })
    }
    val suggested = remember(categoryName, selectedIcon) {
        (listOfNotNull(selectedIcon) + iconLibrary.filter { item ->
            categoryName.isNotBlank() && (item.name.contains(categoryName, ignoreCase = true) || item.keywords.contains(categoryName, ignoreCase = true))
        }.map { it.value } + listOf("🏷️", "🍔", "🏠", "🚗", "🛍️", "❤️", "🎮", "📚", "🐾", "⭐", "💰", "☕")).distinct().take(12)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp))
                Text("Choose Icon", style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = IosColors.spendOrange) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).then(subtleGlassModifier()).padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    textStyle = IosText.callout.copy(color = IosColors.textPrimary),
                    modifier = Modifier.weight(1f).testTag("categories.iconSearch"),
                    decorationBox = { inner -> Box { if (searchText.isEmpty()) Text("Search icons", style = IosText.callout, color = IosColors.textTertiary); inner() } },
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                groups.forEach { group ->
                    val selected = if (group == "All") selectedGroup == null else selectedGroup == group
                    val symbol = when (group) {
                        "All" -> "square.grid.2x2"
                        "Money" -> "dollarsign.circle"
                        "Work & Tech" -> "laptopcomputer"
                        "Food & Drink" -> "fork.knife"
                        "Home & Life" -> "house"
                        "Transport" -> "car"
                        "Shopping" -> "bag"
                        "Health & Care" -> "heart"
                        "Entertainment" -> "play.rectangle"
                        "People & Learning" -> "person.2"
                        "Nature & Pets" -> "leaf"
                        else -> "sparkles"
                    }
                    Row(
                        Modifier.clip(CircleShape).background(if (selected) IosColors.spendOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(role = Role.Tab) { selectedGroup = if (group == "All") null else group }
                            .border(1.dp, if (selected) IosColors.spendOrange.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("categories.iconGroup.${group.lowercase().replace(' ', '-') }"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(SfSymbols.icon(symbol), contentDescription = null, tint = if (selected) Color.White else IosColors.textSecondary, modifier = Modifier.size(14.dp))
                        Text(group, style = IosText.caption.w(FontWeight.SemiBold), color = if (selected) Color.White else IosColors.textSecondary)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                if (query.isEmpty() && selectedGroup == null) {
                    IconBrowserSection(if (categoryName.isBlank()) "Popular" else "Suggested for “$categoryName”", suggested, selectedIcon, onSelect)
                    IconBrowserSection("All icons", iconLibrary.map { it.value }.distinct(), selectedIcon, onSelect)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedGroup ?: "Search results", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary, modifier = Modifier.weight(1f))
                        Text(icons.map { it.value }.distinct().size.toString(), style = IosText.caption, color = IosColors.textTertiary)
                    }
                    if (icons.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(28.dp))
                            Text("No icons found", style = IosText.body, color = IosColors.textSecondary)
                            Text("Try a broader word, like food, home, or travel.", style = IosText.caption, color = IosColors.textTertiary)
                        }
                    } else IconBrowserGrid(icons.map { it.value }.distinct(), selectedIcon, onSelect)
                }
            }
        }
    }
}

@Composable
private fun IconBrowserSection(title: String, values: List<String>, selectedIcon: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
        IconBrowserGrid(values, selectedIcon, onSelect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconBrowserGrid(values: List<String>, selectedIcon: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        values.forEach { value ->
            Column(Modifier.width(72.dp).clickable(role = Role.Button) { onSelect(value) }.testTag("categories.iconOption.$value"), horizontalAlignment = Alignment.CenterHorizontally) {
                IconChoice(value, selectedIcon == value, showsLabel = true) { onSelect(value) }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(text, style = IosText.subheadline, color = IosColors.textSecondary, modifier = Modifier.padding(start = Spacing.xs))
}

private fun categorySwatch(hex: String): Color = Color(runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.GRAY))
