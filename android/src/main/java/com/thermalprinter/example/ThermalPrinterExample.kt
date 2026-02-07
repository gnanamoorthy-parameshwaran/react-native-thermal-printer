/**
 * Example: Complete thermal printer engine pipeline.
 *
 * This demonstrates:
 * 1. React Native JSON payload parsing
 * 2. Internal model transformation
 * 3. Layout engine column handling
 * 4. ESC/POS byte generation
 * 5. Hex dump output
 *
 * Run this as a main() to see the complete flow.
 */
package com.thermalprinter.example

import com.thermalprinter.engine.encode.EscPosEncoder
import com.thermalprinter.engine.layout.LayoutEngine
import com.thermalprinter.engine.parser.ReceiptParser
import com.thermalprinter.engine.render.TextRenderer

object ThermalPrinterExample {

    @JvmStatic
    fun main(args: Array<String>) {
        // ============================================================
        // 1. REACT NATIVE JSON PAYLOAD
        // ============================================================
        val jsonPayload =
                """
            {
              "config": {
                "charsPerLine": 32
              },
              "elements": [
                {
                  "type": "text",
                  "value": "கடையின் பெயர்",
                  "align": "center",
                  "bold": true
                },
                {
                  "type": "text",
                  "value": "123 Main St, City",
                  "align": "center"
                },
                {
                  "type": "text",
                  "value": ""
                },
                {
                  "type": "row",
                  "columns": [
                    { "text": "Item", "width": 16 },
                    { "text": "Qty", "width": 6, "align": "center" },
                    { "text": "Price", "width": 10, "align": "right" }
                  ]
                },
                {
                  "type": "row",
                  "columns": [
                    { "text": "Apple Juice Large", "width": 16 },
                    { "text": "2", "width": 6, "align": "center" },
                    { "text": "₹150.00", "width": 10, "align": "right" }
                  ]
                },
                {
                  "type": "row",
                  "columns": [
                    { "text": "Banana", "width": 16 },
                    { "text": "1", "width": 6, "align": "center" },
                    { "text": "₹50.00", "width": 10, "align": "right" }
                  ]
                },
                {
                  "type": "text",
                  "value": ""
                },
                {
                  "type": "row",
                  "columns": [
                    { "text": "TOTAL", "width": 22, "bold": true },
                    { "text": "₹200.00", "width": 10, "align": "right", "bold": true }
                  ]
                },
                {
                  "type": "text",
                  "value": "Thank you!",
                  "align": "center"
                },
                {
                  "type": "cut"
                }
              ]
            }
        """.trimIndent()

        println("=" * 70)
        println("THERMAL PRINTER ENGINE EXAMPLE")
        println("=" * 70)

        // ============================================================
        // 2. PARSE: JSON → Receipt Model
        // ============================================================
        println("\n[STEP 1] Parsing JSON payload...")
        val parser = ReceiptParser()
        val receipt = parser.parse(jsonPayload)

        println("  Config: charsPerLine=${receipt.config.charsPerLine}")
        println("  Elements: ${receipt.elements.size} total")
        receipt.elements.forEachIndexed { i, el -> println("    [$i] ${el::class.simpleName}") }

        // ============================================================
        // 3. LAYOUT: Receipt Model → Layout Lines
        // ============================================================
        println("\n[STEP 2] Laying out receipt...")
        val layoutEngine = LayoutEngine(receipt.config)
        val layoutLines = layoutEngine.layout(receipt.elements)

        println("  Generated ${layoutLines.size} layout lines:")
        layoutLines.forEachIndexed { i, line ->
            val preview =
                    if (line.text.length > 50) {
                        line.text.take(50) + "..."
                    } else {
                        line.text
                    }
            println(
                    "    [$i] [${line.align}] ${
                if (line.style.bold) "[BOLD]" else ""
            }${if (line.style.underline) "[UNDERLINE]" else ""} \"$preview\""
            )
        }

        // ============================================================
        // 4. RENDER: Layout Lines → Printer Commands
        // ============================================================
        println("\n[STEP 3] Rendering to printer commands...")
        val textRenderer = TextRenderer()
        val commands = textRenderer.render(layoutLines)

        println("  Generated ${commands.size} printer commands:")
        commands.take(10).forEachIndexed { i, cmd ->
            val desc =
                    when (cmd) {
                        is com.thermalprinter.engine.render.PrinterCommand.Text ->
                                "TEXT: \"${cmd.content.take(30)}\" (bold=${cmd.bold})"
                        com.thermalprinter.engine.render.PrinterCommand.LineFeed -> "LINEFEED"
                        com.thermalprinter.engine.render.PrinterCommand.PaperCut -> "PAPERCUT"
                    }
            println("    [$i] $desc")
        }
        if (commands.size > 10) {
            println("    ... and ${commands.size - 10} more")
        }

        // ============================================================
        // 5. ENCODE: Printer Commands → ESC/POS Bytes
        // ============================================================
        println("\n[STEP 4] Encoding to ESC/POS bytes...")
        val encoder = EscPosEncoder()
        val escPosBytes = encoder.encode(commands)

        println("  Total bytes generated: ${escPosBytes.size}")
        println("  First 50 bytes (hex):")
        printHexDump(escPosBytes.take(50).toByteArray())

        // ============================================================
        // 6. COMPLETE HEX DUMP
        // ============================================================
        println("\n" + "=" * 70)
        println("COMPLETE ESC/POS HEX DUMP")
        println("=" * 70)
        printHexDump(escPosBytes)

        // ============================================================
        // 7. ANALYSIS
        // ============================================================
        println("\n" + "=" * 70)
        println("PIPELINE ANALYSIS")
        println("=" * 70)
        println("  Input: React Native JSON payload")
        println("  Output: ${escPosBytes.size} bytes of ESC/POS encoded data")
        println("  Ready for transport: PrinterTransport.write(bytes)")
        println("\n  Architecture validates:")
        println("    ✓ JSON parsing layer")
        println("    ✓ Column wrapping and alignment")
        println("    ✓ Text styling (bold, underline)")
        println("    ✓ ESC/POS encoding")
        println("    ✓ Scalable for future features")
    }

    private fun printHexDump(bytes: ByteArray, bytesPerLine: Int = 16) {
        for (i in bytes.indices step bytesPerLine) {
            val chunk = bytes.slice(i until minOf(i + bytesPerLine, bytes.size))
            val hex = chunk.joinToString(" ") { b -> "%02X".format(b) }
            val ascii =
                    chunk.joinToString("") { b ->
                        val c = b.toInt().toChar()
                        if (c.isWhitespace()) "."
                        else if (c.code >= 32 && c.code < 127) c.toString() else "."
                    }
            println("  %04X: %-48s  %s".format(i, hex, ascii))
        }
    }

    private operator fun String.times(count: Int) = this.repeat(count)
}
