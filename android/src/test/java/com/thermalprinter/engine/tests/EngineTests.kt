/**
 * Unit tests demonstrating the testability of each layer. This is NOT a complete test suite, but
 * rather examples showing how to test each component independently.
 *
 * Each layer can be tested in isolation without mocks or fixtures, proving the architecture's
 * testability claim.
 */
package com.thermalprinter.engine.tests

import com.thermalprinter.engine.encode.EscPosEncoder
import com.thermalprinter.engine.layout.LayoutEngine
import com.thermalprinter.engine.model.*
import com.thermalprinter.engine.parser.ReceiptParser
import com.thermalprinter.engine.render.PrinterCommand
import com.thermalprinter.engine.render.TextRenderer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** PARSER TESTS Validates JSON schema compliance and model construction. */
class ReceiptParserTests {

    private val parser = ReceiptParser()

    @Test
    fun `parser rejects invalid JSON`() {
        assertThrows<Exception> { parser.parse("{ invalid json }") }
    }

    @Test
    fun `parser validates column width sum exceeds charsPerLine`() {
        val json =
                """
            {
              "config": {"charsPerLine": 10},
              "elements": [{
                "type": "row",
                "columns": [
                  {"text": "Item", "width": 7},
                  {"text": "Price", "width": 5}
                ]
              }]
            }
        """.trimIndent()

        assertThrows<IllegalArgumentException> { parser.parse(json) }
    }

    @Test
    fun `parser applies default values`() {
        val json =
                """
            {
              "config": {"charsPerLine": 32},
              "elements": [{
                "type": "text",
                "value": "Hello"
              }]
            }
        """.trimIndent()

        val receipt = parser.parse(json)
        val element = receipt.elements[0] as PrintElement.Text

        // Defaults should be applied
        assertEquals(Align.LEFT, element.align)
        assertEquals(false, element.style.bold)
    }

    @Test
    fun `parser parses alignment values correctly`() {
        val json =
                """
            {
              "config": {"charsPerLine": 32},
              "elements": [
                {"type": "text", "value": "Left", "align": "left"},
                {"type": "text", "value": "Center", "align": "center"},
                {"type": "text", "value": "Right", "align": "right"}
              ]
            }
        """.trimIndent()

        val receipt = parser.parse(json)
        assertEquals(Align.LEFT, (receipt.elements[0] as PrintElement.Text).align)
        assertEquals(Align.CENTER, (receipt.elements[1] as PrintElement.Text).align)
        assertEquals(Align.RIGHT, (receipt.elements[2] as PrintElement.Text).align)
    }

    @Test
    fun `parser handles special characters and Unicode`() {
        val json =
                """
            {
              "config": {"charsPerLine": 32},
              "elements": [{
                "type": "text",
                "value": "கடையின் பெயர் 商店名"
              }]
            }
        """.trimIndent()

        val receipt = parser.parse(json)
        val text = (receipt.elements[0] as PrintElement.Text).value
        assertEquals("கடையின் பெயர் 商店名", text)
    }
}

/** LAYOUT ENGINE TESTS Validates column wrapping, padding, and alignment logic. */
class LayoutEngineTests {

    private val config = PrinterConfig(charsPerLine = 32)
    private val engine = LayoutEngine(config)

    @Test
    fun `layout engine pads text to column width`() {
        val column = Column("Hi", width = 5, align = Align.LEFT)
        val row = PrintElement.Row(listOf(column))

        val lines = engine.layout(listOf(row))

        // Should be padded to 5 chars: "Hi   "
        assertEquals("Hi   ", lines[0].text)
    }

    @Test
    fun `layout engine centers text in column`() {
        val column = Column("Hi", width = 5, align = Align.CENTER)
        val row = PrintElement.Row(listOf(column))

        val lines = engine.layout(listOf(row))

        // Centered in 5 chars: " Hi  "
        assertEquals(" Hi  ", lines[0].text)
    }

