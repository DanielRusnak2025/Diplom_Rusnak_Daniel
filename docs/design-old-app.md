# NewsDove JavaFX Design Reference

Derived from screenshots in `Cloud/lab3/`. No FXML/CSS sources are in the monorepo (they live in the
legacy Maven project); all values are measured/approximated from the 8 reference screenshots.

---

## 1. Color Palette

| Token | Hex | Used for |
|-------|-----|----------|
| `colorBackground` | `#C4B49A` | Main window background, all screen backgrounds |
| `colorPrimary` | `#1B3C2D` | Logo text, primary buttons, language badge circles, top nav buttons (active), admin tab active |
| `colorCardSurface` | `#4E4B35` | News article card backgrounds in the feed/slider |
| `colorInputSurface` | `#EDE8D8` | Input field fills, unselected category chip fills |
| `colorError` | `#CC2222` | "Delete Article" / "Delete User" button |
| `colorOnPrimary` | `#FFFFFF` | Text on dark-green buttons and card backgrounds |
| `colorOnBackground` | `#2C2B1E` | Body text, field labels, card subtitles |
| `colorBorder` | `#1B3C2D` | Input field outlines, chip outlines |
| `colorHighlight` | `#6B6B44` | Selected row in admin list sidebar |
| `colorTagBar` | `#8B7D5A` | Approximate tone of progress bar backgrounds in admin tag display |

> All values are approximations from screenshots; adjust ±5% if original source files become available.

---

## 2. Typography

No custom font file is bundled (none found in the monorepo). The app uses the **system sans-serif**
(likely the JVM default: Segoe UI on Windows). The reference screenshots show a clean, geometric sans.

| Role | Weight | Approx size | Used for |
|------|--------|-------------|----------|
| Display | Bold (700) | 28–32 sp | "NewsDove" logo on the home screen |
| Title | SemiBold (600) | 16–18 sp | Screen headings ("Registration"), article card title |
| Body | Regular (400) | 13–14 sp | Article subtitles (main theme, secondary theme, language), field labels |
| Label | Medium (500) | 12–13 sp | Button text, chip labels, badge text |
| Caption | Regular (400) | 11 sp | "Match: 0%", "found: 97" counter, field hints |

---

## 3. Component Styles

### 3.1 Primary Button ("Enter", "log out", "Save")
- Background: `colorPrimary` (`#1B3C2D`)
- Text: `colorOnPrimary` white, Label weight
- Shape: **fully rounded pill** (cornerRadius ≈ height / 2, ~26–28 dp)
- Padding: horizontal 32 dp, vertical 10 dp

### 3.2 Secondary / Outlined Button ("Back to Login", "cancel", admin nav tabs inactive)
- Background: `colorBackground` or transparent
- Border: 1.5 dp `colorPrimary`
- Text: `colorPrimary`
- Shape: same pill, same padding

### 3.3 Destructive Button ("Delete Article", "Delete User")
- Background: `colorError` (`#CC2222`)
- Text: white
- Shape: same pill

### 3.4 Text Field / Input
- Fill: `colorInputSurface` (`#EDE8D8`)
- Border: 1.5 dp `colorBorder` (`#1B3C2D`), cornerRadius ≈ 12 dp
- Placeholder text: `colorOnBackground` at 50% alpha
- Text: `colorOnBackground`
- Height: ~36–40 dp

### 3.5 News Article Card (feed)
- Background: `colorCardSurface` (`#4E4B35`)
- CornerRadius: 16 dp
- Padding: 12–16 dp
- Title: white, Title weight, 2-line max
- Subtitles (main/secondary theme): white at 80% alpha, Body weight
- Language badge: dark circle `colorPrimary`, white text, diameter ~36 dp, positioned top-right

