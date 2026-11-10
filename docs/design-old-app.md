# NewsDove JavaFX Design Reference

Derived from pixel-sampled screenshots (sampled from user screenshots 2026-11-10).
No FXML/CSS sources are in the monorepo (they live in the legacy Maven project).

---

## 1. Color Palette

| Token | Hex | Used for |
|-------|-----|----------|
| `Cream` | `#EDE0C1` | Main background, card fill, text field fill, onPrimary |
| `DiagBand` | `#DCC999` | Decorative diagonal band drawn behind all content |
| `Teal` | `#294946` | Primary: buttons, borders, titles, icons, language badge bg |
| `Panel` | `#D4C398` | Panel/container background (register form, onboarding, dialogs, bottom sheet) |
| `MutedText` | `#524F4C` | Secondary/meta text (publisher, date, subtitles) |
| `ErrorText` | `#93231A` | Error messages, field-level error labels |
| `DangerRed` | `#C0392B` | Destructive action buttons (delete, block) |
| `White` | `#FFFFFF` | Text on danger buttons |

> Previous approximations (#C4B49A, #1B3C2D, #4E4B35, etc.) have been replaced with
> pixel-sampled values. The DarkOlive card style (#4E4B35) has been dropped.

---

## 2. Typography

No custom font file is bundled. The app uses the **system sans-serif** (Roboto on Android).

| Role | Weight | Size | Used for |
|------|--------|------|----------|
| Display | Bold (700) | 30 sp | "NewsDove" logo on Login screen |
| Headline Medium | Bold (700) | 24 sp | Screen headings ("Регистрация", onboarding titles) |
| Title Large | SemiBold (600) | 18 sp | App bar title |
| Title Medium | SemiBold (600) | 16 sp | "Вход" subtitle, card titles |
| Body Large | Regular (400) | 16 sp | List item text |
| Body Medium | Regular (400) | 14 sp | Subtitles, descriptions |
| Body Small | Regular (400) | 12 sp | Feed end / error text |
| Label Large | **Bold (700)** | 14 sp | Button text |
| Label Medium | Medium (500) | 12 sp | Chip labels, badge text |
| Label Small | Regular (400) | 11 sp | Secondary chip / badge text |

---

## 3. Component Styles

### 3.1 Primary Button ("Войти", "Зарегистрироваться", "Выйти")
- Background: `Teal` (`#294946`)
- Text: `Cream` (`#EDE0C1`), Label Large weight (Bold)
- Shape: **fully rounded pill** (`RoundedCornerShape(50%)`)

### 3.2 Secondary / Outlined Button ("Отмена", "Back to Login")
- Background: transparent or `Cream`
- Border: 1–2 dp `Teal`
- Text: `Teal`, Label Large Bold
- Shape: same pill

### 3.3 Destructive Button ("Delete Article", "Delete User")
- Background: `DangerRed` (`#C0392B`)
- Text: `White`, Label Large Bold
- Shape: same pill

### 3.4 Text Field / Input
- Fill: `Cream` (`#EDE0C1`)
- Border: 1.5–2 dp `Teal` (`#294946`)
- Shape: pill (`extraSmall = RoundedCornerShape(50%)` → OutlinedTextField)
- Placeholder: `MutedText` at ~50% alpha; text: `Teal`

### 3.5 News Article Card (feed)
- Background: `Cream` (`#EDE0C1`)
- Border: 2 dp `Teal`
- Corner radius: 16 dp (`shapes.large`)
- Title: `Teal`, Title Medium, **Bold**
- Publisher / date: `MutedText` (`#524F4C`), Body Small
- Language badge: `Teal` circle (32 dp), `Cream` text, Label Small Bold
- Category chip: `Cream` fill, `Teal` border, `Teal` label

### 3.6 Category Chip (OnboardingInterestsScreen)
- Unselected: `Cream` fill, `Teal` border, `Teal` label
- Selected: `Teal` fill, `Teal` border, `Cream` label
- Shape: pill (M3 `FilterChip` = `CornerFull` by default)

### 3.7 Panel / Form Container
- Background: `Panel` (`#D4C398`)
- Border: 2 dp `Teal`
- Corner radius: 16 dp (`shapes.large`)
- Used for: Register form, all onboarding screens, logout dialog, complaint bottom sheet

### 3.8 Dialog ("Выйти из аккаунта?")
- Surface: `Cream`, border 2 dp `Teal`, corner 28 dp (`shapes.extraLarge`)
- Body text: `Teal`, Body Large
- Buttons: row — `OutlinedButton` (cancel) + `Button` (confirm), both pill

### 3.9 Complaint Bottom Sheet
- `ModalBottomSheet` `containerColor = Panel` (`primaryContainer`)
- Items: `RadioButton` rows with `Teal` text
- Buttons: TextButton (cancel) + Button (send)

### 3.10 Top Navigation / TopAppBar
- `containerColor = Cream`, title/icon color = `Teal`

---

## 4. Screen Descriptions

### 4.1 Login Screen
- Full background: `Cream` with `DiagBand` diagonal (Canvas, lower-right quadrant)
- "NewsDove" Display Bold Teal, "Вход" Title Medium MutedText below
- Two pill text fields (Cream fill, Teal border)
- "Войти" primary button full-width + "Зарегистрируйтесь" TextButton

### 4.2 Registration
- Same diagonal band background
- "NewsDove" Display Bold Teal
- Panel container (`Panel` bg, 2dp Teal border, 16dp radius) holds:
  - "Регистрация" Headline Medium Bold
  - Login + Password fields
  - "Зарегистрироваться" primary button + "Уже есть аккаунт" TextButton

### 4.3 Language Selection (Onboarding)
- Headline outside panel; Panel container fills remaining space
- Subtitle in `MutedText`; Checkbox list of languages; "Далее" button

### 4.4 Region Selection (Onboarding)
- Same structure as 4.3 with regions

### 4.5 Categories / Interests (Onboarding)
- Panel container with FlowRow of `FilterChip`s (cream/teal unselected, teal/cream selected)
- "Готово" button at bottom of panel

### 4.6 Feed Screen
- `Scaffold(containerColor = Transparent)` → diagonal band shows through
- TopAppBar: Cream bg, "NewsDove" Teal title, "Выйти" TextButton
- Article cards: Cream bg, 2dp Teal border, 16dp radius
- Logout tapped → confirmation Dialog

### 4.7 Log Out Dialog
- `Dialog` with `Surface(Cream, 2dp Teal border, extraLarge shape)`
- "Вы уверены, что хотите выйти из аккаунта?" Body Large
- Row: `OutlinedButton("Отмена")` + `Button("Выйти")`

### 4.8 Complaint Sheet
- `ModalBottomSheet(containerColor = Panel)`
- RadioButton list of reasons + Submit button

### 4.9–4.11 Admin Screens (Step 5e)
- Not yet implemented; same palette applies

---

## 5. Mapping to Compose Material3

### 5.1 ColorScheme

```kotlin
// Sampled from user screenshots 2026-11-10
lightColorScheme(
    primary              = Teal,          // #294946
    onPrimary            = Cream,         // #EDE0C1
    primaryContainer     = Panel,         // #D4C398
    onPrimaryContainer   = Teal,
    secondary            = Teal,
    onSecondary          = Cream,
    secondaryContainer   = Panel,
    onSecondaryContainer = Teal,
    background           = Cream,         // #EDE0C1
    onBackground         = Teal,
    surface              = Cream,
    onSurface            = Teal,
    surfaceVariant       = Panel,
    onSurfaceVariant     = MutedText,     // #524F4C (WCAG-adjusted)
    error                = ErrorText,     // #93231A
    onError              = Cream,
    errorContainer       = DangerRed,     // #C0392B
    onErrorContainer     = White,
    outline              = Teal,
    outlineVariant       = Panel,
)
```

### 5.2 Typography (key overrides)

```kotlin
Typography(
    displayLarge   = TextStyle(fontWeight = Bold,      fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = Bold,      fontSize = 24.sp),
    titleLarge     = TextStyle(fontWeight = SemiBold,  fontSize = 18.sp),
    titleMedium    = TextStyle(fontWeight = SemiBold,  fontSize = 16.sp),
    labelLarge     = TextStyle(fontWeight = Bold,      fontSize = 14.sp),  // button text
)
```

### 5.3 Shapes

```kotlin
Shapes(
    extraSmall = RoundedCornerShape(percent = 50),  // text fields → pill
    small      = RoundedCornerShape(percent = 50),  // chips
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),         // cards, panels, bottom sheet
    extraLarge = RoundedCornerShape(28.dp),         // dialogs
)
```

### 5.6 WCAG Contrast Ratios

Relative luminance: sRGB → linearize → `L = 0.2126R + 0.7152G + 0.0722B`; ratio = `(L1+0.05)/(L2+0.05)`.

| Color | Hex | L |
|-------|-----|---|
| Cream | `#EDE0C1` | 0.751 |
| Panel | `#D4C398` | 0.553 |
| DiagBand | `#DCC999` | 0.593 |
| Teal (primary) | `#294946` | 0.057 |
| MutedText | `#524F4C` | 0.079 |
| ErrorText | `#93231A` | 0.075 |
| DangerRed | `#C0392B` | 0.143 |
| White | `#FFFFFF` | 1.000 |

| Token pair | Surfaces | Ratio | WCAG AA (≥4.5:1) |
|------------|----------|-------|-------------------|
| `onPrimary` (Cream) on `primary` (Teal) | Buttons | **7.49 : 1** | ✓ PASS |
| `primary` (Teal) on `background` (Cream) | Titles, borders | **7.49 : 1** | ✓ PASS |
| `primary` (Teal) on `primaryContainer` (Panel) | Panel content | **5.64 : 1** | ✓ PASS |
| `onSurfaceVariant` (MutedText) on `background` (Cream) | Card meta text | **6.22 : 1** | ✓ PASS |
| `onSurfaceVariant` (MutedText) on `primaryContainer` (Panel) | Panel subtitles | **4.68 : 1** | ✓ PASS |
| `onError` (Cream) on `error` (ErrorText) | Error states | **6.42 : 1** | ✓ PASS |
| `onErrorContainer` (White) on `errorContainer` (DangerRed) | Danger buttons | **5.44 : 1** | ✓ PASS |
| ~~`#83817C`~~ on Cream (original muted, NOT used) | — | ~~2.93 : 1~~ | ✗ FAIL |
| ~~`#83817C`~~ on Panel (original muted, NOT used) | — | ~~2.30 : 1~~ | ✗ FAIL |

**Adjustment:** `#83817C` fails on both Cream (2.9:1) and Panel (2.3:1). Darkened to `#524F4C`
(same warm-neutral hue, reduced lightness) → 6.2:1 on Cream and 4.7:1 on Panel. ✓

---

### 5.5 Things That Cannot Be Reproduced on Mobile

| Desktop feature | Why not on phone | Mitigation |
|-----------------|-----------------|------------|
| Left sidebar + right detail panel side-by-side | Phone too narrow for 2-pane | Master-detail navigation; `ListDetailPaneScaffold` on tablet |
| Calendar date picker inline | Use Material3 `DatePickerDialog` | Triggered by a button |
| Pie chart in Search by Username | No M3 chart component | Two `LinearProgressIndicator` rows or drop for MVP |
| Tag progress bars in admin detail | Achievable via `LinearProgressIndicator` | ✓ reproducible |
