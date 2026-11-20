package com.appcoholic.ui

import androidx.compose.ui.graphics.Color
import com.quran.labs.androidquran.common.ui.core.QuranAccent

/**
 * Rattil's green for the Compose screens (audio bar, audio manager, bookmark sheets). Upstream's
 * [QuranAccent.NavyGold] follows its own icon; XML views get the same green through
 * `accent_navy_gold` -> `accent_color` in colors.xml, so keep both in sync.
 */
val RattilAccent = QuranAccent(
  light = Color(0xFF4CAF50),
  dark = Color(0xFF4CAF50),
  onLight = Color.White,
  onDark = Color(0xFF00210B)
)
