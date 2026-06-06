package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.ui.graphics.Color

// ── mStream Velvet palette ────────────────────────────────────────────────────
// Mapped from mStream's :root CSS variables (navy/purple dark theme).

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

// Very dark (used for onPrimary, onAccent text)
val VelvetInk         = Color(0xFF0D0B1A)   // near-black with purple tint
