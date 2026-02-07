/**
 * ESC/POS Encoder - The final stage of the printing pipeline.
 *
 * SINGLE RESPONSIBILITY:
 * - Encodes device-agnostic PrinterCommand objects into ESC/POS byte sequences
 * - Centralizes all ESC/POS magic numbers and command definitions
 * - No logic; pure encoding
 *
 * DESIGN PHILOSOPHY:
 * - All byte constants defined here, never scattered in code
 * - Easily extensible for raster graphics, barcodes, QR codes
 * - No assumptions about transport (Network, USB, Bluetooth)
 *
 * ESC/POS SPEC REFERENCE:
 * - Escape character: 0x1B
 * - Select Printer Command: ESC @ (0x1B 0x40)
 * - Text styling uses ESC ! (0x1B 0x21) with bitmap flags
 * - Paper cut: GS V (0x1D 0x56 0x00 or 0x41)
 */
package com.thermalprinter.engine.encode

import com.thermalprinter.engine.model.Align
import com.thermalprinter.engine.render.PrinterCommand

/** ESC/POS command definitions and constants. */
object EscPosCodes {
    // Control characters
    const val ESC: Byte = 0x1B
    const val GS: Byte = 0x1D
    const val LF: Byte = 0x0A
    const val CR: Byte = 0x0D

    // ESC @ - Initialize printer
    const val INIT_CMD1 = 0x1B.toByte()
    const val INIT_CMD2 = 0x40.toByte()

    // ESC E - Bold mode (0=off, 1=on)
    const val BOLD_CMD1 = 0x1B.toByte()
    const val BOLD_CMD2 = 0x45.toByte()

    // ESC - - Underline (0=off, 1=single, 2=double)
    const val UNDERLINE_CMD1 = 0x1B.toByte()
    const val UNDERLINE_CMD2 = 0x2D.toByte()

    // ESC a - Text alignment (0=left, 1=center, 2=right)
    const val ALIGN_CMD1 = 0x1B.toByte()
    const val ALIGN_CMD2 = 0x61.toByte()

    // GS V - Paper cut (0x00=full, 0x41=partial)
    const val CUT_CMD1 = 0x1D.toByte()
    const val CUT_CMD2 = 0x56.toByte()
    const val CUT_FULL = 0x00.toByte()
    const val CUT_PARTIAL = 0x41.toByte()

    // Newline
    const val NEWLINE_CMD = 0x0A.toByte()
}

/**
 * Encodes PrinterCommand objects into ESC/POS byte sequences. Stateless; can be instantiated
 * multiple times or used as singleton.
 */
class EscPosEncoder {

    fun encode(commands: List<PrinterCommand>): ByteArray {
        val buffer = mutableListOf<Byte>()

        // Initialize printer (clear any previous state)
        buffer.addAll(initializePrinter())

        for (command in commands) {
            buffer.addAll(encodeCommand(command))
        }

        return buffer.toByteArray()
    }

    private fun initializePrinter(): List<Byte> {
        return listOf(EscPosCodes.INIT_CMD1, EscPosCodes.INIT_CMD2)
    }

    private fun encodeCommand(command: PrinterCommand): List<Byte> {
        return when (command) {
            is PrinterCommand.Text -> encodeText(command)
            PrinterCommand.LineFeed -> listOf(EscPosCodes.LF)
            PrinterCommand.PaperCut -> encodePaperCut()
        }
    }

    private fun encodeText(command: PrinterCommand.Text): List<Byte> {
        val buffer = mutableListOf<Byte>()

        // Set alignment
        buffer.addAll(encodeAlignment(command.align))

        // Set text styling
        if (command.bold) {
            buffer.addAll(encodeBold(true))
        }
        if (command.underline) {
            buffer.addAll(encodeUnderline(true))
        }

        // Encode text content (UTF-8 with BOM for proper encoding)
        buffer.addAll(command.content.toByteArray(Charsets.UTF_8).toList())

        // Reset styling after text
        if (command.bold) {
            buffer.addAll(encodeBold(false))
        }
        if (command.underline) {
            buffer.addAll(encodeUnderline(false))
        }

        // Implicit line feed handled by TextRenderer
        return buffer
    }

    private fun encodeAlignment(align: Align): List<Byte> {
        val alignCode =
                when (align) {
                    Align.LEFT -> 0x00.toByte()
                    Align.CENTER -> 0x01.toByte()
                    Align.RIGHT -> 0x02.toByte()
                }
        return listOf(EscPosCodes.ALIGN_CMD1, EscPosCodes.ALIGN_CMD2, alignCode)
    }

    private fun encodeBold(enabled: Boolean): List<Byte> {
        val flag = if (enabled) 0x01.toByte() else 0x00.toByte()
        return listOf(EscPosCodes.BOLD_CMD1, EscPosCodes.BOLD_CMD2, flag)
    }

    private fun encodeUnderline(enabled: Boolean): List<Byte> {
        val flag = if (enabled) 0x01.toByte() else 0x00.toByte()
        return listOf(EscPosCodes.UNDERLINE_CMD1, EscPosCodes.UNDERLINE_CMD2, flag)
    }

    private fun encodePaperCut(): List<Byte> {
        return listOf(EscPosCodes.CUT_CMD1, EscPosCodes.CUT_CMD2, EscPosCodes.CUT_FULL)
    }
}
