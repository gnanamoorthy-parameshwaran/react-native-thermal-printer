package com.thermalprinter

class EscPosBuilder(private val width: Int) {

  private val buffer = mutableListOf<Byte>()

  fun init() = apply {
    // Initialize printer (ESC @)
    buffer += listOf(0x1B, 0x40).map { it.toByte() }

    // Set left margin to 0 (ESC l)
    buffer += listOf(0x1B, 0x6C, 0x00, 0x00).map { it.toByte() }

    // Set print area width (GS W) - width in dots (pixel mode)
    // For thermal printer width: multiply character width by 12 dots per char
    val dotsWidth = (width * 12).toShort()
    buffer += listOf(0x1D, 0x57).map { it.toByte() }
    buffer += ((dotsWidth.toInt() and 0xFF).toByte())
    buffer += (((dotsWidth.toInt() shr 8) and 0xFF).toByte())
  }

  fun text(text: String) = apply { buffer += text.toByteArray(Charsets.UTF_8).toList() }

  fun newLine() = apply { buffer += 0x0A }

  fun divider() = apply {
    buffer += "-".repeat(width).toByteArray(Charsets.UTF_8).toList()
    newLine()
  }

  fun align(value: String) = apply {
    val n =
            when (value) {
              "center" -> 1
              "right" -> 2
              else -> 0
            }
    buffer += listOf(0x1B, 0x61, n).map { it.toByte() }
  }

  fun bold(on: Boolean) = apply {
    buffer += listOf(0x1B, 0x45, if (on) 1 else 0).map { it.toByte() }
  }

  fun feed(lines: Int = 5) = apply { repeat(lines) { newLine() } }

  fun cut() = apply {
    // Add 8 lines of feed before cutting to ensure full content prints
    feed(8)
    // Cut command (GS V)
    buffer += listOf(0x1D, 0x56, 0x00).map { it.toByte() }
  }

  fun build(): ByteArray = buffer.toByteArray()
}
