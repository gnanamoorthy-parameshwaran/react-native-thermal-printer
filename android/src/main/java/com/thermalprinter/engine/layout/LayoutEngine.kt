/**
 * Layout Engine for thermal printer content. Converts abstract print elements into aligned, padded
 * lines.
 *
 * RESPONSIBILITIES:
 * - Convert Row elements into character-aligned strings
 * - Handle text wrapping within columns
 * - Apply padding and alignment
 * - Merge multi-line columns line-by-line
 *
 * CONSTRAINTS:
 * - No ESC/POS commands (handled by RenderEngine)
 * - No Android APIs
 * - Pure string manipulation
 *
 * DESIGN NOTE: Output is "layoutted lines" with styling info. Actual ESC/POS encoding happens
 * downstream in RenderEngine and EscPosEncoder.
 */
package com.thermalprinter.engine.layout

import com.thermalprinter.engine.model.*

/** Result of layoutting a print element. Multiple lines may be produced from a single element. */
data class LayoutLine(
        val text: String,
        val style: TextStyle = TextStyle(),
        val align: Align = Align.LEFT,
        val commandType: CommandType = CommandType.TEXT
)

enum class CommandType {
    TEXT, // Regular text line
    LINEFEED, // Line feed (blank line)
    DIVIDER, // Divider line
    PAPERCUT // Paper cut command
}

/** Converts abstract PrintElements into aligned layout lines. */
class LayoutEngine(private val config: PrinterConfig) {

    fun layout(elements: List<PrintElement>): List<LayoutLine> {
        val lines = mutableListOf<LayoutLine>()

        for (element in elements) {
            lines.addAll(layoutElement(element))
        }

        return lines
    }

    private fun layoutElement(element: PrintElement): List<LayoutLine> {
        return when (element) {
            is PrintElement.Text -> layoutText(element)
            is PrintElement.Row -> layoutRow(element)
            is PrintElement.LineFeed -> {
                // Create multiple LayoutLines based on count
                (0 until element.count).map {
                    LayoutLine(text = "", align = Align.LEFT, commandType = CommandType.LINEFEED)
                }
            }
            is PrintElement.Divider -> {
                // Create divider line filled with character, width = charsPerLine
                val dividerText = element.char.repeat(config.charsPerLine)
                listOf(
                        LayoutLine(
                                text = dividerText,
                                align = Align.LEFT,
                                commandType = CommandType.DIVIDER
                        )
                )
            }
            is PrintElement.PaperCut ->
                    listOf(
                            LayoutLine(
                                    text = "",
                                    align = Align.LEFT,
                                    commandType = CommandType.PAPERCUT
                            )
                    )
        }
    }

    private fun layoutText(text: PrintElement.Text): List<LayoutLine> {
        // Text elements are single-line; wrapping is handled at row level
        return listOf(LayoutLine(text = text.value, style = text.style, align = text.align))
    }

    private fun layoutRow(row: PrintElement.Row): List<LayoutLine> {
        // Step 1: Wrap each column to its width
        val wrappedColumns = row.columns.map { column -> wrapColumn(column) }

        // Step 2: Merge wrapped lines horizontally
        val numLines = wrappedColumns.maxOfOrNull { it.size } ?: 0
        val mergedLines = mutableListOf<LayoutLine>()

        for (lineIndex in 0 until numLines) {
            val lineParts = mutableListOf<String>()

            for (columnIndex in row.columns.indices) {
                val wrappedColumn = wrappedColumns[columnIndex]
                val column = row.columns[columnIndex]

                // Get this line from the column, or empty if column ran out
                val columnText =
                        if (lineIndex < wrappedColumn.size) {
                            wrappedColumn[lineIndex]
                        } else {
                            ""
                        }

                // Align and pad to column width
                val paddedText = padColumn(columnText, column.width, column.align)
                lineParts.add(paddedText)
            }

            // Merge all parts into a single line
            val mergedLine = lineParts.joinToString("")

            // Determine style: use bold/underline from first styled column in this row
            val style = findStyleForLine(row, lineIndex)

            mergedLines.add(LayoutLine(text = mergedLine, style = style, align = Align.LEFT))
        }

        return mergedLines
    }

    /**
     * Wrap a single column text to its character width. Returns a list of lines, each up to
     * column.width chars. Preserves word boundaries when possible.
     */
    private fun wrapColumn(column: Column): List<String> {
        if (column.text.isEmpty()) {
            return listOf("")
        }

        val lines = mutableListOf<String>()
        val words = column.text.split(Regex("\\s+"))
        var currentLine = ""

        for (word in words) {
            // If word is longer than column width, split it
            if (word.length > column.width) {
                // Flush current line if not empty
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.trimEnd())
                    currentLine = ""
                }

                // Add word fragments
                var remaining = word
                while (remaining.isNotEmpty()) {
                    if (remaining.length <= column.width) {
                        currentLine = remaining
                        remaining = ""
                    } else {
                        lines.add(remaining.take(column.width))
                        remaining = remaining.drop(column.width)
                    }
                }
            } else {
                // Word fits; try to add to current line
                val potentialLine =
                        if (currentLine.isEmpty()) {
                            word
                        } else {
                            "$currentLine $word"
                        }

                if (potentialLine.length <= column.width) {
                    currentLine = potentialLine
                } else {
                    // Flush current line and start new one
                    lines.add(currentLine)
                    currentLine = word
                }
            }
        }

        // Flush remaining
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return if (lines.isEmpty()) listOf("") else lines
    }

    /** Pad/align text to fit within column width. */
    private fun padColumn(text: String, width: Int, align: Align): String {
        if (text.length >= width) {
            // Truncate if text is too long
            return text.take(width)
        }

        val padding = width - text.length
        return when (align) {
            Align.LEFT -> text + " ".repeat(padding)
            Align.CENTER -> {
                val leftPad = padding / 2
                val rightPad = padding - leftPad
                " ".repeat(leftPad) + text + " ".repeat(rightPad)
            }
            Align.RIGHT -> " ".repeat(padding) + text
        }
    }

    /**
     * Determine the style for a specific line within a row. Currently returns style of first styled
     * column that contributes to this line.
     */
    private fun findStyleForLine(row: PrintElement.Row, lineIndex: Int): TextStyle {
        for (column in row.columns) {
            if (column.style.bold || column.style.underline) {
                return column.style
            }
        }
        return TextStyle()
    }
}
