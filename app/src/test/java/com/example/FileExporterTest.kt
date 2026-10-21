package com.example

import com.example.model.GeneratedFile
import com.example.util.FileExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileExporterTest {

    @Test
    fun testExtractGeneratedFiles_namedFileExplicit() {
        val markdown = """
            İşte istediğiniz web sayfası dosyası:
            ```file:hesap_makinesi.html
            <!DOCTYPE html>
            <html>
            <body><h1>Hesap Makinesi</h1></body>
            </html>
            ```
            Güle güle kullanın!
        """.trimIndent()

        val files = FileExporter.extractGeneratedFiles(markdown)
        assertEquals(1, files.size)
        assertEquals("hesap_makinesi.html", files[0].fileName)
        assertEquals("html", files[0].fileExtension)
        assertTrue(files[0].isHtml)
        assertTrue(files[0].content.contains("Hesap Makinesi"))

        // Verify stripping redundant code blocks
        val stripped = FileExporter.stripFileCodeBlocks(markdown, files)
        assertFalse(stripped.contains("```file:hesap_makinesi.html"))
        assertTrue(stripped.contains("İşte istediğiniz web sayfası dosyası:"))
        assertTrue(stripped.contains("Güle güle kullanın!"))
    }

    @Test
    fun testExtractGeneratedFiles_unspecifiedCodeBecomesDownloadableFile() {
        val markdown = """
            HTML buton örneği:
            ```html
            <button class="btn">Tıkla</button>
            ```
            Bu butonu CSS ile stillendirebilirsiniz.
        """.trimIndent()

        val files = FileExporter.extractGeneratedFiles(markdown)
        assertEquals(1, files.size)
        assertEquals("html", files[0].fileExtension)

        val stripped = FileExporter.stripFileCodeBlocks(markdown, files)
        assertFalse(stripped.contains("```html"))
        assertTrue(stripped.contains("HTML buton örneği:"))
    }

    @Test
    fun testExtractGeneratedFiles_pythonSnippetBecomesDownloadableFile() {
        val markdown = """
            Python ile fibonacci kodu:
            ```python
            def fib(n):
                return n if n <= 1 else fib(n-1) + fib(n-2)
            ```
        """.trimIndent()

        val files = FileExporter.extractGeneratedFiles(markdown)
        assertEquals(1, files.size)
        assertEquals("py", files[0].fileExtension)
    }

    @Test
    fun testExtractGeneratedFiles_explicitDownloadableFileIntent() {
        val markdown = """
            İstediğiniz notları indirilebilir dosya şeklinde oluşturdum:
            ```txt
            1. Görev
            2. Toplantı
            ```
        """.trimIndent()

        val files = FileExporter.extractGeneratedFiles(markdown)
        assertEquals(1, files.size)
        assertEquals("txt", files[0].fileExtension)
        assertTrue(files[0].isTxt)
    }

    @Test
    fun testPrepareDarkHtml() {
        val rawHtml = "<html><body><h1>Başlık</h1></body></html>"
        val darkHtml = FileExporter.prepareDarkHtml(rawHtml)
        assertTrue(darkHtml.contains("#141518"))
        assertTrue(darkHtml.contains("color-scheme"))
    }
}
