package com.thermalprinter.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what it was asked to draw and returns a fixed-height image. */
private class FakeRasterizer(private val height: Int = 10) : Rasterizer {
  val drawn = mutableListOf<PrintElement>()

  override fun rasterize(element: PrintElement, profile: PrinterProfile): MonoImage {
    drawn.add(element)
    return MonoImage.blank(profile.dotsPerLine, height)
  }

  override fun rasterizeTextLines(lines: List<TextLine>, profile: PrinterProfile) =
    MonoImage.blank(profile.dotsPerLine, lines.size * Preview.LINE_DOTS)
}

class JobParserTest {

  @Test
  fun `applies defaults for an 80mm printer`() {
    val job = JobParser.job("""{"elements":[{"type":"text","value":"Hi"}]}""")
    assertEquals(PrinterProfile(576, 48, CodePage.PC437), job.profile)
    assertEquals(RenderMode.AUTO, job.mode)
    assertEquals(PrintElement.Text("Hi"), job.elements.single())
  }

  @Test
  fun `parses every element type`() {
    val job = JobParser.job(
      """
      {"mode":"image","elements":[
        {"type":"text","value":"T","align":"center","bold":true,"size":2},
        {"type":"row","columns":[{"text":"a","width":40},{"text":"b","width":8,"align":"right","underline":true}]},
        {"type":"divider","char":"="},
        {"type":"feed","lines":3},
        {"type":"cut"}
      ]}
      """
    )
    assertEquals(RenderMode.IMAGE, job.mode)
    assertEquals(
      listOf(
        PrintElement.Text("T", Align.CENTER, TextStyle(bold = true), 2),
        PrintElement.Row(listOf(Column("a", 40), Column("b", 8, Align.RIGHT, TextStyle(underline = true)))),
        PrintElement.Divider('='),
        PrintElement.Feed(3),
        PrintElement.Cut,
      ),
      job.elements,
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects rows wider than the paper`() {
    JobParser.job("""{"elements":[{"type":"row","columns":[{"text":"a","width":40},{"text":"b","width":9}]}]}""")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `reports malformed JSON as an invalid argument`() {
    JobParser.job("{ not json")
  }

  @Test
  fun `printer defaults to port 9100`() {
    assertEquals(NetworkPrinter("10.0.0.5", 9100, 3000, 8), JobParser.printer("""{"host":"10.0.0.5"}"""))
  }
}

class TextLayoutTest {

  @Test
  fun `wraps on words and splits words longer than the line`() {
    assertEquals(listOf("Coffee is", "good"), TextLayout.wrap("Coffee is good", 10))
    assertEquals(listOf("abcd", "ef"), TextLayout.wrap("abcdef", 4))
    assertEquals(listOf("a", "", "b"), TextLayout.wrap("a\n\nb", 4))
    assertEquals(listOf(""), TextLayout.wrap("", 4))
  }

  @Test
  fun `pads columns and keeps padding unstyled`() {
    val row = PrintElement.Row(
      listOf(
        Column("Tea", 6),
        Column("2", 3, Align.CENTER),
        Column("10.00", 7, Align.RIGHT, TextStyle(bold = true)),
      )
    )
    val line = TextLayout.row(row).single()
    assertEquals("Tea    2   10.00", line.segments.joinToString("") { it.text })
    assertEquals(16, line.length)
    assertEquals(Segment("10.00", TextStyle(bold = true)), line.segments.last())
    assertEquals(Segment("  "), line.segments[line.segments.size - 2])
  }

  @Test
  fun `bold applies only to its own column`() {
    val row = PrintElement.Row(listOf(Column("Total", 10), Column("8.50", 6, style = TextStyle(bold = true))))
    val segments = TextLayout.row(row).single().segments
    assertFalse(segments.first { it.text == "Total" }.style.bold)
    assertTrue(segments.first { it.text == "8.50" }.style.bold)
  }

  @Test
  fun `wrapped columns produce aligned lines`() {
    val row = PrintElement.Row(listOf(Column("Masala dosa", 6), Column("1", 2, Align.RIGHT)))
    val lines = TextLayout.row(row).map { l -> l.segments.joinToString("") { it.text } }
    assertEquals(listOf("Masala 1", "dosa    "), lines)
  }

  @Test
  fun `double size text wraps at half width`() {
    val lines = TextLayout.text(PrintElement.Text("x".repeat(30), size = 2), 48)
    assertEquals(listOf(24, 6), lines.map { it.length })
  }
}

class ReceiptRendererTest {

  @Test
  fun `bill starts at the first black dot`() {
    val inked = object : Rasterizer {
      override fun rasterize(element: PrintElement, profile: PrinterProfile) =
        MonoImage.blank(profile.dotsPerLine, 30).also { it.data[20 * it.bytesPerRow] = 0x80.toByte() }
      override fun rasterizeTextLines(lines: List<TextLine>, profile: PrinterProfile) =
        MonoImage.blank(profile.dotsPerLine, 1)
    }
    val blocks = ReceiptRenderer(profile, RenderMode.IMAGE, inked)
      .render(listOf(PrintElement.Text("அ"), PrintElement.Text("ஆ")))
    // First image loses its 20 blank rows; the gap above the second line is kept.
    assertEquals(10 + 30, (blocks.single() as Block.Image).image.height)
  }

  private val profile = PrinterProfile()
  private val tamil = PrintElement.Text("தக்காளி 1 கிலோ")
  private val english = PrintElement.Text("Tomato 1 kg")
  private val rupee = PrintElement.Row(listOf(Column("Total", 30), Column("₹40", 18)))

  @Test
  fun `auto mode draws only what the code page can't print and merges adjacent images`() {
    val rasterizer = FakeRasterizer(height = 10)
    val blocks = ReceiptRenderer(profile, RenderMode.AUTO, rasterizer)
      .render(listOf(english, tamil, rupee, PrintElement.Feed(2), PrintElement.Cut))
    assertEquals(4, blocks.size)
    assertTrue(blocks[0] is Block.Text)
    assertEquals(20, (blocks[1] as Block.Image).image.height)
    assertEquals(Block.Feed(2), blocks[2])
    assertEquals(Block.Cut, blocks[3])
    assertEquals(listOf(tamil, rupee), rasterizer.drawn)
  }

  @Test
  fun `text mode replaces unprintable characters one column each`() {
    val blocks = ReceiptRenderer(profile, RenderMode.TEXT, FakeRasterizer()).render(listOf(rupee))
    val line = (blocks.single() as Block.Text).lines.single()
    assertEquals(48, line.length)
    assertTrue(line.segments.any { it.text == "?40" })
  }

  @Test
  fun `image mode sends the bill as one image before the cut`() {
    val rasterizer = FakeRasterizer(height = 10)
    val blocks = ReceiptRenderer(profile, RenderMode.IMAGE, rasterizer)
      .render(listOf(english, PrintElement.Feed(1), PrintElement.Divider(), PrintElement.Cut))
    assertEquals(2, rasterizer.drawn.size)
    assertEquals(2, blocks.size)
    assertEquals(10 + Preview.LINE_DOTS + 10, (blocks[0] as Block.Image).image.height)
    assertEquals(Block.Cut, blocks.last())
  }

  @Test
  fun `preview stacks blocks to their combined height`() {
    val rasterizer = FakeRasterizer(height = 50)
    val blocks = ReceiptRenderer(profile, RenderMode.AUTO, rasterizer).render(listOf(english, tamil, PrintElement.Feed(1)))
    val image = Preview.compose(blocks, profile, rasterizer)
    assertEquals(576, image.width)
    assertEquals(Preview.LINE_DOTS + 50 + Preview.LINE_DOTS, image.height)
  }
}

class EscPosEncoderTest {