### 3.6 Category Chip / Tag Chip (Categories screen, filters)
- Background (unselected): `colorInputSurface`
- Background (selected): `colorPrimary`
- Text (unselected): `colorPrimary`
- Text (selected): white
- Border: 1.5 dp `colorPrimary` (unselected only)
- Shape: pill, height ~32 dp, horizontal padding 16 dp

### 3.7 Admin List Sidebar
- Background: `colorBackground`
- Item text: `colorOnBackground`
- Selected item background: `colorHighlight` (`#6B6B44`)
- Selected item text: white
- Border between items: 0.5 dp, `colorBorder` at 20% alpha

### 3.8 Dialog ("Are you sure you want to log out?")
- Background: `colorInputSurface` (`#EDE8D8`), cornerRadius 20 dp
- Body text: `colorOnBackground`, Title weight, centered
- Buttons: row with "log out" (Primary) and "cancel" (Primary, same style)
- Shadow: moderate elevation (~8 dp)

### 3.9 Admin Tag Progress Row
- Label: `colorOnBackground`, Body weight
- Progress bar: filled `colorPrimary`, track lighter
- Percentage text: `colorPrimary`, Caption weight

### 3.10 Top Navigation Tabs (admin: "Search by keyword", "Search by Username", "Search by Reports")
- Active: `colorPrimary` background, white text, pill shape
- Inactive: `colorBackground` / outlined, `colorPrimary` text
- Arranged horizontally, same pill shape as buttons

---

## 4. Screen Descriptions

### 4.1 Home Screen + Login (Guest)
- Full-height layout, top 60% = news slider
- Slider cards: stacked/scrollable list of `ArticleCard` (§3.5)
- Bottom 40%: "NewsDove" logo (Display), then Login field, Password field,
  "Register" link-text (left-aligned), "Enter" button (right-aligned, pill)
- Background: `colorBackground` fills the entire window

### 4.2 Registration
- Centered card (rounded panel) on `colorBackground`
- Header "Registration" (Title weight)
- 3 text fields stacked: Login, Password, Confirm Password
- 2 buttons row: "Back to Login" (secondary) + "Enter" (primary)

### 4.3 Language / Region (not in screenshots; described in coursework §3.5.1.3)
- Two dropdown selectors (Language, Region) on `colorBackground`
- "Enter" button full-width at bottom

### 4.4 Categories
- Header text, centered, 2-line
- Chip grid (FlowPane): 15 category chips in wrap layout
- Full-width "Enter" primary button at bottom

### 4.5 Main Screen (User)
- Top bar: avatar icon + search icon (dark circles), then 4 filter dropdowns
  (Language, Newsroom, Categories, Region), red ✕ clear button
- "found: N" count label below
- Scrollable list of `ArticleCard` (§3.5) with match score visible
- No bottom nav bar visible

### 4.6 Log Out Dialog
- Modal overlay, centered dialog on `colorInputSurface` background
- "Are you sure you want to log out?" centered text
- Two pill buttons: "log out" + "cancel" side by side

### 4.7 Admin – Search by Keyword
- Top tab bar: 3 nav tabs + icons (top-right: settings/export)
- Left sidebar (~35% width): keyword field + date picker + scrollable article list
- Right panel (~65% width): article detail form (title, link, newsroom, language,
  region, publication date — all editable fields), tag progress bars, Grade section
- Save + Delete buttons top-right

### 4.8 Admin – Search by Username
- Same layout as 4.7
- Left sidebar: user list
- Right panel: user form (Username, passwords, email, registration date, Is Admin toggle,
  preferred region/language, Blocked list, pie chart for rating breakdown, Grade list)

### 4.9 Admin – Search by Reports
- Same layout
- Left sidebar: report list (Report N – username)
- Right panel: read-only fields (Report Date, Applied User, Blocked Article Title,
  Blocked Article Link, Reason For Blocking — text area)

---

## 5. Mapping to Compose Material3

### 5.1 ColorScheme

