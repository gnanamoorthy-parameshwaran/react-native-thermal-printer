package com.thermalprinter.engine

import java.io.ByteArrayOutputStream

/** Turns rendered blocks into ESC/POS bytes. */
class EscPosEncoder(private val profile: PrinterProfile) {

  fun encode(blocks: List<Block>): ByteArray {
    val out = ByteArrayOutputStream()
    out.cmd(ESC, '@'.code)
    out.cmd(ESC, 't'.code, profile.codePage.table)
    for (block in blocks) {
      when (block) {
        is Block.Text -> block.lines.forEach { out.line(it) }
        is Block.Image -> out.image(block.image)
        is Block.Feed -> out.cmd(ESC, 'd'.code, block.lines.coerceIn(0, 255))
        is Block.Space -> out.feedDots(block.dots)
        // Plain feed + GS V 0: the feed-and-cut variant (GS V 66) is ignored by many printers.
        Block.Cut -> {
          out.cmd(ESC, '2'.code) // default 1/6 inch line spacing, which cutFeedLines assumes
          out.cmd(ESC, 'd'.code, profile.cutFeedLines)
          out.cmd(GS, 'V'.code, 0)
        }
      }
    }
    return out.toByteArray()
  }

  private fun ByteArrayOutputStream.line(line: TextLine) {
    cmd(ESC, 'a'.code, line.align.ordinal)
    if (line.invert) cmd(GS, 'B'.code, 1)
    cmd(GS, '!'.code, if (line.size == 2) 0x11 else 0x00)
    for (segment in line.segments) {
      cmd(ESC, 'E'.code, if (segment.style.bold) 1 else 0)
      cmd(ESC, '-'.code, if (segment.style.underline) 1 else 0)
      write(profile.codePage.encode(segment.text))
    }
    cmd(ESC, 'E'.code, 0)
    cmd(ESC, '-'.code, 0)
    cmd(GS, '!'.code, 0)
    if (line.invert) cmd(GS, 'B'.code, 0)
    write(LF)
  }

  /** ESC J, at most 255 per command. Some printers count it in half dots, so this is approximate. */
  private fun ByteArrayOutputStream.feedDots(dots: Int) {
    var rest = dots
    while (rest > 0) {
      val n = minOf(255, rest)
      cmd(ESC, 'J'.code, n)
      rest -= n
    }
  }

  private fun ByteArrayOutputStream.image(image: MonoImage) {
    cmd(ESC, 'a'.code, 0)
    val bytesPerRow = image.bytesPerRow
    var top = 0
    while (top < image.height) {
      val rows = minOf(profile.bandRows, image.height - top)
      cmd(GS, 'v'.code, '0'.code, 0, bytesPerRow and 0xFF, bytesPerRow shr 8, rows and 0xFF, rows shr 8)
      write(image.data, top * bytesPerRow, rows * bytesPerRow)
      top += rows
    }
  }

  private fun ByteArrayOutputStream.cmd(vararg bytes: Int) = bytes.forEach { write(it) }

  private companion object {
    const val ESC = 0x1B
    const val GS = 0x1D
    const val LF = 0x0A
  }
}
