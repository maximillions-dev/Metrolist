package com.metrolist.music.lyrics.apple

import java.util.concurrent.TimeUnit

object AppleMusicLyricsParser {
    private val lineRegex = """^\[(\d{2}:\d{2}\.\d{3})](.*)""".toRegex()
    private val wordRegex = """<(\d{2}:\d{2}\.\d{3})>([^<]*)""".toRegex()

    fun parse(lrc: String): HierarchicalLyrics {
        val lines = lrc.lines()
        val lyricLines = mutableListOf<LyricLine>()

        lines.forEachIndexed { index, line ->
            parseLine(line)?.let { lyricLines.add(it) }
        }

        // Correct end times for the last word of each line
        for (i in 0 until lyricLines.size - 1) {
            val currentLine = lyricLines[i]
            val nextLine = lyricLines[i+1]
            if (currentLine.words.isNotEmpty()) {
                val lastWord = currentLine.words.last()
                if (lastWord.endTime == lastWord.startTime) { // End time was not found in the same line
                    lastWord.endTime = nextLine.startTime
                }
            }
        }

        // Ensure the very last word has a duration
        lyricLines.lastOrNull()?.words?.lastOrNull()?.let {
            if (it.endTime == it.startTime) {
                it.endTime = it.startTime + 1000 // Add a 1-second duration
            }
        }

        return HierarchicalLyrics(lines = lyricLines)
    }

    private fun parseLine(line: String): LyricLine? {
        val content: String
        val lineStartTime: Long

        val lineMatch = lineRegex.find(line)
        if (lineMatch != null) {
            lineStartTime = parseTime(lineMatch.groupValues[1])
            content = lineMatch.groupValues[2]
        } else {
            // Handle lines without a line-level timestamp (like some bg: lines)
            lineStartTime = wordRegex.find(line)?.groupValues?.get(1)?.let { parseTime(it) } ?: return null
            content = line
        }

        val speaker = when {
            content.contains("v1:") -> Speaker.LEAD_RIGHT
            content.contains("v2:") -> Speaker.LEAD_LEFT
            content.contains("bg:") -> Speaker.BACKGROUND
            else -> Speaker.LEAD_RIGHT
        }

        val cleanContent = content.replace("v1:", "").replace("v2:", "").replace("bg:", "")

        val words = wordRegex.findAll(cleanContent).mapIndexed { index, matchResult ->
            val startTime = parseTime(matchResult.groupValues[1])
            val text = matchResult.groupValues[2]

            val nextMatch = matchResult.next()
            val endTime = nextMatch?.groupValues?.get(1)?.let { parseTime(it) } ?: startTime

            LyricWord(text, startTime, endTime)
        }.toList()

        if (words.isEmpty()) return null

        return LyricLine(lineStartTime, words, speaker)
    }

    private fun parseTime(time: String): Long {
        val parts = time.split(":", ".")
        if (parts.size != 3) return 0L
        val minutes = parts[0].toLong()
        val seconds = parts[1].toLong()
        val millis = parts[2].toLong()
        return TimeUnit.MINUTES.toMillis(minutes) +
                TimeUnit.SECONDS.toMillis(seconds) +
                millis
    }
}