```kotlin
private val NewsDovePalette = lightColorScheme(
    primary            = Color(0xFF1B3C2D),   // colorPrimary
    onPrimary          = Color(0xFFFFFFFF),   // colorOnPrimary
    primaryContainer   = Color(0xFF4E4B35),   // colorCardSurface (card bg)
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary          = Color(0xFF6B6B44),   // colorHighlight
    onSecondary        = Color(0xFFFFFFFF),
    background         = Color(0xFFC4B49A),   // colorBackground
    onBackground       = Color(0xFF2C2B1E),   // colorOnBackground
    surface            = Color(0xFFEDE8D8),   // colorInputSurface
    onSurface          = Color(0xFF2C2B1E),
    surfaceVariant     = Color(0xFF4E4B35),   // card surface
    onSurfaceVariant   = Color(0xFFFFFFFF),
    error              = Color(0xFFCC2222),   // colorError
    onError            = Color(0xFFFFFFFF),
    outline            = Color(0xFF1B3C2D),   // colorBorder
)
```

### 5.2 Typography

```kotlin
val NewsDoveTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,    fontSize = 30.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal,  fontSize = 14.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium,  fontSize = 13.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Normal,  fontSize = 11.sp),
)
```

No font family override — uses default Roboto on Android (visually close to what the desktop shows).

### 5.3 Shapes

```kotlin
val NewsDoveShapes = Shapes(
    small  = RoundedCornerShape(50),   // chips, badges — pill
    medium = RoundedCornerShape(12.dp), // text fields, small cards
    large  = RoundedCornerShape(16.dp), // article cards, bottom sheet
    extraLarge = RoundedCornerShape(28.dp), // dialogs
)
```

Buttons use `shape = CircleShape` (fully-rounded pill) via `Button(shape = CircleShape)` or
`shape = RoundedCornerShape(50)`.

### 5.4 Screen Mapping

| Old screen | Compose equivalent | Notes |
|------------|-------------------|-------|
| Home + Login | `LoginScreen` (current, already implemented) | Add news card slider to top half; currently shows just the form |
| Registration | `RegisterScreen` (current) | ✓ already a form; style inputs with `colorInputSurface` fill + `colorBorder` outline |
| Language / Region | `OnboardingLanguageScreen` (current) | Style as dropdowns on tan background |
| Categories | `OnboardingCategoriesScreen` (current) | Use `FilterChip` with custom colors for the chip grid |
| Main Screen | `FeedScreen` (current, Step 5d) | Replace `AssistChip` with styled `FilterChip`; move top filter row |
| Log Out dialog | `AlertDialog` on `FeedScreen` | Use `surface = colorInputSurface`, pill buttons |
| Admin – Search by Keyword | Step 5e admin screen | Phone: single-column with list + detail on separate screens (master-detail on tablet) |
| Admin – Search by Username | Step 5e admin screen | Same as above |
| Admin – Search by Reports | Step 5e admin screen | Same as above |

### 5.5 Things That Cannot Be Reproduced on Mobile

| Desktop feature | Why not on phone | Mitigation |
|-----------------|-----------------|------------|
| Left sidebar + right detail panel side-by-side (admin screens) | Phone width too narrow for 2-pane layout | Master-detail: list → tap → detail screen (phone), or adaptive 2-pane on tablet (Compose `ListDetailPaneScaffold`) |
| Calendar date picker widget inline | Desktop uses JavaFX `DatePicker` inline | Use Material3 `DatePickerDialog` triggered by a button |
| Pie chart in Search by Username | No M3 chart; custom Canvas | Approximate with two stacked `LinearProgressIndicator` rows or drop for MVP |
| Tag progress bars in admin detail | Achievable in Compose with `LinearProgressIndicator` | ✓ reproducible |
| Clipboard copy buttons | `ClipboardManager` API available on Android | ✓ reproducible |
| Multi-line editable text field for Reason | `OutlinedTextField(maxLines=…)` | ✓ reproducible |
