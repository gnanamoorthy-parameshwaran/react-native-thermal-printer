/**
 * Text Render Engine (v1). Converts layout lines into printer commands with styling.
 *
 * RESPONSIBILITIES:
 * - Transform LayoutLine objects into PrinterCommand objects
 * - Apply text styling (bold, underline)
 * - Handle alignment directives
 * - Generate line feed and paper cut commands
 *
 * DESIGN FOR EXTENSIBILITY:
 * - A bitmap renderer can be added alongside this without modification
 * - Renderer selection can be dynamic (strategy pattern)
 * - Commands are device-agnostic (encoder transforms them to ESC/POS bytes)
 */
package com.thermalprinter.engine.render

import com.thermalprinter.engine.layout.CommandType
import com.thermalprinter.engine.layout.LayoutLine
import com.thermalprinter.engine.model.Align

/**
 * Device-agnostic printer command. Encoder converts these to ESC/POS bytes.
 *
 * EXTENSION POINT:
 * - BitImageCommand can be added here for bitmap rendering
 * - BarcodeCommand for 1D barcodes
 * - Qr2dCommand for 2D codes
 */
sealed class PrinterCommand {
    /** Text to be printed with optional styling. */
    data class Text(
            val content: String,
            val bold: Boolean = false,
            val underline: Boolean = false,
            val align: Align = Align.LEFT
    ) : PrinterCommand()

    /** Single line feed (newline). */
    data object LineFeed : PrinterCommand()

    /** Cut paper (full cut at top-of-form position). */
    data object PaperCut : PrinterCommand()
}

/** Text renderer - converts layout lines to printer commands. */
class TextRenderer {

    fun render(lines: List<LayoutLine>): List<PrinterCommand> {
        val commands = mutableListOf<PrinterCommand>()

        for (line in lines) {
            when (line.commandType) {
                CommandType.TEXT -> {
                    // Regular text line
                    commands.add(
                            PrinterCommand.Text(
                                    content = line.text,
                                    bold = line.style.bold,
                                    underline = line.style.underline,
                                    align = line.align
                            )
                    )
                    // Every line ends with a line feed (implicit in most printer designs)
                    commands.add(PrinterCommand.LineFeed)
                }
                CommandType.LINEFEED -> {
                    // Explicit line feed command
                    commands.add(PrinterCommand.LineFeed)
                }
                CommandType.DIVIDER -> {
                    // Divider line (just text)
                    commands.add(PrinterCommand.Text(content = line.text, align = line.align))
                    // Divider also ends with line feed
                    commands.add(PrinterCommand.LineFeed)
                }
                CommandType.PAPERCUT -> {
                    // Paper cut command
                    commands.add(PrinterCommand.PaperCut)
                }
            }
        }

        return commands
    }
}

/**
 * Facade combining parser, layout, and rendering. This is the main entry point for the printing
 * pipeline.
 */
class RenderPipeline(private val config: com.thermalprinter.engine.model.PrinterConfig) {
    private val parser = com.thermalprinter.engine.parser.ReceiptParser()
    private val layoutEngine = com.thermalprinter.engine.layout.LayoutEngine(config)
    private val textRenderer = TextRenderer()

    fun renderReceipt(jsonPayload: String): List<PrinterCommand> {
        // Parse JSON → Receipt model
        val receipt = parser.parse(jsonPayload)

        // Layout → LayoutLines
        val layoutLines = layoutEngine.layout(receipt.elements)

        // Render → PrinterCommands
        val commands = textRenderer.render(layoutLines)

        return commands
    }
}