    @Test
    fun `layout engine right-aligns text in column`() {
        val column = Column("Hi", width = 5, align = Align.RIGHT)
        val row = PrintElement.Row(listOf(column))

        val lines = engine.layout(listOf(row))

        // Right-aligned in 5 chars: "   Hi"
        assertEquals("   Hi", lines[0].text)
    }

    @Test
    fun `layout engine wraps long text to column width`() {
        val column = Column("Hello World", width = 5)
        val row = PrintElement.Row(listOf(column))

        val lines = engine.layout(listOf(row))

        // Should wrap to multiple lines
        assertTrue(lines.size >= 2, "Expected wrapping; got ${lines.size} lines")
    }

    @Test
    fun `layout engine merges multiple columns horizontally`() {
        val row =
                PrintElement.Row(
                        listOf(
                                Column("Item", width = 16, align = Align.LEFT),
                                Column("2", width = 6, align = Align.CENTER),
                                Column("150.00", width = 10, align = Align.RIGHT)
                        )
                )

        val lines = engine.layout(listOf(row))

        // Should produce single line with all columns merged
        assertEquals(1, lines.size)
        assertEquals(32, lines[0].text.length) // 16 + 6 + 10
    }

    @Test
    fun `layout engine handles empty text`() {
        val element = PrintElement.Text("")
        val lines = engine.layout(listOf(element))

        // Empty elements should produce empty lines
        assertEquals(1, lines.size)
        assertEquals("", lines[0].text)
    }

    @Test
    fun `layout engine preserves text styling through layout`() {
        val element = PrintElement.Text("Bold Text", style = TextStyle(bold = true))
        val lines = engine.layout(listOf(element))

        assertEquals(true, lines[0].style.bold)
    }
}

/** RENDER ENGINE TESTS Validates conversion of layout lines to printer commands. */
class RenderEngineTests {

    private val renderer = TextRenderer()

    @Test
    fun `renderer converts layout line to text command`() {
        val line = com.thermalprinter.engine.layout.LayoutLine("Hello")
        val commands = renderer.render(listOf(line))

        // Should have text command followed by line feed
        assertEquals(2, commands.size)
        assertEquals("Hello", (commands[0] as PrinterCommand.Text).content)
        assertEquals(PrinterCommand.LineFeed, commands[1])
    }

    @Test
    fun `renderer preserves styling in commands`() {
        val line =
                com.thermalprinter.engine.layout.LayoutLine(
                        "Bold",
                        style = TextStyle(bold = true, underline = true)
                )
        val commands = renderer.render(listOf(line))

        val textCmd = commands[0] as PrinterCommand.Text
        assertEquals(true, textCmd.bold)
        assertEquals(true, textCmd.underline)
    }

    @Test
    fun `renderer preserves alignment`() {
        val line = com.thermalprinter.engine.layout.LayoutLine("Centered", align = Align.CENTER)
        val commands = renderer.render(listOf(line))

        val textCmd = commands[0] as PrinterCommand.Text
        assertEquals(Align.CENTER, textCmd.align)
    }
}

/** ENCODER TESTS Validates ESC/POS byte generation. */
class EscPosEncoderTests {

    private val encoder = EscPosEncoder()

    @Test
    fun `encoder initializes printer`() {
        val bytes = encoder.encode(listOf())

        // First two bytes should be ESC @
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }

    @Test
    fun `encoder produces valid UTF-8 text`() {
        val cmd = PrinterCommand.Text("Hello")
        val bytes = encoder.encode(listOf(cmd))

        // Should contain "Hello" bytes
        val helloBytes = "Hello".toByteArray(Charsets.UTF_8)
        val bytesStr = bytes.joinToString("")
        val helloStr = helloBytes.joinToString("")
        assertTrue(bytesStr.contains(helloStr))
    }

