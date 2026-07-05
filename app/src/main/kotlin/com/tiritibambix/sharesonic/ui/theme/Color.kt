package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.ui.graphics.Color

// ── Velvet palette ────────────────────────────────────────────────────
// Mapped from Velvet's :root CSS variables (navy/purple dark theme).

// Backgrounds  --bg / --surface / --raised / --card
val VelvetBg          = Color(0xFF1A1A2E)   // --bg       deepest layer
val VelvetSurface     = Color(0xFF16213E)   // --surface  cards, top bar
val VelvetRaised      = Color(0xFF0F3460)   // --raised   elevated surfaces
val VelvetCard        = Color(0xFF1E2D4A)   // --card     in-card containers

// Borders  --border / --border2
val VelvetBorder      = Color(0xFF2A3A5E)   // --border   dividers
val VelvetBorder2     = Color(0xFF3A4E72)   // --border2  stronger border

// Primary (purple)  --primary / --primary-h
val VelvetPrimary     = Color(0xFF8B5CF6)   // --primary
val VelvetPrimaryHov  = Color(0xFF7C3AED)   // --primary-h (hover / inverse)
val VelvetPrimaryDark = Color(0xFF3B1A78)   // dark container for primary
val VelvetPrimaryOnC  = Color(0xFFEDE9FE)   // on primaryContainer (light lavender)

// Accent / secondary (blue)  --accent
val VelvetAccent      = Color(0xFF60A5FA)   // --accent
val VelvetAccentDark  = Color(0xFF1E3A5F)   // dark container for accent
val VelvetAccentOnC   = Color(0xFFBFDBFE)   // on secondaryContainer

// Tertiary (teal-green for queue / positive)  --green
val VelvetGreen       = Color(0xFF34D399)   // --green
val VelvetGreenDark   = Color(0xFF064E3B)   // dark container for green
val VelvetGreenOnC    = Color(0xFFA7F3D0)   // on tertiaryContainer

// Semantic  --red / --yellow
val VelvetRed         = Color(0xFFF87171)   // --red  error
val VelvetRedDark     = Color(0xFF7F1D1D)   // error container
val VelvetYellow      = Color(0xFFFBBF24)   // --yellow  warning (unused by M3 but available)

// Text  --t1 / --t2 / --t3
val VelvetT1          = Color(0xFFEEEEFF)   // --t1 primary text (near-white with lavender)
val VelvetT2          = Color(0xFF8888B0)   // --t2 secondary text
val VelvetT3          = Color(0xFF7E8EC0)   // --t3 muted text
val VelvetT4          = Color(0xFF2A3A5E)   // --t4 dim / disabled ink

// Very dark (used for onPrimary, onAccent text)
val VelvetInk         = Color(0xFF0D0B1A)   // near-black with purple tint

// ── "Dark" palette — true near-black, Material/Apple dark guidelines ─────────
// Mapped from the user-supplied :root.dark CSS variables.

val DarkBg          = Color(0xFF000000)   // --bg       true black
val DarkSurface     = Color(0xFF0D0D0D)   // --surface
val DarkRaised      = Color(0xFF1C1C1E)   // --raised   elevated surfaces
val DarkCard        = Color(0xFF141414)   // --card

val DarkBorder      = Color(0xFF262626)   // --border   rgba(255,255,255,.09) on black
val DarkBorder2     = Color(0xFF333333)   // --border2  rgba(255,255,255,.15) on black

val DarkPrimary       = Color(0xFFA78BFA)   // --primary
val DarkPrimaryHov    = Color(0xFF9061F9)   // --primary-h
val DarkPrimaryDark   = Color(0xFF2F2347)   // dark container for primary
val DarkPrimaryOnC    = Color(0xFFE3D9FC)   // on primaryContainer

val DarkAccent      = Color(0xFF60A5FA)   // --accent
val DarkAccentDark  = Color(0xFF1C2F4A)   // dark container for accent
val DarkAccentOnC   = Color(0xFFCFE2FF)   // on secondaryContainer

val DarkGreen       = Color(0xFF34D399)   // --green
val DarkGreenDark   = Color(0xFF0F3D2E)   // dark container for green
val DarkGreenOnC    = Color(0xFFB8F0D9)   // on tertiaryContainer

val DarkRed         = Color(0xFFF87171)   // --red
val DarkRedDark     = Color(0xFF4A1F1F)   // error container
val DarkYellow      = Color(0xFFFBBF24)   // --yellow (unused by M3 but available)

val DarkT1          = Color(0xFFF1F1F1)   // --t1 primary text
val DarkT2          = Color(0xFF8A8A9A)   // --t2 secondary text
val DarkT3          = Color(0xFF707082)   // --t3 muted text
val DarkT4          = Color(0xFF252530)   // --t4

val DarkInk         = Color(0xFF1A1025)   // near-black purple-tinted ink for onPrimary/onAccent

// ── "Light" palette — soft lavender-gray ─────────────────────────────────────
// Mapped from the user-supplied :root.light CSS variables.

val LightBg          = Color(0xFFE8E8F2)   // --bg       medium lavender-gray
val LightSurface     = Color(0xFFF2F2FA)   // --surface  soft off-white
val LightRaised      = Color(0xFFE4E4EF)   // --raised
val LightCard        = Color(0xFFDCDCEC)   // --card

