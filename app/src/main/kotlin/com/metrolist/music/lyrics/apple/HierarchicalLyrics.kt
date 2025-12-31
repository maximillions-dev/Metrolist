package com.metrolist.music.lyrics.apple

data class HierarchicalLyrics(
    val lines: List<LyricLine>
)

data class LyricLine(
    val startTime: Long,
    val words: List<LyricWord>,
    val speaker: Speaker
)

data class LyricWord(
    val text: String,
    val startTime: Long,
    var endTime: Long
)

enum class Speaker {
    LEAD_LEFT,
    LEAD_RIGHT,
    BACKGROUND
}
