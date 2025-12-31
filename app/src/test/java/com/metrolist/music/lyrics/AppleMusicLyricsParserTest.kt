package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicLyricsParserTest {

    private val sampleLrc = """
[00:10.000]v1:<00:11.000>He<00:11.500>llo <00:12.000>World
[00:13.000]bg:<00:13.500>This<00:14.000>is<00:14.500>a<00:15.000>test
bg:<00:16.000>No <00:16.500>timestamp
[00:18.000]
[00:19.000]v2:<00:20.000>Final <00:20.500>Line
"""

    @Test
    fun `parse should correctly handle various enhanced LRC formats`() {
        val parsedLyrics = AppleMusicLyricsParser.parse(sampleLrc)

        assertEquals("Should contain 4 lines with lyrics", 4, parsedLyrics.lines.size)

        // Line 1: Syllable concatenation and new word with space
        val line1 = parsedLyrics.lines[0]
        assertEquals(10000, line1.startTime)
        assertEquals(Speaker.V1, line1.speaker)
        assertEquals(2, line1.words.size)
        assertEquals("Hello", line1.words[0].text)
        assertEquals(11000, line1.words[0].startTime)
        assertEquals(12000, line1.words[0].endTime)
        assertEquals("World", line1.words[1].text)
        assertEquals(12000, line1.words[1].startTime)
        assertEquals(13000, line1.words[1].endTime) // End time is start of next line

        // Line 2: No spaces, should be one word, regardless of speaker
        val line2 = parsedLyrics.lines[1]
        assertEquals(13000, line2.startTime)
        assertEquals(Speaker.BG, line2.speaker)
        assertEquals(1, line2.words.size)
        assertEquals("Thisisatest", line2.words[0].text)
        assertEquals(13500, line2.words[0].startTime)
        assertEquals(16000, line2.words[0].endTime) // End time is start of first word in next line

        // Line 3: BG line with no line-level timestamp
        val line3 = parsedLyrics.lines[2]
        assertEquals("Should inherit previous line's timestamp", 13000, line3.startTime)
        assertEquals(Speaker.BG, line3.speaker)
        assertEquals(2, line3.words.size)
        assertEquals("No", line3.words[0].text)
        assertEquals(16000, line3.words[0].startTime)
        assertEquals(16500, line3.words[0].endTime)
        assertEquals("timestamp", line3.words[1].text)
        assertEquals(16500, line3.words[1].startTime)
        assertEquals(19000, line3.words[1].endTime) // End time is start of next non-empty line

        // Line 4: Final line
        val line4 = parsedLyrics.lines[3]
        assertEquals(19000, line4.startTime)
        assertEquals(Speaker.V2, line4.speaker)
        assertEquals(2, line4.words.size)
        assertEquals("Final", line4.words[0].text)
        assertEquals(20000, line4.words[0].startTime)
        assertEquals(20500, line4.words[0].endTime)
        assertEquals("Line", line4.words[1].text)
        assertEquals(20500, line4.words[1].startTime)
        assertTrue("End time should be greater than start time", line4.words[1].endTime > line4.words[1].startTime)
    }
}
