package com.thermalprinter.engine

/** Printer-ready pieces of a receipt, in print order. */
sealed class Block {
  data class Text(val lines: List<TextLine>) : Block()

  class Image(val image: MonoImage) : Block()

  data class Feed(val lines: Int) : Block()

  data object Cut : Block()
}

/** Draws elements as bitmaps. The Android implementation uses Canvas; tests use a fake. */
interface Rasterizer {
  /** Draws a Text, Row or Divider at the full paper width. */
  fun rasterize(element: PrintElement, profile: PrinterProfile): MonoImage

  /** Approximates built-in-font lines, for previews. */
  fun rasterizeTextLines(lines: List<TextLine>, profile: PrinterProfile): MonoImage
}

/** Picks text or bitmap output for each element according to [mode]. */
class ReceiptRenderer(
  private val profile: PrinterProfile,
  private val mode: RenderMode,
  private val rasterizer: Rasterizer,
) {

  fun render(elements: List<PrintElement>): List<Block> = trimLeading(merge(
    elements.map { element ->
      when (element) {
        // In image mode a feed is blank raster, so it doesn't break up the bill's one image.
        is PrintElement.Feed ->
          if (mode == RenderMode.IMAGE) Block.Image(MonoImage.blank(profile.dotsPerLine, element.lines * Preview.LINE_DOTS))
          else Block.Feed(element.lines)
        PrintElement.Cut -> Block.Cut
        else ->
          if (useImage(element)) Block.Image(rasterizer.rasterize(element, profile))
          else Block.Text(layout(element))
      }
    }
  ))

  /**
   * Starts the bill at its first black dot. Fonts reserve space above the glyphs (a lot, for
   * Tamil), which would otherwise add to the blank gap the cutter already leaves at the top.
   */
  private fun trimLeading(blocks: List<Block>): List<Block> {
    val first = blocks.firstOrNull()
    return if (first is Block.Image) listOf(Block.Image(first.image.trimTop())) + blocks.drop(1) else blocks
  }

  /** Joins adjacent images: the printer stops the paper between raster commands, not within one. */
  private fun merge(blocks: List<Block>): List<Block> {
    val out = mutableListOf<Block>()
    for (block in blocks) {
      val last = out.lastOrNull()
      if (block is Block.Image && last is Block.Image) {
        out[out.size - 1] = Block.Image(MonoImage.stack(listOf(last.image, block.image)))
      } else {
        out.add(block)
      }
    }
    return out
  }

  private fun useImage(element: PrintElement): Boolean = when (mode) {
    RenderMode.TEXT -> false
    RenderMode.IMAGE -> true
    RenderMode.AUTO -> !strings(element).all(profile.codePage::canEncode)
  }

  private fun layout(element: PrintElement): List<TextLine> {
    val cp = profile.codePage
    return when (element) {
      is PrintElement.Text -> TextLayout.text(element.copy(value = cp.sanitize(element.value)), profile.charsPerLine)
      is PrintElement.Row -> TextLayout.row(element.copy(columns = element.columns.map { it.copy(text = cp.sanitize(it.text)) }))
      is PrintElement.Divider -> TextLayout.divider(element.copy(char = cp.sanitize(element.char.toString())[0]), profile.charsPerLine)
      else -> error("not a text element: $element")
    }
  }

  private fun strings(element: PrintElement): List<String> = when (element) {
    is PrintElement.Text -> listOf(element.value)
    is PrintElement.Row -> element.columns.map { it.text }
    is PrintElement.Divider -> listOf(element.char.toString())
    else -> emptyList()
  }
}

/** Stacks rendered blocks into one image showing what the paper will look like. */
object Preview {
  /** Default ESC/POS line spacing: 1/6 inch at 203 dpi. */
  const val LINE_DOTS = 34

  fun compose(blocks: List<Block>, profile: PrinterProfile, rasterizer: Rasterizer): MonoImage {
    val width = profile.dotsPerLine
    val parts = blocks.map { block ->
      when (block) {
        is Block.Text -> rasterizer.rasterizeTextLines(block.lines, profile)
        is Block.Image -> block.image
        is Block.Feed -> MonoImage.blank(width, block.lines * LINE_DOTS)
        Block.Cut -> cutMark(width)
      }
    }
    return MonoImage.stack(parts.ifEmpty { listOf(MonoImage.blank(width, 1)) })
  }

  private fun cutMark(width: Int): MonoImage {
    val image = MonoImage.blank(width, 17)
    val row = 8 * image.bytesPerRow
    for (i in 0 until image.bytesPerRow) image.data[row + i] = 0xF0.toByte()
    return image
  }
}