val LightBorder      = Color(0xFFD4D4E0)   // --border   rgba(0,0,0,.10) on light bg
val LightBorder2     = Color(0xFFC0C0D2)   // --border2  rgba(0,0,0,.17) on light bg

val LightPrimary       = Color(0xFF6D3CE6)   // --primary
val LightPrimaryHov    = Color(0xFF5B28D4)   // --primary-h
val LightPrimaryDark   = Color(0xFFE4D9FB)   // light lavender container for primary
val LightPrimaryOnC    = Color(0xFF2C1463)   // on primaryContainer (dark purple text)

val LightAccent      = Color(0xFF2563EB)   // --accent
val LightAccentDark  = Color(0xFFD8E6FE)   // light container for accent
val LightAccentOnC   = Color(0xFF0D2C63)   // on secondaryContainer

val LightGreen       = Color(0xFF16A34A)   // --green
val LightGreenDark   = Color(0xFFD3F3E0)   // light container for green
val LightGreenOnC    = Color(0xFF0A4023)   // on tertiaryContainer

val LightRed         = Color(0xFFDC2626)   // --red
val LightRedDark     = Color(0xFFFBD7D7)   // error container
val LightYellow      = Color(0xFFD97706)   // --yellow (unused by M3 but available)

val LightT1          = Color(0xFF0C0C1A)   // --t1 primary text (near-black with lavender tint)
val LightT2          = Color(0xFF42425E)   // --t2 secondary text
val LightT3          = Color(0xFF7878A0)   // --t3 muted text
val LightT4          = Color(0xFFB8B8D0)   // --t4

val LightInk         = Color(0xFFFFFFFF)   // white — used for onPrimary/onAccent text

// ── High-contrast palette — AAA pure black/white, yellow accent ──────────────
// Mapped from Velvet's :root.hc CSS variables.

val HcBg          = Color(0xFF000000)   // --bg       pure black
val HcSurface     = Color(0xFF000000)   // --surface
val HcRaised      = Color(0xFF0A0A0A)   // --raised
val HcCard        = Color(0xFF0A0A0A)   // --card

val HcBorder      = Color(0xFFFFFFFF)   // --border   pure white
val HcBorder2     = Color(0xFFFFFFFF)   // --border2  pure white

val HcPrimary       = Color(0xFFFFFFFF)   // --primary
val HcPrimaryHov    = Color(0xFFFFFFFF)   // --primary-h
val HcPrimaryDark   = Color(0xFF333333)   // dark container for primary
val HcPrimaryOnC    = Color(0xFF000000)   // on primaryContainer

val HcAccent      = Color(0xFFFFE066)   // --accent   yellow
val HcAccentDark  = Color(0xFF4A3E0F)   // dark container for accent
val HcAccentOnC   = Color(0xFF000000)   // on secondaryContainer

val HcGreen       = Color(0xFF00E676)   // --green
val HcGreenDark   = Color(0xFF063919)   // dark container for green
val HcGreenOnC    = Color(0xFF9DFFC0)   // on tertiaryContainer

val HcRed         = Color(0xFFFF5252)   // --red
val HcRedDark     = Color(0xFF4A0F0F)   // error container
val HcYellow      = Color(0xFFFFE066)   // --yellow

val HcT1          = Color(0xFFFFFFFF)   // --t1
val HcT2          = Color(0xFFFFFFFF)   // --t2 — white for HC readability
val HcT3          = Color(0xFFE6E6E6)   // --t3
val HcT4          = Color(0xFF9A9A9A)   // --t4

val HcInk         = Color(0xFF000000)   // near-black ink

// ── Colourblind-safe palette — blue primary + orange accent (no red/green) ───
// Mapped from Velvet's :root.cb CSS variables.

val CbBg          = Color(0xFF10131A)   // --bg
val CbSurface     = Color(0xFF161A24)   // --surface
val CbRaised      = Color(0xFF1D2230)   // --raised
val CbCard        = Color(0xFF1B2030)   // --card

val CbBorder      = Color(0xFF2A3140)   // --border
val CbBorder2     = Color(0xFF3A4458)   // --border2

val CbPrimary       = Color(0xFF3B82F6)   // --primary  blue
val CbPrimaryHov    = Color(0xFF2F6FE0)   // --primary-h
val CbPrimaryDark   = Color(0xFF16244A)   // dark container for primary
val CbPrimaryOnC    = Color(0xFFDCE7FA)   // on primaryContainer

val CbAccent      = Color(0xFFF59E0B)   // --accent  orange
val CbAccentDark  = Color(0xFF4A3009)   // dark container for accent
val CbAccentOnC   = Color(0xFFFFEEDA)   // on secondaryContainer

val CbGreen       = Color(0xFF3B82F6)   // --green    intentionally = primary (no red/green)
val CbGreenDark   = Color(0xFF16244A)   // dark container for green
val CbGreenOnC    = Color(0xFFDCE7FA)   // on tertiaryContainer

val CbRed         = Color(0xFFF59E0B)   // --red      intentionally = accent
val CbRedDark     = Color(0xFF4A3009)   // error container
val CbYellow      = Color(0xFFF59E0B)   // --yellow

val CbT1          = Color(0xFFF3F5F9)   // --t1
val CbT2          = Color(0xFFAAB4C6)   // --t2
val CbT3          = Color(0xFFAEB8CA)   // --t3
val CbT4          = Color(0xFF2A3140)   // --t4

val CbInk         = Color(0xFF0A0F1A)   // near-black ink
