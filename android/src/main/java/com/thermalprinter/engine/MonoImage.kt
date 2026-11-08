package com.thermalprinter.engine

/** 1-bit image. Rows are packed MSB-first, a set bit is a black dot. */
class MonoImage(val width: Int, val height: Int, val data: ByteArray) {
  init {
    require(width > 0 && width % 8 == 0) { "width must be a positive multiple of 8" }
    require(data.size == width / 8 * height) { "data size doesn't match dimensions" }
  }

  val bytesPerRow: Int get() = width / 8

  operator fun get(x: Int, y: Int): Boolean =
    data[y * bytesPerRow + x / 8].toInt() and (0x80 ushr (x % 8)) != 0

  /** Swaps black and white, for white-on-black bands. */
  fun inverted(): MonoImage =
    MonoImage(width, height, ByteArray(data.size) { (data[it].toInt() xor 0xFF).toByte() })

  /** Drops blank rows above the first black dot. A fully blank image is returned unchanged. */
  fun trimTop(): MonoImage {
    val first = (0 until height).firstOrNull { y ->
      (0 until bytesPerRow).any { data[y * bytesPerRow + it].toInt() != 0 }
    } ?: return this
    if (first == 0) return this
    return MonoImage(width, height - first, data.copyOfRange(first * bytesPerRow, data.size))
  }

  companion object {
    fun blank(width: Int, height: Int) = MonoImage(width, height, ByteArray(width / 8 * height))

    /** Thresholds ARGB pixels; transparent pixels count as white paper. */
    fun fromArgb(width: Int, height: Int, pixels: IntArray, threshold: Int = 160): MonoImage {
      val image = blank(width, height)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val p = pixels[y * width + x]
          if (p ushr 24 < 128) continue
          val luma = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
          if (luma < threshold) {
            val i = y * image.bytesPerRow + x / 8
            image.data[i] = (image.data[i].toInt() or (0x80 ushr (x % 8))).toByte()
          }
        }
      }
      return image
    }

    fun stack(images: List<MonoImage>): MonoImage {
      require(images.isNotEmpty()) { "nothing to stack" }
      val width = images.first().width
      require(images.all { it.width == width }) { "images must share a width" }
      val data = ByteArray(images.sumOf { it.data.size })
      var offset = 0
      for (image in images) {
        image.data.copyInto(data, offset)
        offset += image.data.size
      }
      return MonoImage(width, images.sumOf { it.height }, data)
    }
  }
}
