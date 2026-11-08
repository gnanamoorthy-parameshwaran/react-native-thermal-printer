package com.thermalprinter.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.thermalprinter.engine.Align
import com.thermalprinter.engine.MonoImage
import com.thermalprinter.engine.Preview
import com.thermalprinter.engine.PrintElement
import com.thermalprinter.engine.PrinterProfile
import com.thermalprinter.engine.Segment
import com.thermalprinter.engine.TextLayout
import com.thermalprinter.engine.TextLine
import com.thermalprinter.engine.TextStyle

/**
 * Draws elements with Android's text stack, which shapes Tamil and other complex scripts and falls
 * back to system fonts for glyphs [typeface] lacks. Wrapping is measured in dots, so wide glyphs
 * wrap correctly; the bitmap is exactly as tall as the wrapped text.
 */
class AndroidRasterizer(
  private val typeface: Typeface,
  private val fontSize: Float,
) : com.thermalprinter.engine.Rasterizer {

  override fun rasterize(element: PrintElement, profile: PrinterProfile): MonoImage =
    when (element) {
      is PrintElement.Text -> {
        // Inverted text keeps clear of the band's edges.
        val inset = if (element.invert) profile.dotsPerChar else 0
        val width = profile.dotsPerLine - 2 * inset
        val paint = paint(element.style, fontSize * element.size)
        // A line that only just overflows is drawn slightly smaller instead of wrapping a word.
        val full = paint.measureText(element.value)
        if (full > width && full * NEAR_FIT <= width) paint.textSize *= width / full * 0.98f
        val layout = layout(element.value, paint, width, element.align)
        draw(profile.dotsPerLine, layout.height) { canvas ->
          canvas.translate(inset.toFloat(), 0f)
          layout.draw(canvas)
        }
      }
      is PrintElement.Row -> {
        // Columns are sized in built-in-font chars, but this font is wider, so neighbours keep a
        // gap and a word too wide for its cell is drawn smaller rather than broken or overlapping.
        // The gap sits on the left only, so right-aligned amounts line up with text-mode rows.
        val gap = profile.dotsPerChar
        // Inverted rows keep clear of the band's edges.
        val inset = if (element.invert) profile.dotsPerChar else 0
        val last = element.columns.lastIndex
        var left = 0
        val cells = element.columns.mapIndexed { i, column ->
          val width = column.width * profile.dotsPerChar
          val padLeft = if (i > 0) gap else inset
          val inner = width - padLeft - (if (i == last) inset else 0)
          val paint = fitted(column.text, paint(column.style, fontSize), inner)
          val cell = left + padLeft to layout(column.text, paint, inner, column.align)
          left += width
          cell
        }
        draw(profile.dotsPerLine, cells.maxOf { it.second.height }) { canvas ->
          for ((x, layout) in cells) {
            canvas.save()
            canvas.translate(x.toFloat(), 0f)
            layout.draw(canvas)
            canvas.restore()
          }
        }
      }
      is PrintElement.Divider -> rasterizeTextLines(TextLayout.divider(element, profile.charsPerLine), profile)
      else -> throw IllegalArgumentException("can't rasterize $element")
    }

  /** Draws lines in a monospace font sized to the built-in font's 12 x 24 dot cell. */
  override fun rasterizeTextLines(lines: List<TextLine>, profile: PrinterProfile): MonoImage {
    val height = lines.sumOf { Preview.LINE_DOTS * it.size }
    return draw(profile.dotsPerLine, height) { canvas ->
      var top = 0f
      for (line in lines) {
        val cell = profile.dotsPerChar * line.size
        val lineHeight = Preview.LINE_DOTS * line.size
        val columns = profile.charsPerLine / line.size
        var x = cell * when (line.align) {
          Align.LEFT -> 0
          Align.CENTER -> (columns - line.length) / 2
          Align.RIGHT -> columns - line.length
        }.coerceAtLeast(0).toFloat()
        for (segment in line.segments) {
          val paint = monospace(segment, cell.toFloat())
          val metrics = paint.fontMetrics
          val baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
          canvas.drawText(segment.text, x, baseline, paint)
          x += cell * segment.text.length
        }
        top += lineHeight
      }
    }
  }

  private fun paint(style: TextStyle, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = size
    typeface = if (style.bold) Typeface.create(this@AndroidRasterizer.typeface, Typeface.BOLD) else this@AndroidRasterizer.typeface
    isUnderlineText = style.underline
  }

  /**
   * Shrinks [paint] so the widest word fits [width], down to [MIN_SCALE]; past that, words break.
   * Keeps numbers like 99999.00 whole and inside their column.
   */
  private fun fitted(text: String, paint: TextPaint, width: Int): TextPaint {
    val widest = text.split(Regex("\\s+")).maxOfOrNull { paint.measureText(it) } ?: return paint
    if (widest <= width) return paint
    // A little under the exact ratio: StaticLayout's rounding wraps a word that fits exactly.
    paint.textSize *= maxOf(MIN_SCALE, width / widest * 0.96f)
    return paint
  }

  private fun monospace(segment: Segment, cellWidth: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    typeface = Typeface.MONOSPACE
    isFakeBoldText = segment.style.bold
    isUnderlineText = segment.style.underline
    textSize = 100f
    textSize = 100f * cellWidth / measureText("M")
  }

  private fun layout(text: String, paint: TextPaint, width: Int, align: Align): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
      .setAlignment(
        when (align) {
          Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
          Align.CENTER -> Layout.Alignment.ALIGN_CENTER
          Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
      )
      .setIncludePad(false)
      .build()

  private fun draw(width: Int, height: Int, block: (Canvas) -> Unit): MonoImage {
    val h = height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.WHITE)
      block(canvas)
      val pixels = IntArray(width * h)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, h)
      return MonoImage.fromArgb(width, h, pixels)
    } finally {
      bitmap.recycle()
    }
  }

  private companion object {
    const val MIN_SCALE = 0.6f

    /** How much a single line may shrink to avoid wrapping. */
    const val NEAR_FIT = 0.85f
  }
}
