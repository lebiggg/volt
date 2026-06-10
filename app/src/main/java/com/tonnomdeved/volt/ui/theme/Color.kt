package com.tonnomdeved.volt.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette de repli — utilisée uniquement quand le device ne supporte pas
 * les couleurs dynamiques (Android < 12). Sur Pixel 8 / GrapheneOS, ces
 * couleurs sont automatiquement remplacées par `dynamicLightColorScheme`
 * / `dynamicDarkColorScheme`.
 *
 * Tonalité monochromatique sobre, cohérente avec l'esthétique GrapheneOS.
 */

// Light fallback
val VoltLightPrimary       = Color(0xFF1F1F1F)
val VoltLightOnPrimary     = Color(0xFFFFFFFF)
val VoltLightSurface       = Color(0xFFFAFAFA)
val VoltLightOnSurface     = Color(0xFF1A1A1A)
val VoltLightSurfaceVariant = Color(0xFFE6E6E6)
val VoltLightOutline       = Color(0xFFB8B8B8)

// Dark fallback (priorité sur GrapheneOS)
val VoltDarkPrimary        = Color(0xFFE6E6E6)
val VoltDarkOnPrimary      = Color(0xFF101010)
val VoltDarkSurface        = Color(0xFF0F0F0F)
val VoltDarkOnSurface      = Color(0xFFE6E6E6)
val VoltDarkSurfaceVariant = Color(0xFF1E1E1E)
val VoltDarkOutline        = Color(0xFF3D3D3D)

// Signaux sémantiques (utilisés uniformément sur les statuts)
val SignalOk      = Color(0xFF34C759) // service actif / WS connecté
val SignalWarning = Color(0xFFFFB020) // backoff / en attente
val SignalError   = Color(0xFFFF453A) // erreur / déconnecté
