package com.thermalprinter.engine

/** A run of characters sharing one style. */
data class Segment(val text: String, val style: TextStyle = TextStyle())

/** One printed line in the printer's built-in font. Alignment is applied by the printer. */
data class TextLine(
  val segments: List<Segment>,
  val align: Align = Align.LEFT,
  val size: Int = 1,
  /** White on black (GS B). Inverted lines are padded to full width so the band spans the paper. */
  val invert: Boolean = false,
) {
  val length: Int get() = segments.sumOf { it.text.length }
}

/**
 * Lays out elements for the built-in font, where every character is one column. Callers must pass
 * text already sanitized for the code page so that holds.
 */
object TextLayout {

  fun text(element: PrintElement.Text, charsPerLine: Int): List<TextLine> {
    val width = charsPerLine / element.size
    return wrap(element.value, width).map {
      if (element.invert) {
        val padded = cell(it, Column(it, width, element.align, element.style))
        TextLine(padded, Align.LEFT, element.size, invert = true)
      } else {
        TextLine(listOf(Segment(it, element.style)), element.align, element.size)
      }
    }
  }

  fun row(row: PrintElement.Row): List<TextLine> {
    // Inverted rows keep one blank char inside each end, so text doesn't touch the band's edges.
    val last = row.columns.lastIndex
    fun inset(c: Int) = if (row.invert && row.columns[c].width > 1 && (c == 0 || c == last)) 1 else 0
    val wrapped = row.columns.mapIndexed { c, column -> wrap(column.text, column.width - inset(c)) }
    val count = wrapped.maxOf { it.size }
    return (0 until count).map { i ->
      TextLine(
        row.columns.flatMapIndexed { c, column ->
          val text = wrapped[c].getOrElse(i) { "" }
          val cell = cell(text, column.copy(width = column.width - inset(c)))
          when {
            inset(c) == 0 -> cell
            c == 0 -> listOf(Segment(" ")) + cell
            else -> cell + Segment(" ")
          }
        },
        invert = row.invert,
      )
    }
  }

  fun divider(divider: PrintElement.Divider, charsPerLine: Int): List<TextLine> =
    listOf(TextLine(listOf(Segment(divider.char.toString().repeat(charsPerLine)))))

  /** Word-wraps [text] to [width] columns, honouring '\n' and splitting words longer than a line. */
  fun wrap(text: String, width: Int): List<String> {
    require(width > 0) { "width must be > 0" }
    val lines = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
      var line = ""
      for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
        var rest = word
        while (rest.isNotEmpty()) {
          val candidate = if (line.isEmpty()) rest else "$line $rest"
          when {
            candidate.length <= width -> {
              line = candidate
              rest = ""
            }
            line.isNotEmpty() -> {
              lines.add(line)
              line = ""
            }
            else -> {
              lines.add(rest.take(width))
              rest = rest.drop(width)
            }
          }
        }
      }
      lines.add(line)
    }
    return lines
  }

  /** Pads [text] to the column width. Padding is unstyled so underlines cover only the text. */
  private fun cell(text: String, column: Column): List<Segment> {
    val gap = column.width - text.length
    val left = when (column.align) {
      Align.LEFT -> 0
      Align.CENTER -> gap / 2
      Align.RIGHT -> gap
    }
    return listOf(
      Segment(" ".repeat(left)),
      Segment(text, column.style),
      Segment(" ".repeat(gap - left)),
    ).filter { it.text.isNotEmpty() }
  }
}
