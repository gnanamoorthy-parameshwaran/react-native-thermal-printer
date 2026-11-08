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
        val layout = layout(element.value, paint(element.style, fontSize * element.size), profile.dotsPerLine, element.align)
        draw(profile.dotsPerLine, layout.height) { layout.draw(it) }
      }
      is PrintElement.Row -> {
        var left = 0
        val cells = element.columns.map { column ->
          val width = column.width * profile.dotsPerChar
          val cell = left to layout(column.text, paint(column.style, fontSize), width, column.align)
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
}
