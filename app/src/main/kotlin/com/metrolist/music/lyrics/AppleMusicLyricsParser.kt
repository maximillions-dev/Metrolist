/**
 * Metrolist Project (C) 2024
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.lyrics

import java.util.regex.Pattern

object AppleMusicLyricsParser {
    private val lineRegex = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](v1:|v2:|\\[bg\\]:)?(.*)")
    private val wordRegex = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]+)")

    fun parse(lyricsText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val textLines = lyricsText.lines()

        for (i in textLines.indices) {
            val line = textLines[i]
            val matcher = lineRegex.matcher(line)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toFloat() ?: 0f
                val sec = matcher.group(2)?.toFloat() ?: 0f
                val ms = matcher.group(3)?.toFloat() ?: 0f
                val startTime = min * 60 + sec + ms / 1000

                val speaker = when (matcher.group(4)) {
                    "v1:" -> SpeakerRole.V1
                    "v2:" -> SpeakerRole.V2
                    "[bg]:" -> SpeakerRole.BG
                    else -> SpeakerRole.NONE
                }

                val content = matcher.group(5) ?: ""
                val words = mutableListOf<Word>()
                val wordMatcher = wordRegex.matcher(content)
                var fullText = ""

                while (wordMatcher.find()) {
                    val wordMin = wordMatcher.group(1)?.toFloat() ?: 0f
                    val wordSec = wordMatcher.group(2)?.toFloat() ?: 0f
                    val wordMs = wordMatcher.group(3)?.toFloat() ?: 0f
                    val wordStartTime = wordMin * 60 + wordSec + wordMs / 1000
                    val wordText = wordMatcher.group(4) ?: ""
                    fullText += wordText
                    words.add(Word(wordText, wordStartTime, 0f))
                }

                for (j in 0 until words.size - 1) {
                    words[j] = words[j].copy(endTime = words[j + 1].startTime)
                }

                val nextLineStartTime = if (i + 1 < textLines.size) {
                    val nextLineMatcher = lineRegex.matcher(textLines[i + 1])
                    if (nextLineMatcher.matches()) {
                        val nextMin = nextLineMatcher.group(1)?.toFloat() ?: 0f
                        val nextSec = nextLineMatcher.group(2)?.toFloat() ?: 0f
                        val nextMs = nextLineMatcher.group(3)?.toFloat() ?: 0f
                        nextMin * 60 + nextSec + nextMs / 1000
                    } else {
                        startTime + 5f
                    }
                } else {
                    startTime + 5f
                }

                if (words.isNotEmpty()) {
                    words[words.size - 1] = words.last().copy(endTime = nextLineStartTime)
                }

                lines.add(
                    LyricLine(
                        text = fullText,
                        startTime = startTime,
                        endTime = nextLineStartTime,
                        speaker = speaker,
                        words = words
                    )
                )
            }
        }
        return lines
    }
}
