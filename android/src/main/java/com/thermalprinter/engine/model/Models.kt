/**
 * Core data models for the thermal printer engine. These models form the internal representation of
 * print content. They are deliberately decoupled from React Native and ESC/POS specifics.
 *
 * DESIGN PRINCIPLE:
 * - These models must remain stable as new features (bitmap, barcode) are added
 * - Only new sealed class subclasses should be added, never modify existing ones
 * - Parser converts RN JSON → These models
 * - Renderers transform these models → Printer commands
 */
package com.thermalprinter.engine.model

/**
 * Represents a single print element in the receipt. Extensible via sealed class hierarchy without
 * breaking existing code.
 */
sealed class PrintElement {
    /**
     * Plain text element with optional styling. Single line; layout engine handles wrapping at row
     * level.
     */
    data class Text(
            val value: String,
            val align: Align = Align.LEFT,
            val style: TextStyle = TextStyle()
    ) : PrintElement()

    /**
     * Columnar row element. Columns are laid out horizontally with fixed character widths.
     * Extension point: For bitmap rendering, Row could have a bitmap variant
     */
    data class Row(val columns: List<Column>) : PrintElement()

    /** Line feed (newline) element with optional count for multiple line feeds. */
    data class LineFeed(val count: Int = 1) : PrintElement()

    /** Divider line element with optional character. */
    data class Divider(val char: String = "-") : PrintElement()

    /** Paper cut command. Can be extended with CUT_PARTIAL variant later. */
    data object PaperCut : PrintElement()
}

/** Represents a column within a row. */
data class Column(
        val text: String,
        val width: Int,
        val align: Align = Align.LEFT,
        val style: TextStyle = TextStyle()
)

/** Text styling flags. Extensible: italics, font selection can be added later. */
data class TextStyle(val bold: Boolean = false, val underline: Boolean = false)

/** Text alignment modes. JUSTIFY could be added later for justified columns. */
enum class Align {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Printer configuration. Immutable and centralized. Extension point: dpi, language, code page can
 * be added here.
 */
data class PrinterConfig(val charsPerLine: Int) {
    init {
        require(charsPerLine > 0) { "charsPerLine must be > 0" }
    }
}

/** Complete receipt model. This is what the parser produces and the layout engine consumes. */
data class Receipt(val config: PrinterConfig, val elements: List<PrintElement>)
