package com.thermalprinter.engine

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses the JSON sent from JS. Every failure surfaces as IllegalArgumentException so the module
 * can report it as INVALID_RECEIPT.
 */
object JobParser {

  fun job(json: String): PrintJob = parsing {
    val root = JSONObject(json)
    val profile = root.optJSONObject("profile")?.let(::profile) ?: PrinterProfile()
    val elements = root.getJSONArray("elements")
    PrintJob(
      profile = profile,
      mode = enumValue(root.optString("mode", "auto")),
      image = root.optJSONObject("image")?.let(::image) ?: ImageOptions(),
      elements = (0 until elements.length()).map { element(elements.getJSONObject(it), profile) },
    )
  }

  fun printer(json: String): NetworkPrinter = parsing {
    val root = JSONObject(json)
    NetworkPrinter(
      host = root.getString("host").also { require(it.isNotBlank()) { "host must not be empty" } },
      port = root.optInt("port", 9100).also { require(it in 1..65535) { "port must be 1-65535" } },
      timeoutMs = root.optInt("timeoutMs", 3000).also { require(it > 0) { "timeoutMs must be > 0" } },
      retries = root.optInt("retries", 8).also { require(it >= 0) { "retries must be >= 0" } },
      drainTimeoutMs = root.optInt("drainTimeoutMs", 10_000).also { require(it > 0) { "drainTimeoutMs must be > 0" } },
    )
  }

  private fun profile(json: JSONObject): PrinterProfile {
    val cutterDistanceMm = json.optDouble("cutterDistanceMm", 13.0)
    return PrinterProfile(
      dotsPerLine = json.optInt("dotsPerLine", 576),
      charsPerLine = json.optInt("charsPerLine", 48),
      codePage = enumValue(json.optString("codePage", "PC437")),
      bandRows = json.optInt("bandRows", 256),
      cutterDistanceMm = cutterDistanceMm,
      bottomMarginMm = json.optDouble("bottomMarginMm", cutterDistanceMm),
    )
  }

  private fun image(json: JSONObject) = ImageOptions(
    fontSize = json.optDouble("fontSize", 28.0).toFloat().also { require(it > 0) { "fontSize must be > 0" } },
    fontAsset = json.optString("font").ifEmpty { null },
  )

  private fun element(json: JSONObject, profile: PrinterProfile): PrintElement =
    when (val type = json.getString("type")) {
      "text" -> PrintElement.Text(
        value = json.getString("value"),
        align = enumValue(json.optString("align", "left")),
        style = style(json),
        size = json.optInt("size", 1).also { require(it in 1..2) { "text size must be 1 or 2" } },
      )
      "row" -> {
        val array = json.getJSONArray("columns")
        val columns = (0 until array.length()).map { column(array.getJSONObject(it)) }
        require(columns.isNotEmpty()) { "row needs at least one column" }
        val total = columns.sumOf { it.width }
        require(total <= profile.charsPerLine) {
          "row columns are $total chars wide but the paper fits ${profile.charsPerLine}"
        }
        PrintElement.Row(columns)
      }
      "divider" -> PrintElement.Divider(
        json.optString("char", "-").also { require(it.isNotEmpty()) { "divider char must not be empty" } }[0]
      )
      "feed" -> PrintElement.Feed(
        json.optInt("lines", 1).also { require(it in 1..255) { "feed lines must be 1-255" } }
      )
      "cut" -> PrintElement.Cut
      else -> throw IllegalArgumentException("unknown element type '$type'")
    }

  private fun column(json: JSONObject) = Column(
    text = json.getString("text"),
    width = json.getInt("width").also { require(it > 0) { "column width must be > 0" } },
    align = enumValue(json.optString("align", "left")),
    style = style(json),
  )

  private fun style(json: JSONObject) =
    TextStyle(bold = json.optBoolean("bold", false), underline = json.optBoolean("underline", false))

  private inline fun <reified T : Enum<T>> enumValue(name: String): T =
    enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
      ?: throw IllegalArgumentException(
        "'$name' is not one of ${enumValues<T>().joinToString { it.name.lowercase() }}"
      )

  private inline fun <T> parsing(block: () -> T): T =
    try {
      block()
    } catch (e: JSONException) {
      throw IllegalArgumentException(e.message, e)
    }
}
