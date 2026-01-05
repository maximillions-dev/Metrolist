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
        private const val GLOW_SCORE_THRESHOLD = 1.2f // Lowered threshold for more sensitivity

        fun processAndAssignGlow(originalWords: List<Word>): List<Word> {
            val newWords = mutableListOf<Word>()
            val processedIndices = mutableSetOf<Int>()

            for (i in originalWords.indices) {
                if (i in processedIndices) continue

                val currentWord = originalWords[i]
                val conceptualParts = mutableListOf(currentWord)

                // Group words that are not separated by a space into a "conceptual word"
                if (!currentWord.text.endsWith(" ") && i < originalWords.size - 1) {
                    for (j in i + 1 until originalWords.size) {
                        val nextWord = originalWords[j]
                        conceptualParts.add(nextWord)
                        if (nextWord.text.endsWith(" ")) {
                            break // End of conceptual word
                        }
                    }
                }

                // Now, process the conceptual word (which might be just a single word)
                processedIndices.addAll(conceptualParts.map { originalWords.indexOf(it) })

                val conceptualText = conceptualParts.joinToString("") { it.text }.trim()
                val conceptualStartTime = conceptualParts.first().startTime
                val conceptualEndTime = conceptualParts.last().endTime
                val conceptualDuration = conceptualEndTime - conceptualStartTime

                var glowStrength = 0f
                var glowingPart: Word? = null

                // First, check for hyphenated "accumulated glow"
                if (conceptualText.count { it == '-' } >= 2) {
                    val longestPart = conceptualParts.maxByOrNull { it.endTime - it.startTime }
                    if (longestPart != null) {
                         val longestPartDuration = longestPart.endTime - longestPart.startTime
                         if (longestPartDuration >= LONG_WORD_GLOW_THRESHOLD) { // Use same threshold
                            glowStrength = conceptualParts.sumOf { if (it == longestPart) 0.50 else 0.25 }.toFloat()
                            glowingPart = longestPart
                         }
                    }
                }

                // If not an accumulated glow, check for "long word glow"
                if (glowStrength == 0f && conceptualDuration >= LONG_WORD_GLOW_THRESHOLD) {
                    // New formula with more weight on duration
                    val score = (conceptualText.length / 15.0f) + (conceptualDuration / 1.5f)
                    if (score > GLOW_SCORE_THRESHOLD) {
                        glowStrength = ((score - GLOW_SCORE_THRESHOLD) * 0.5f).coerceIn(0f, 1.0f)
                        // The glow applies to the longest part of the conceptual word
                        glowingPart = conceptualParts.maxByOrNull { it.endTime - it.startTime }
                    }
                }

                // Add the processed parts to the new word list
                conceptualParts.forEach { part ->
                    newWords.add(part.copy(glowStrength = if (part == glowingPart) glowStrength else 0f))
                }
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
