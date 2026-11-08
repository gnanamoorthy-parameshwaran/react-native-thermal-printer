package com.thermalprinter.engine

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class Align { LEFT, CENTER, RIGHT }

/** How printable elements are turned into printer data. */
enum class RenderMode {
  /** Printer's built-in font. Characters outside the code page print as '?'. */
  TEXT,
  /** Every element is drawn as a bitmap. */
  IMAGE,
  /** Built-in font when the code page can encode the element, bitmap otherwise. */
  AUTO,
}

data class TextStyle(val bold: Boolean = false, val underline: Boolean = false)

sealed class PrintElement {
  /** A paragraph; wraps to the paper width. [size] 2 is double width and height. */
  data class Text(
    val value: String,
    val align: Align = Align.LEFT,
    val style: TextStyle = TextStyle(),
    val size: Int = 1,
  ) : PrintElement()

  data class Row(val columns: List<Column>) : PrintElement()

  data class Divider(val char: Char = '-') : PrintElement()

  data class Feed(val lines: Int = 1) : PrintElement()

  data object Cut : PrintElement()
}

/** [width] is in characters of the built-in font, so a row lays out the same in both modes. */
data class Column(
  val text: String,
  val width: Int,
  val align: Align = Align.LEFT,
  val style: TextStyle = TextStyle(),
)

/**
 * Character tables selectable with ESC t. [table] is the ESC/POS table number, which is the same
 * on Epson and most compatible printers.
 */
enum class CodePage(private val charsetName: String, val table: Int) {
  PC437("IBM437", 0),
  PC850("IBM850", 2),
  PC858("IBM00858", 19),
  WPC1252("windows-1252", 16);

  private val charset: Charset by lazy {
    if (Charset.isSupported(charsetName)) Charset.forName(charsetName) else Charsets.US_ASCII
  }

  fun canEncode(text: String): Boolean = charset.newEncoder().canEncode(text)

  /** Replaces every code point the table can't print with '?', keeping one char per column. */
  fun sanitize(text: String): String {
    if (canEncode(text)) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val s = String(Character.toChars(cp))
      out.append(if (canEncode(s)) s else "?")
      i += Character.charCount(cp)
    }
    return out.toString()
  }

  fun encode(text: String): ByteArray {
    val buffer = charset.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPLACE)
      .onMalformedInput(CodingErrorAction.REPLACE)
      .encode(java.nio.CharBuffer.wrap(text))
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
  }
}

/** Paper geometry. 80mm printers are 576 dots / 48 chars, 58mm are 384 dots / 32 chars. */
data class PrinterProfile(
  val dotsPerLine: Int = 576,
  val charsPerLine: Int = 48,
  val codePage: CodePage = CodePage.PC437,
  /**
   * Rows per raster command. The printer pauses between commands, so larger is smoother; lower it
   * only if a printer with a small buffer prints garbage.
   */
  val bandRows: Int = 256,
  /**
   * Distance from the print head to the cutter blade. It is also the blank gap at the top of every
   * bill, so measure that gap to find it.
   */
  val cutterDistanceMm: Double = 13.0,
  /** Blank paper below the last line. Defaults to [cutterDistanceMm], matching the top gap. */
  val bottomMarginMm: Double = cutterDistanceMm,
) {
  init {
    require(dotsPerLine > 0 && dotsPerLine % 8 == 0) { "dotsPerLine must be a positive multiple of 8" }
    require(charsPerLine in 1..dotsPerLine) { "charsPerLine must be between 1 and dotsPerLine" }
    require(bandRows in 1..2303) { "bandRows must be 1-2303" }
    require(cutterDistanceMm in 0.0..100.0) { "cutterDistanceMm must be 0-100" }
    require(bottomMarginMm in 0.0..100.0) { "bottomMarginMm must be 0-100" }
  }

  /**
   * Lines fed after the last line so it ends about [bottomMarginMm] past the cut. Fed in lines, not
   * dots: ESC J's unit varies between printers, while the default line is always 1/6 inch. Never
   * less than clears the cutter, so a cut can't go through printed content.
   */
  val cutFeedLines: Int get() {
    val wanted = Math.round((cutterDistanceMm + bottomMarginMm) / LINE_MM).toInt()
    val clearsCutter = Math.ceil((cutterDistanceMm + 1.0) / LINE_MM).toInt()
    return maxOf(wanted, clearsCutter).coerceAtMost(255)
  }

  companion object {
    /** Default ESC/POS line spacing, 1/6 inch. */
    const val LINE_MM = 25.4 / 6
  }

  val dotsPerChar: Int get() = dotsPerLine / charsPerLine
}

data class ImageOptions(
  /** Text height in dots for bitmap rendering. The built-in font is 24 dots tall. */
  val fontSize: Float = 28f,
  /** Font file in the app's Android assets, e.g. "fonts/NotoSansTamil-Regular.ttf". */
  val fontAsset: String? = null,
)

data class PrintJob(
  val profile: PrinterProfile = PrinterProfile(),
  val mode: RenderMode = RenderMode.AUTO,
  val image: ImageOptions = ImageOptions(),
  val elements: List<PrintElement>,
)

data class NetworkPrinter(
  val host: String,
  val port: Int = 9100,
  val timeoutMs: Int = 3000,
  /** Extra connection attempts while another device is printing. */
  val retries: Int = 8,
  /** How long to wait for the printer to finish before disconnecting, if it doesn't report status. */
  val drainTimeoutMs: Int = 10_000,
)
