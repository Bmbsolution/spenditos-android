# Porting a SwiftUI screen to Compose (visual parity)

The Android app must look like the iOS app (`../spenditos-ios/Spenditos/Spenditos`). Port each view from its
SwiftUI source, value by value. Do not restyle, and do not invent a design. When the Swift says
`.padding(14)`, `.font(.caption.weight(.semibold))`, `spacing: 10` or `cornerRadius: size * 0.3`, the
Compose code uses the same numbers.

## Scope rules

- Change only the UI layer (composables). Keep the view models, repositories, navigation routes, callbacks,
  and every `testTag`, `contentDescription` and semantics that already exist. Behavior must not change.
- If iOS shows an element that Android has no data for, leave it out; do not change the data layer. If iOS
  lacks something Android has (a feature Android needs), keep it, styled like its iOS neighbors.
- Shared pieces live in `ui/theme/Theme.kt` and `ui/components/` (`IosKit.kt`, `SfSymbols.kt`,
  `CategoryIcons.kt`). Use them; do not edit them in a screen port. Put screen-only helpers in the
  feature package.

## Units

1 SwiftUI point = 1 dp = 1 sp at the default text size. Copy numbers as-is.

## Type (`ui/theme/Theme.kt` → `IosText`)

| SwiftUI | Compose |
|---|---|
| `.largeTitle` `.title` `.title2` `.title3` | `IosText.largeTitle` (34) `.title` (28) `.title2` (22) `.title3` (20) |
| `.headline` | `IosText.headline` (17 semibold) |
| `.body` `.callout` `.subheadline` | `IosText.body` (17) `.callout` (16) `.subheadline` (15) |
| `.footnote` `.caption` `.caption2` | `IosText.footnote` (13) `.caption` (12) `.caption2` (11) |
| `.x.weight(.semibold)` | `IosText.x.w(FontWeight.SemiBold)` |
| `.system(size: n, weight: w)` | `IosText.system(n, w)` |
| `.system(size: n, weight: w, design: .rounded)` or `.monospacedDigit()` | `IosText.rounded(n, w)` |
| App aliases (Typography.swift): `displayLarge` | `IosText.largeTitle.w(Black)` |
| `headingL` `headingM` `headingS` | `title2.w(Bold)` `title3.w(SemiBold)` `headline` |
| `bodyL` `bodyM` `bodyS` | `body` `callout` `subheadline` |
| `amountLarge` `amountMedium` `amountSmall` `amountTitle` | `rounded(34, Bold)` `rounded(22, SemiBold)` `rounded(17, Medium)` `rounded(20, SemiBold)` |

Weights: `.medium` Medium, `.semibold` SemiBold, `.bold` Bold, `.heavy` ExtraBold, `.black` Black.
`.textCase(.uppercase)` uppercases the string; `.tracking(t)` is `letterSpacing = t.sp`.
`.lineLimit(n)` is `maxLines = n` with `overflow = TextOverflow.Ellipsis`. `.minimumScaleFactor` can be
left out, or use `autoSize = TextAutoSize.StepBased(...)` on `BasicText`.

## Color (`IosColors`, `MaterialTheme.colorScheme`)

| SwiftUI | Compose |
|---|---|
| `.textPrimary` / `.primary` | `IosColors.textPrimary` |
| `.textSecondary` / `.secondary` | `IosColors.textSecondary` |
| `.textTertiary` | `IosColors.textTertiary` |
| `.spendOrange` / `.accentColor` / `.tint` | `IosColors.spendOrange` (or `MaterialTheme.colorScheme.primary`, which adapts to dark) |
| `.spendGold` `.info` `.warning` `.neutral` | `IosColors.spendGold` `.info` `.warning` `.neutral` |
| `.income` `.expense` | `IosColors.income` `IosColors.expense` |
| `.appBackground` (systemGroupedBackground) | `MaterialTheme.colorScheme.background` |
| `.cardBackground` / `.surfaceSecondary` | `MaterialTheme.colorScheme.surfaceContainer` |
| `Color(hex: s)` | `categoryColor(s)` (`ui/components/CategoryColor.kt`) or `Color(0xFFRRGGBB)` |
| `c.opacity(a)` | `c.copy(alpha = a)` |
| `Color(uiColor: .separator)` / `Divider()` | `IosColors.separator` / `HorizontalDivider(color = IosColors.separator)` |

## Surfaces and shapes

