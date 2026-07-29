package com.umuterayaltay.sosyal.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color

// Web sitesinin GERÇEK renk token'ları (app/static/css/style.css :root /
// [data-theme="dark"]) — twa-manifest.json'daki iki genel renk yerine tam
// palet buraya taşındı, marka tutarlılığı arayüz TASARIMI değil sadece
// RENK PALETİ seviyesinde (bkz. .claude/rules/css.md — WCAG AA 4.5:1
// kontrastlı token'lar, aynen korunuyor).

// --- Açık tema (style.css :root) ---
val WarmBg = Color(0xFFFBF8F4)          // --color-warm-bg
val White = Color(0xFFFFFFFF)           // --color-white
val Charcoal900 = Color(0xFF2B2420)     // --color-charcoal-900 (açık tema metin)
val Taupe600 = Color(0xFF7A6F63)        // --color-taupe-600 (muted metin)
val Sand200 = Color(0xFFEDE6DC)         // --color-sand-200 (border)
val Coral700 = Color(0xFFB03D26)        // --color-coral-700 (açık tema primary)
val Teal700 = Color(0xFF1F6F5C)         // --color-teal-700 (açık tema ikincil/success)
val Red500 = Color(0xFFDD3333)          // --color-red-500 (danger)
val Red50 = Color(0xFFFDECEA)           // --color-red-50 (danger arka plan)

// --- Koyu tema (style.css [data-theme="dark"]) ---
val Charcoal950 = Color(0xFF1C1815)     // --color-charcoal-950 (koyu tema zemin)
val Charcoal850 = Color(0xFF2A241F)     // --color-charcoal-850 (koyu tema kart)
val Cream100 = Color(0xFFF0EBE3)        // --color-cream-100 (koyu tema metin)
val Taupe400 = Color(0xFF9C8F80)        // --color-taupe-400 (koyu tema muted)
val Charcoal700 = Color(0xFF3D352C)     // --color-charcoal-700 (koyu tema border)
val Coral400 = Color(0xFFFF7A5C)        // --color-coral-400 (koyu tema primary)
val Teal400 = Color(0xFF3FA98A)         // --color-teal-400 (koyu tema ikincil)
val Red400 = Color(0xFFFF6B6B)          // --color-red-400 (koyu tema danger)
val Red950 = Color(0xFF3A1C1C)          // --color-red-950 (koyu tema danger arka plan)