  private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

  @Test
  fun `starts with init and code page selection`() {
    val out = EscPosEncoder(PrinterProfile(codePage = CodePage.PC858)).encode(emptyList())
    assertArrayEquals(bytes(0x1B, 0x40, 0x1B, 0x74, 19), out)
  }

  @Test
  fun `encodes a styled line`() {
    val line = TextLine(listOf(Segment("A"), Segment("B", TextStyle(bold = true))), Align.CENTER, size = 2)
    val out = EscPosEncoder(PrinterProfile()).encode(listOf(Block.Text(listOf(line)))).drop(5)
    assertEquals(
      bytes(
        0x1B, 0x61, 1, 0x1D, 0x21, 0x11,
        0x1B, 0x45, 0, 0x1B, 0x2D, 0, 'A'.code,
        0x1B, 0x45, 1, 0x1B, 0x2D, 0, 'B'.code,
        0x1B, 0x45, 0, 0x1B, 0x2D, 0, 0x1D, 0x21, 0, 0x0A,
      ).toList(),
      out,
    )
  }

  @Test
  fun `encodes text in the code page, not UTF-8`() {
    val line = TextLine(listOf(Segment("é")))
    val out = EscPosEncoder(PrinterProfile(codePage = CodePage.PC437)).encode(listOf(Block.Text(listOf(line))))
    assertTrue(out.contains(0x82.toByte())) // é in CP437
    assertFalse(out.contains(0xC3.toByte())) // first byte of é in UTF-8
  }

