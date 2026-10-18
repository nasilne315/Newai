package com.example

import com.example.ui.MarkdownBlock
import com.example.ui.formatInlineMarkdown
import com.example.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownFormattingTest {

    @Test
    fun testTableParsing() {
        val markdown = """
            | Meyve | Renk | Fiyat |
            |---|---|---|
            | Elma | Kırmızı | 20 TL |
            | Muz | Sarı | 35 TL |
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Table)

        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("Meyve", "Renk", "Fiyat"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Elma", "Kırmızı", "20 TL"), table.rows[0])
    }

    @Test
    fun testInlineFormattingAndSymbolCleanup() {
        val input = "**Önemli Bilgi** ve __Altı Çizili Terim__ ve ##gereksiz"
        val formatted = formatInlineMarkdown(input)

        assertFalse(formatted.text.contains("**"))
        assertFalse(formatted.text.contains("##"))
        assertFalse(formatted.text.contains("__"))
        assertTrue(formatted.text.contains("Önemli Bilgi"))
        assertTrue(formatted.text.contains("Altı Çizili Terim"))
    }

    @Test
    fun testMathBlockParsing() {
        val mathMarkdown = "$$ f(x) = x^2 + 2x + 1 $$"
        val blocks = parseMarkdown(mathMarkdown)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.MathBlock)
        assertEquals("f(x) = x^2 + 2x + 1", (blocks[0] as MarkdownBlock.MathBlock).formula)
    }

    @Test
    fun testDividersAndCallouts() {
        val markdown = """
            # Ana Başlık
            İlk paragraf
            ---
            > Önemli bir alıntı veya özet kutusu
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertTrue(blocks[2] is MarkdownBlock.Divider)
        assertTrue(blocks[3] is MarkdownBlock.Callout)
    }
}
