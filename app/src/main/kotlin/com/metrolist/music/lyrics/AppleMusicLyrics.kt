/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.lyrics

enum class Speaker {
    V1,
    V2,
    BG,
    UNKNOWN
}

data class LyricWord(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

data class LyricLine(
    val speaker: Speaker,
    val words: List<LyricWord>,
    val startTime: Long,
    val endTime: Long
)

data class AppleMusicLyrics(
    val lines: List<LyricLine>
)
