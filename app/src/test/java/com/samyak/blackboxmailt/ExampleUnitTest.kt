package com.samyak.blackboxmailt

import com.samyak.blackboxmailt.data.parseIsoToMillis
import com.samyak.blackboxmailt.ui.formatSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testParseIsoToMillis() {
        assertTrue(parseIsoToMillis("2026-08-25T10:00:00Z") > 0)
        assertEquals(0L, parseIsoToMillis(""))
        assertEquals(0L, parseIsoToMillis(null))
        assertEquals(0L, parseIsoToMillis("invalid-date"))
    }

    @Test
    fun testFormatSize() {
        assertEquals("0 B", formatSize(0))
        assertEquals("500 B", formatSize(500))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("2.0 MB", formatSize(2 * 1024 * 1024))
    }

    @Test
    fun testAttachmentPathSanitization() {
        val testCases = listOf(
            "../../etc/passwd" to "passwd",
            ".." to "attachment",
            "." to "attachment",
            "...hidden" to "hidden",
            "invoice#123!.pdf" to "invoice_123_.pdf",
            "normal_file.txt" to "normal_file.txt",
            "" to "attachment"
        )

        for ((input, expectedName) in testCases) {
            val rawName = File(input).name
            val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trimStart('.')
                .ifBlank { "attachment" }
            assertEquals("Failed for input: $input", expectedName, safeName)
            assertFalse(safeName.contains(".."))
            assertFalse(safeName.startsWith("."))
        }
    }
}
