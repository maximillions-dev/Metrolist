/**
 * Metrolist Project (C) 2024
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.lyrics

import java.util.regex.Pattern

object AppleMusicLyricsParser {
    private val lineRegex = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](v1:|v2:|\\[bg\\]:)?(.*)")
    private val partRegex = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")

    private object GlowProcessor {
        private const val LONG_WORD_GLOW_THRESHOLD = 1.2f
        private const val ACCUMULATED_GLOW_THRESHOLD = 1.2f
        private const val GLOW_SCORE_THRESHOLD = 1.5f

        fun processAndAssignGlow(originalWords: List<Word>): List<Word> {
            val newWords = mutableListOf<Word>()
            val processedIndices = mutableSetOf<Int>()

            for (i in originalWords.indices) {
                if (i in processedIndices) continue

                // Check for multi-part "accumulated glow" words first.
                // A multi-part word is a sequence of words where each one except the last ends with a hyphen.
                if (i < originalWords.size - 1 && originalWords[i].text.endsWith('-')) {
                    val conceptualParts = mutableListOf(originalWords[i])
                    var k = i + 1
                    while (k < originalWords.size) {
                        conceptualParts.add(originalWords[k])
                        if (!originalWords[k-1].text.endsWith('-')) break // Previous word didn't have a hyphen, sequence is broken.
                        if (!originalWords[k].text.endsWith('-')) break // Current word doesn't have a hyphen, this is the end of the sequence.
                        k++
                    }

                    // A valid conceptual word must end with a non-hyphenated part.
                    if (conceptualParts.size > 1 && !conceptualParts.last().text.endsWith('-')) {
                        val conceptualText = conceptualParts.joinToString("") { it.text }
                        if (conceptualText.count { it == '-' } >= 2) {
                            val longestPart = conceptualParts.maxByOrNull { it.endTime - it.startTime }
                            if (longestPart != null && (longestPart.endTime - longestPart.startTime) >= ACCUMULATED_GLOW_THRESHOLD) {
                                // This conceptual word qualifies for an accumulated glow.
                                val totalStrength = conceptualParts.sumOf { if (it == longestPart) 0.50 else 0.25 }.toFloat()
                                conceptualParts.forEach { part ->
                                    newWords.add(part.copy(glowStrength = if (part == longestPart) totalStrength else 0f))
                                    processedIndices.add(originalWords.indexOf(part))
                                }
                                continue // Continue to the next unprocessed word in the main loop.
                            }
                        }
                    }
                }

                // If not part of a processed accumulated glow, handle as a single word.
                val word = originalWords[i]
                val duration = word.endTime - word.startTime
                var strength = 0f
                if (duration >= LONG_WORD_GLOW_THRESHOLD) {
                    val score = (word.text.trim().length / 10.0f) + (duration / 2.0f)
                    if (score > GLOW_SCORE_THRESHOLD) {
                        // Scale the glow strength based on how much the score exceeds the threshold.
                        strength = ((score - GLOW_SCORE_THRESHOLD) * 0.4f).coerceIn(0f, 1.0f)
                    }
                }
                newWords.add(word.copy(glowStrength = strength))
                processedIndices.add(i)
            }
            return newWords
        }
    }

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

                val processedWords = GlowProcessor.processAndAssignGlow(words)
                val fullText = processedWords.joinToString(separator = "") { it.text }
                val lineEndTime = processedWords.lastOrNull()?.endTime ?: lineStartTime

                lines.add(
                    LyricLine(
                        text = fullText,
                        startTime = lineStartTime,
                        endTime = lineEndTime,
                        speaker = speaker,
                        words = processedWords
                    )
                )
            }
        }
        return lines
    }
}
