/**
 * Metrolist Project (C) 2024
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.lyrics

import java.util.regex.Pattern

object AppleMusicLyricsParser {
    private val lineRegex = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](v1:|v2:|\\[bg\\]:)?(.*)")
    private val partRegex = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")

    fun parse(lyricsText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val textLines = lyricsText.lines()

        for (i in textLines.indices) {
            val line = textLines[i]
            val lineMatcher = lineRegex.matcher(line)
            if (lineMatcher.matches()) {
                val lineMin = lineMatcher.group(1)?.toFloat() ?: 0f
                val lineSec = lineMatcher.group(2)?.toFloat() ?: 0f
                val lineMs = lineMatcher.group(3)?.toFloat() ?: 0f
                val lineStartTime = lineMin * 60 + lineSec + lineMs / 1000

                val speaker = when (lineMatcher.group(4)) {
                    "v1:" -> SpeakerRole.V1
                    "v2:" -> SpeakerRole.V2
                    "[bg]:" -> SpeakerRole.BG
                    else -> SpeakerRole.NONE
                }

                val content = lineMatcher.group(5) ?: ""

                val parts = mutableListOf<Pair<Float, String>>()
                val partMatcher = partRegex.matcher(content)
                while(partMatcher.find()) {
                    val partMin = partMatcher.group(1)?.toFloat() ?: 0f
                    val partSec = partMatcher.group(2)?.toFloat() ?: 0f
                    val partMs = partMatcher.group(3)?.toFloat() ?: 0f
                    val partStartTime = partMin * 60 + partSec + partMs / 1000
                    val partText = partMatcher.group(4) ?: ""
                    parts.add(Pair(partStartTime, partText))
                }

                if (parts.isEmpty()) continue

                val words = mutableListOf<Word>()
                for (j in parts.indices) {
                    val currentPart = parts[j]
                    val text = currentPart.second

                    if (text.isNotEmpty()) {
                        val endTime = if (j + 1 < parts.size) {
                            parts[j + 1].first
                        } else {
                             if (i + 1 < textLines.size) {
                                val nextLineMatcher = lineRegex.matcher(textLines[i + 1])
                                if (nextLineMatcher.matches()) {
                                    val nextMin = nextLineMatcher.group(1)?.toFloat() ?: 0f
                                    val nextSec = nextLineMatcher.group(2)?.toFloat() ?: 0f
                                    val nextMs = nextLineMatcher.group(3)?.toFloat() ?: 0f
                                    nextMin * 60 + nextSec + nextMs / 1000
                                } else {
                                    currentPart.first + 1f
                                }
                            } else {
                                currentPart.first + 1f
                            }
                        }
                        words.add(Word(text, currentPart.first, endTime))
                    }
                }

                val fullText = words.joinToString(separator = "") { it.text }
                val lineEndTime = words.lastOrNull()?.endTime ?: lineStartTime

                lines.add(
                    LyricLine(
                        text = fullText,
                        startTime = lineStartTime,
                        endTime = lineEndTime,
                        speaker = speaker,
                        words = words
                    )
                )
            }
        }
        return lines
    }
}
