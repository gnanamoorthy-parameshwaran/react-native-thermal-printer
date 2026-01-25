package com.thermalprinter

import com.facebook.react.bridge.*
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import java.net.Socket
import org.json.JSONArray
import org.json.JSONObject

class ThermalPrinterModule(reactContext: ReactApplicationContext) :
        NativeThermalPrinterSpec(reactContext) {

  private var socket: Socket? = null

  override fun getName(): String = NAME

  override fun connect(printer: ReadableMap, promise: Promise) {
    try {
      socket = Socket(printer.getString("ip"), printer.getInt("port"))
      promise.resolve(socket?.isConnected ?: false)
    } catch (e: Exception) {
      promise.reject("CONNECT_ERROR", e)
    }
  }

  override fun print(data: String, promise: Promise) {
    try {
      val bytes = buildReceipt(data)
      val out = socket?.getOutputStream()

      out?.write(bytes)
      out?.flush()

      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("PRINT_ERROR", e)
    }
  }

  override fun disconnect(promise: Promise) {
    try {
      socket?.close()
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("DISCONNECT_ERROR", e)
    }
  }

  companion object {
    const val NAME = "NativeThermalPrinter"
  }

  private fun buildReceipt(json: String): ByteArray {
    val root = JSONObject(json)

    val config = root.getJSONObject("config")
    val width = config.getInt("maxCharPerLine")

    // Validate width for common printer sizes
    if (width < 20 || width > 80) {
      throw IllegalArgumentException("Invalid width: $width. Expected 20-80 characters per line")
    }

    val builder = EscPosBuilder(width).init()

    renderSection(builder, root.getJSONArray("header"), width)
    renderSection(builder, root.getJSONArray("body"), width)
    renderSection(builder, root.getJSONArray("footer"), width)

    builder.cut()

    return builder.build()
  }

  private fun renderSection(builder: EscPosBuilder, array: JSONArray, width: Int) {
    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)

      when (obj.getString("type")) {
        "text" -> renderText(builder, obj)
        "divider" -> builder.divider()
        "table" -> renderTable(builder, obj, width)
      }
    }
  }

  private fun renderText(builder: EscPosBuilder, obj: JSONObject) {
    val text = obj.getString("text")
    val opt = obj.optJSONObject("options")

    val align = opt?.optString("align", "left") ?: "left"
    val bold = opt?.optBoolean("bold", false) ?: false

    builder.align(align).bold(bold).text(text).newLine().bold(false).align("left")
  }

  // ===============================
  // TABLE RENDERER
  // ===============================
  private fun renderTable(builder: EscPosBuilder, table: JSONObject, lineWidth: Int) {
    val columns = table.getJSONArray("columns")
    val data = table.getJSONArray("data")

    val colWidths = calculateColumnWidths(lineWidth, columns)

    // -------- HEADER --------
    builder.bold(true).text(buildHeaderLine(columns, colWidths)).newLine().bold(false)

    builder.divider()

    // -------- ROWS --------
    for (i in 0 until data.length()) {
      val row = data.getJSONArray(i)
      builder.text(buildRowLine(row, columns, colWidths)).newLine()
    }
  }

  // ===============================
  // CALCULATE COLUMN WIDTHS
  // ===============================
  private fun calculateColumnWidths(totalWidth: Int, columns: JSONArray): List<Int> {
    val widths = mutableListOf<Int>()
    var totalPercent = 0

    for (i in 0 until columns.length()) {
      val percent = columns.getJSONObject(i).getInt("width")
      totalPercent += percent
      widths.add(totalWidth * percent / 100)
    }

    // Validate that columns don't exceed total width
    val totalChars = widths.sum()
    if (totalChars > totalWidth) {
      val adjustment = totalWidth - totalChars
      // Adjust the last column to fit
      if (widths.isNotEmpty()) {
        widths[widths.size - 1] += adjustment
      }
    }

    return widths
  }

  // ===============================
  // BUILD HEADER LINE
  // ===============================
  private fun buildHeaderLine(columns: JSONArray, widths: List<Int>): String {
    val sb = StringBuilder()

    for (i in widths.indices) {
      val column = columns.getJSONObject(i)
      val text = column.getString("text")
      val align = column.optString("align", "left")
      val width = widths[i]

      sb.append(
              when (align) {
                "center" -> text.take(width).padStart((width + text.length) / 2).padEnd(width)
                "right" -> text.take(width).padStart(width)
                else -> text.take(width).padEnd(width) // left (default)
              }
      )
    }

    return sb.toString()
  }

  // ===============================
  // BUILD ROW LINE
  // ===============================
  private fun buildRowLine(row: JSONArray, columns: JSONArray, widths: List<Int>): String {
    val sb = StringBuilder()

    for (i in widths.indices) {
      val text = row.optString(i, "")
      val align = columns.getJSONObject(i).optString("align", "left")
      val width = widths[i]

      // Auto-detect number format for right alignment if not explicitly set
      val isNumber = text.matches(Regex("^[0-9.,$]+$"))
      val effectiveAlign = if (align == "left" && isNumber) "right" else align

      sb.append(
              when (effectiveAlign) {
                "center" -> text.take(width).padStart((width + text.length) / 2).padEnd(width)
                "right" -> text.take(width).padStart(width)
                else -> text.take(width).padEnd(width) // left (default)
              }
      )
    }

    return sb.toString()
  }
}