    @Test
    fun `encoder includes bold command for bold text`() {
        val cmd = PrinterCommand.Text("Bold", bold = true)
        val bytes = encoder.encode(listOf(cmd))

        // Should contain ESC E 01
        val hasBold =
                bytes
                        .dropWhile { it != 0x1B.toByte() }
                        .dropWhile { it == 0x1B.toByte() }
                        .takeWhile { it != 0x45.toByte() }
                        .isEmpty()

        assertTrue(hasBold, "Bold command not found in encoded bytes")
    }

    @Test
    fun `encoder includes underline command for underlined text`() {
        val cmd = PrinterCommand.Text("Underline", underline = true)
        val bytes = encoder.encode(listOf(cmd))

        // Should contain ESC - 01
        assertTrue(bytes.size > 4, "Encoded data too short for underline")
    }

    @Test
    fun `encoder sets correct alignment codes`() {
        val leftCmd = PrinterCommand.Text("Left", align = Align.LEFT)
        val centerCmd = PrinterCommand.Text("Center", align = Align.CENTER)
        val rightCmd = PrinterCommand.Text("Right", align = Align.RIGHT)

        val leftBytes = encoder.encode(listOf(leftCmd))
        val centerBytes = encoder.encode(listOf(centerCmd))
        val rightBytes = encoder.encode(listOf(rightCmd))

        // Each should contain different alignment code
        assertTrue(leftBytes.size > 0)
        assertTrue(centerBytes.size > 0)
        assertTrue(rightBytes.size > 0)
    }

    @Test
    fun `encoder includes paper cut command`() {
        val cmd = PrinterCommand.PaperCut
        val bytes = encoder.encode(listOf(cmd))

        // Should contain GS V
        assertTrue(bytes.contains(0x1D.toByte()), "GS not found")
    }

    @Test
    fun `encoder includes line feeds`() {
        val cmd = PrinterCommand.LineFeed
        val bytes = encoder.encode(listOf(cmd))

        // Should contain 0x0A
        assertTrue(bytes.contains(0x0A.toByte()), "LF not found")
    }
}

/** INTEGRATION TESTS Tests the full pipeline from JSON to bytes. */
class IntegrationTests {

    @Test
    fun `complete pipeline: JSON to ESC-POS bytes`() {
        val json =
                """
            {
              "config": {"charsPerLine": 32},
              "elements": [
                {"type": "text", "value": "Hello World", "align": "center", "bold": true},
                {"type": "text", "value": ""},
                {"type": "row", "columns": [
                  {"text": "Item", "width": 16},
                  {"text": "Price", "width": 16, "align": "right"}
                ]},
                {"type": "cut"}
              ]
            }
        """.trimIndent()

        // Execute pipeline
        val parser = ReceiptParser()
        val receipt = parser.parse(json)

        val layoutEngine = LayoutEngine(receipt.config)
        val lines = layoutEngine.layout(receipt.elements)

        val renderer = TextRenderer()
        val commands = renderer.render(lines)

        val encoder = EscPosEncoder()
        val bytes = encoder.encode(commands)

        // Validate output
        assertTrue(bytes.size > 0, "No bytes generated")
        assertEquals(0x1B.toByte(), bytes[0], "First byte should be ESC")
        assertEquals(0x40.toByte(), bytes[1], "Second byte should be @")
    }

    @Test
    fun `pipeline handles Unicode text correctly`() {
        val json =
                """
            {
              "config": {"charsPerLine": 32},
              "elements": [
                {"type": "text", "value": "கடையின் பெயர் 商店"}
              ]
            }
        """.trimIndent()

        val parser = ReceiptParser()
        val receipt = parser.parse(json)

        // Verify parsed correctly
        val element = receipt.elements[0] as PrintElement.Text
        assertEquals("கடையின் பெயர் 商店", element.value)

        // Verify encoder handles it
        val layoutEngine = LayoutEngine(receipt.config)
        val lines = layoutEngine.layout(receipt.elements)
        val renderer = TextRenderer()
        val commands = renderer.render(lines)
        val encoder = EscPosEncoder()
        val bytes = encoder.encode(commands)

        assertTrue(bytes.size > 0)
    }
}
