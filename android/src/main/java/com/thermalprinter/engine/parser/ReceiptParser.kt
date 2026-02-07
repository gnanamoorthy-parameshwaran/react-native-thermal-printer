/**
 * JSON Parser and Mapper for thermal printer payloads. Converts React Native JSON payload into
 * internal Receipt model.
 *
 * RESPONSIBILITIES:
 * - Parse JSON structure
 * - Validate column widths and configuration
 * - Apply defaults
 * - No ESC/POS or printer-specific logic
 *
 * CONTRACT WITH REACT NATIVE: The JSON schema is the contract; changes here reflect RN API changes.
 */
package com.thermalprinter.engine.parser

import com.thermalprinter.engine.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses RN JSON payload and converts to Receipt model. Throws IllegalArgumentException on
 * validation failure.
 */
class ReceiptParser {

    fun parse(jsonString: String): Receipt {
        val json = JSONObject(jsonString)

        val config = parseConfig(json.getJSONObject("config"))
        val elements = parseElements(json.getJSONArray("elements"), config)

        return Receipt(config, elements)
    }

    private fun parseConfig(configJson: JSONObject): PrinterConfig {
        val charsPerLine = configJson.optInt("charsPerLine", 32)

        return PrinterConfig(charsPerLine).also {
            // Validation happens in PrinterConfig.init()
        }
    }

    private fun parseElements(elementsArray: JSONArray, config: PrinterConfig): List<PrintElement> {
        val elements = mutableListOf<PrintElement>()

        for (i in 0 until elementsArray.length()) {
            val elementJson = elementsArray.getJSONObject(i)
            val type = elementJson.getString("type")

            val element =
                    when (type) {
                        "text" -> parseTextElement(elementJson)
                        "row" -> parseRowElement(elementJson, config)
                        "linefeed" -> parseLineFeedElement(elementJson)
                        "divider" -> parseDividerElement(elementJson)
                        "cut" -> PrintElement.PaperCut
                        else -> throw IllegalArgumentException("Unknown element type: $type")
                    }

            elements.add(element)
        }

        return elements
    }

    private fun parseTextElement(json: JSONObject): PrintElement.Text {
        val value = json.getString("value")
        val alignStr = json.optString("align", "left")
        val bold = json.optBoolean("bold", false)
        val underline = json.optBoolean("underline", false)

        val align = parseAlign(alignStr)
        val style = TextStyle(bold = bold, underline = underline)

        return PrintElement.Text(value, align, style)
    }

    private fun parseRowElement(json: JSONObject, config: PrinterConfig): PrintElement.Row {
        val columnsArray = json.getJSONArray("columns")
        val columns = mutableListOf<Column>()
        var totalWidth = 0

        for (i in 0 until columnsArray.length()) {
            val colJson = columnsArray.getJSONObject(i)
            val column = parseColumn(colJson)
            columns.add(column)
            totalWidth += column.width
        }

        // Validate: sum of column widths must not exceed charsPerLine
        require(totalWidth <= config.charsPerLine) {
            "Row column widths ($totalWidth) exceed charsPerLine (${config.charsPerLine})"
        }

        return PrintElement.Row(columns)
    }

    private fun parseColumn(json: JSONObject): Column {
        val text = json.getString("text")
        val width = json.getInt("width")
        val alignStr = json.optString("align", "left")
        val bold = json.optBoolean("bold", false)
        val underline = json.optBoolean("underline", false)

        require(width > 0) { "Column width must be > 0" }

        val align = parseAlign(alignStr)
        val style = TextStyle(bold = bold, underline = underline)

        return Column(text, width, align, style)
    }

    private fun parseLineFeedElement(json: JSONObject): PrintElement.LineFeed {
        val count = json.optInt("count", 1)
        require(count > 0) { "LineFeed count must be > 0" }
        return PrintElement.LineFeed(count)
    }

    private fun parseDividerElement(json: JSONObject): PrintElement.Divider {
        val char = json.optString("char", "-")
        require(char.isNotEmpty()) { "Divider char must not be empty" }
        return PrintElement.Divider(char.take(1)) // Take only first character
    }

    private fun parseAlign(alignStr: String): Align {
        return when (alignStr.lowercase()) {
            "left" -> Align.LEFT
            "center" -> Align.CENTER
            "right" -> Align.RIGHT
            else -> throw IllegalArgumentException("Unknown alignment: $alignStr")
        }
    }
}