  @Test
  fun `splits images into bands`() {
    val image = MonoImage.blank(16, 5)
    image.data[0] = 0xFF.toByte()
    val out = EscPosEncoder(PrinterProfile(dotsPerLine = 16, charsPerLine = 1, bandRows = 2))
      .encode(listOf(Block.Image(image))).drop(5 + 3)
    val header = { rows: Int -> listOf(0x1D, 0x76, 0x30, 0, 2, 0, rows, 0).map { it.toByte() } }
    assertEquals(header(2), out.subList(0, 8))
    assertEquals(0xFF.toByte(), out[8])
    assertEquals(header(2), out.subList(12, 20))
    assertEquals(header(1), out.subList(24, 32))
    assertEquals(34, out.size)
  }

  @Test
  fun `cut feeds twice the cutter distance in whole lines`() {
    val out = EscPosEncoder(PrinterProfile(cutterDistanceMm = 13.0)).encode(listOf(Block.Cut)).drop(5)
    // 26mm / 4.23mm per line = 6 lines.
    assertEquals(bytes(0x1B, 0x32, 0x1B, 0x64, 6, 0x1D, 0x56, 0).toList(), out)
  }

  @Test
  fun `cut never goes through printed content`() {
    // 13mm total would round to 3 lines (12.7mm), short of the cutter; 4 lines clears it.
    assertEquals(4, PrinterProfile(cutterDistanceMm = 13.0, bottomMarginMm = 0.0).cutFeedLines)
  }
}

class MonoImageTest {

  @Test
  fun `trims blank rows above the first dot`() {
    val image = MonoImage.blank(8, 5)
    image.data[3] = 0x01
    val trimmed = image.trimTop()
    assertEquals(2, trimmed.height)
    assertTrue(trimmed[7, 0])
    val blank = MonoImage.blank(8, 4)
    assertEquals(4, blank.trimTop().height)
  }

  @Test
  fun `thresholds pixels and treats transparency as paper`() {
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    val clear = 0x00000000
    val pixels = IntArray(16) { white }.apply {
      this[0] = black
      this[7] = 0xFF808080.toInt()
      this[8] = clear
    }
    val image = MonoImage.fromArgb(8, 2, pixels)
    assertTrue(image[0, 0])
    assertTrue(image[7, 0])
    assertFalse(image[1, 0])
    assertFalse(image[0, 1])
    assertEquals(0x81.toByte(), image.data[0])
  }
}
