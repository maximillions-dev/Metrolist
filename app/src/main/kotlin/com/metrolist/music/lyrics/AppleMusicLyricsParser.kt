/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.lyrics

import java.util.regex.Pattern

object AppleMusicLyricsParser {

    private val LINE_REGEX = Pattern.compile("^(?:\\[(\\d{2}):(\\d{2})\\.(\\d{3})])?(.*)")
    private val WORD_REGEX = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{3})>([^<]+)")
    private val SPEAKER_REGEX = Pattern.compile("(v1:|v2:|bg:)(.*)")

    fun parse(lyricsText: String): AppleMusicLyrics {
        val lines = mutableListOf<LyricLine>()
        var lastLineTime = 0L
        val allLines = lyricsText.lines().filter { it.isNotBlank() }

        allLines.forEachIndexed { index, line ->
            val lineMatcher = LINE_REGEX.matcher(line)
            if (!lineMatcher.matches()) return@forEachIndexed

            val hasTimestamp = lineMatcher.group(1) != null
            val lineTime = if (hasTimestamp) {
                val time = (lineMatcher.group(1)?.toLong() ?: 0) * 60000 + (lineMatcher.group(2)?.toLong() ?: 0) * 1000 + (lineMatcher.group(3)?.toLong() ?: 0)
                lastLineTime = time
                time
            } else {
                lastLineTime
            }

            val content = lineMatcher.group(4)!!
            val (speaker, lyricsContent) = parseSpeaker(content)

            val segments = mutableListOf<Pair<Long, String>>()
            val wordMatcher = WORD_REGEX.matcher(lyricsContent)
            while (wordMatcher.find()) {
                val time = (wordMatcher.group(1)?.toLong() ?: 0) * 60000 + (wordMatcher.group(2)?.toLong() ?: 0) * 1000 + (wordMatcher.group(3)?.toLong() ?: 0)
                segments.add(Pair(time, wordMatcher.group(4) ?: ""))
            }

            val words = buildWordsFromSegments(segments)

            if (words.isNotEmpty()) {
                val nextStartTime = findNextStartTime(allLines, index)
                updateEndTimes(words, nextStartTime, words.last().startTime + 5000)
                lines.add(LyricLine(speaker, words, lineTime, nextStartTime ?: (words.last().startTime + 5000)))
            }
        }
        return AppleMusicLyrics(lines)
    }

    private fun parseSpeaker(content: String): Pair<Speaker, String> {
        val speakerMatcher = SPEAKER_REGEX.matcher(content)
        return if (speakerMatcher.matches()) {
            val speakerTag = speakerMatcher.group(1) ?: ""
            val sp = when (speakerTag) {
                "v1:" -> Speaker.V1
                "v2:" -> Speaker.V2
                "bg:" -> Speaker.BG
                else -> Speaker.UNKNOWN
            }
            Pair(sp, speakerMatcher.group(2) ?: "")
        } else {
            Pair(Speaker.UNKNOWN, content)
        }
    }

    private fun buildWordsFromSegments(segments: List<Pair<Long, String>>): MutableList<LyricWord> {
        val words = mutableListOf<LyricWord>()
        if (segments.isEmpty()) return words

        var currentWordText = ""
        var currentWordStartTime = segments.first().first

        segments.forEachIndexed { i, (time, rawText) ->
            if (currentWordText.isEmpty()) {
                currentWordStartTime = time
            }
            val trimmedText = rawText.trim()
            currentWordText += trimmedText

            val isLastSegment = i == segments.size - 1
            if (rawText.endsWith(" ") || isLastSegment) {
                words.add(LyricWord(currentWordText, currentWordStartTime, 0))
                currentWordText = ""
            }
        }
        return words
    }

    private fun updateEndTimes(words: MutableList<LyricWord>, nextLineStartTime: Long?, lastWordEndTime: Long) {
        for (i in 0 until words.size - 1) {
            words[i] = words[i].copy(endTime = words[i + 1].startTime)
        }
        if (words.isNotEmpty()) {
            words[words.size - 1] = words.last().copy(endTime = nextLineStartTime ?: lastWordEndTime)
        }
    }

    private fun findNextStartTime(allLines: List<String>, currentLineIndex: Int): Long? {
        for (i in (currentLineIndex + 1) until allLines.size) {
            val nextLine = allLines[i]

            val lineMatcher = LINE_REGEX.matcher(nextLine)
            if (!lineMatcher.matches()) continue

            // A line is considered "empty" for this purpose if it has no content after the timestamp.
            val content = lineMatcher.group(4) ?: ""
            if (content.isBlank()) {
                // It's an empty line like [00:18.000], skip to find the next real line
                continue
            }

            // Now check for timestamps in this non-empty line
            if (lineMatcher.group(1) != null) { // Line-level timestamp
                return (lineMatcher.group(1)?.toLong() ?: 0) * 60000 + (lineMatcher.group(2)?.toLong() ?: 0) * 1000 + (lineMatcher.group(3)?.toLong() ?: 0)
            }

            val wordMatcher = WORD_REGEX.matcher(content)
            if (wordMatcher.find()) { // Word-level timestamp
                return (wordMatcher.group(1)?.toLong() ?: 0) * 60000 + (wordMatcher.group(2)?.toLong() ?: 0) * 1000 + (wordMatcher.group(3)?.toLong() ?: 0)
            }
        }
        return null
    }
}