| SwiftUI | Compose |
|---|---|
| `.glassBackground()` / `.glassCard()` / `GlassCard { }` | `Modifier.then(glassCardModifier())` / `GlassCard { }` (radius 24) |
| `.glassBackground(cornerRadius: r)` | `glassCardModifier(RoundedCornerShape(r.dp))` |
| `.subtleGlass()` | `subtleGlassModifier()` (radius 16) |
| `.elevatedGlass()` | `glassCardModifier(shadowAlpha = 0.12f)` |
| `RoundedRectangle(cornerRadius: r, style: .continuous)` | `RoundedCornerShape(r.dp)` |
| `Capsule()` | `RoundedCornerShape(50)` / `CircleShape` |
| `.shadow(color: c.opacity(a), radius: r, y: y)` | `Modifier.dropShadow(shape, Shadow(radius = (2 * r).dp, color = c, offset = DpOffset(0.dp, y.dp), alpha = a))` |
| `LinearGradient(colors:, startPoint: .topLeading, endPoint: .bottomTrailing)` | `Brush.linearGradient(colors)` (`.top`→`.bottom` is `Brush.verticalGradient`, `.leading`→`.trailing` is `Brush.horizontalGradient`) |
| `.overlay(RoundedRectangle(...).stroke(c, lineWidth: w))` | `Modifier.border(w.dp, c, shape)` |
| `AppTheme.spacingXS/S/M/L/XL` | 4 / 8 / 16 / 24 / 32 dp |
| `AppTheme.radiusS/M/L/XL` | `Radius.s/m/l/xl` (10 / 16 / 24 / 32) |
| `AppTheme.screenPadding` `cardPadding` | 20 dp, 16 dp |

## Layout

- `VStack(alignment:, spacing: s)` → `Column(verticalArrangement = Arrangement.spacedBy(s.dp), horizontalAlignment = ...)`.
  `HStack` → `Row(horizontalArrangement = Arrangement.spacedBy(s.dp), verticalAlignment = ...)`.
  When SwiftUI omits `spacing:`, use 8 dp.
- `.padding()` with no value is 16 dp on every edge. `.padding(.horizontal, n)` is `padding(horizontal = n.dp)`.
- `Spacer()` in a stack is `Spacer(Modifier.weight(1f))`. `Spacer(minLength: 0)` in a `VStack` with
  `.frame(minHeight:)` pushes content to the top; use `heightIn(min = …)` on the Column.
- `.frame(maxWidth: .infinity, alignment: .leading)` → `fillMaxWidth()` plus alignment.
- `.frame(width: w, height: h)` → `size(w.dp, h.dp)`.
- `HStack(alignment: .firstTextBaseline)` → `Row` with `Modifier.alignByBaseline()` on each text.
- A `List` with `.insetGrouped` is a column of `GroupedCard`s (white, radius 24 here: the app uses
  `glassBackground`), rows at least 44 dp tall, 16 dp horizontal insets, inset dividers.
- Navigation large titles: `LargeTitle(...)` (34 bold, 20 dp leading). Inline titles (`.navigationBarTitleDisplayMode(.inline)`): 17 semibold, centered.
- `.sheet` is a `ModalBottomSheet` (or a full-screen route if the Android code already uses one). `Menu` is
  a `DropdownMenu`. `Picker(.segmented)` is `IosSegmented`. `Toggle` is a `Switch` tinted with
  `IosColors.income` (iOS green #34C759 is `Color(0xFF34C759)`).
- Each screen leaves room for the floating tab bar: keep the bottom content padding it already has.

## Symbols (`ui/components/SfSymbols.kt`)

`Image(systemName: "name")` → `Icon(SfSymbols.icon("name"), contentDescription = null, tint = ...)`.
An SF symbol at font size `n` is `Modifier.size((n * 1.2).dp)`. Category icons: iOS shows the monochrome
SF symbol, not the emoji. Use `CategoryIcons.symbol(category.icon, category.name)` (or
`CategorySymbolBadge(icon, name, colorHex, size)` for the rounded tile rows use). `IconChip(icon, tint, size)`
is the iOS `IconChip`. If a symbol is missing from `SfSymbols`, use the closest `Icons.Outlined.*` (or
`Icons.Rounded.*` for `.fill`) icon directly in your file.

## Checking your port

You cannot run Gradle or the emulator. Read your diff against the Swift file once more, number by number,
before finishing. Compile errors are fixed by the reviewer, but keep imports exact (Compose BOM 2026.09,
Material3, `androidx.compose.material.icons.*` extended set, `androidx.compose.ui.draw.dropShadow`,
`androidx.compose.ui.graphics.shadow.Shadow`).
