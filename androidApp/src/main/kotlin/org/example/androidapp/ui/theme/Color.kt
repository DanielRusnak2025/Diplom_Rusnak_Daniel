package org.example.androidapp.ui.theme

import androidx.compose.ui.graphics.Color

// Sampled from user screenshots 2026-11-10
val Cream        = Color(0xFFEDE0C1)   // background, card fill, text field fill
val DiagBand     = Color(0xFFDCC999)   // decorative diagonal band
val Teal         = Color(0xFF294946)   // primary — buttons, borders, titles, icons
val Panel        = Color(0xFFD4C398)   // container/panel background, secondary button fill
// #83817C is 2.9:1 on Cream and 2.3:1 on Panel — both fail WCAG AA.
// Darkened to #524F4C (keeping warm neutral hue): 6.2:1 on Cream, 4.7:1 on Panel ✓
val MutedText    = Color(0xFF524F4C)
val ErrorText    = Color(0xFF93231A)   // error messages: 6.4:1 on Cream, 4.8:1 on Panel ✓
val DangerRed    = Color(0xFFC0392B)   // danger/destructive buttons
val White        = Color(0xFFFFFFFF)   // text on danger buttons: 5.4:1 ✓
val ErrorLightBg = Color(0xFFFFDAD6)   // error container background
