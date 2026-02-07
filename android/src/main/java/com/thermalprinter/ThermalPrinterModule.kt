package com.thermalprinter

import com.facebook.react.bridge.*
import com.thermalprinter.engine.encode.EscPosEncoder
import com.thermalprinter.engine.layout.LayoutEngine
import com.thermalprinter.engine.parser.ReceiptParser
import com.thermalprinter.engine.render.TextRenderer
import java.net.InetSocketAddress
import java.net.Socket

/**
 * React Native bridge for the thermal printer engine.
 *
 * Exposes the Kotlin thermal printer engine (parser, layout, render, encode) to React Native with
 * full ESC/POS support.
 *
 * USAGE FROM REACT NATIVE:
 * ```typescript
 * import ThermalPrinterAPI from 'react-native-thermal-printer';
 *
 * // Connect
 * await ThermalPrinterAPI.connect({ ip: '192.168.1.100', port: 9100 });
 *
 * // Print receipt
 * const receipt = { config: {...}, elements: [...] };
 * const bytes = await ThermalPrinterAPI.print(receipt);
 *
 * // Disconnect
 * await ThermalPrinterAPI.disconnect();
 * ```
 */
class ThermalPrinterModule(reactContext: ReactApplicationContext) :
        NativeThermalPrinterSpec(reactContext) {

  // Network socket for printer communication (v1)
  private var socket: Socket? = null

  override fun getName(): String = NAME

  /**
   * Connect to a network thermal printer.
   * @param config ReadableMap with { ip, port, timeout }
   * @param promise Resolves with success message or rejects with error
   */
  @ReactMethod
  override fun connect(config: ReadableMap, promise: Promise) {
    try {
      val ip = config.getString("ip") ?: throw IllegalArgumentException("Missing 'ip'")
      val port = config.getInt("port")
      val timeout = config.getInt("timeout")

      // Create socket with timeout
      socket = Socket()
      socket?.connect(InetSocketAddress(ip, port), timeout)

      if (socket?.isConnected == true) {
        promise.resolve("Connected to printer at $ip:$port")
      } else {
        throw Exception("Failed to connect to printer")
      }
    } catch (e: Exception) {
      promise.reject("CONNECT_ERROR", e.message, e)
    }
  }

  /**
   * Disconnect from the printer.
   * @param promise Resolves with success message or rejects with error
   */
  @ReactMethod
  override fun disconnect(promise: Promise) {
    try {
      socket?.close()
      socket = null
      promise.resolve("Disconnected from printer")
    } catch (e: Exception) {
      promise.reject("DISCONNECT_ERROR", e.message, e)
    }
  }

  /**
   * Print a receipt from JSON payload.
   *
   * The JSON payload must have this structure: { "config": { "charsPerLine": 32 }, "elements": [
   * ```
   *     { "type": "text", "value": "...", "align": "...", "bold": true },
   *     { "type": "row", "columns": [...] },
   *     { "type": "cut" }
   * ```
   * ] }
   *
   * @param jsonPayload Receipt structure as JSON string
   * @param promise Resolves with byte count or rejects with error
   */
  @ReactMethod
  override fun print(jsonPayload: String, promise: Promise) {
    try {
      // Validate connection
      if (socket == null || !socket!!.isConnected) {
        throw IllegalStateException("Printer not connected. Call connect() first.")
      }

      // Pipeline: JSON → Receipt → Layout → Commands → Bytes
      val parser = ReceiptParser()
      val receipt = parser.parse(jsonPayload)

      val layoutEngine = LayoutEngine(receipt.config)
      val layoutLines = layoutEngine.layout(receipt.elements)

      val renderer = TextRenderer()
      val commands = renderer.render(layoutLines)

      val encoder = EscPosEncoder()
      val escPosBytes = encoder.encode(commands)

      // Send bytes to printer
      val outputStream = socket!!.getOutputStream()
      outputStream.write(escPosBytes)
      outputStream.flush()

      promise.resolve(escPosBytes.size)
    } catch (e: IllegalArgumentException) {
      // Parser validation error
      promise.reject("PARSE_ERROR", "Invalid receipt JSON: ${e.message}", e)
    } catch (e: IllegalStateException) {
      // Connection error
      promise.reject("NOT_CONNECTED", e.message, e)
    } catch (e: Exception) {
      // Other errors
      promise.reject("PRINT_ERROR", e.message, e)
    }
  }

  /**
   * Check if printer is connected.
   * @param promise Resolves with boolean
   */
  @ReactMethod
  override fun isConnected(promise: Promise) {
    try {
      val connected = socket?.isConnected ?: false
      promise.resolve(connected)
    } catch (e: Exception) {
      promise.reject("ERROR", e.message, e)
    }
  }

  /**
   * Get printer configuration and status.
   * @param promise Resolves with config object
   */
  @ReactMethod
  override fun getPrinterConfig(promise: Promise) {
    try {
      val config = Arguments.createMap()
      config.putString("name", "Thermal Printer")
      config.putInt("charsPerLine", 32)
      config.putInt("printerWidthMm", 80)
      config.putBoolean("connected", socket?.isConnected ?: false)
      promise.resolve(config)
    } catch (e: Exception) {
      promise.reject("ERROR", e.message, e)
    }
  }

  /**
   * Debug: Get raw ESC/POS bytes without printing. Useful for debugging and testing.
   *
   * @param jsonPayload Receipt structure as JSON string
   * @param promise Resolves with hex-encoded bytes
   */
  @ReactMethod
  override fun getEscPosBytes(jsonPayload: String, promise: Promise) {
    try {
      val parser = ReceiptParser()
      val receipt = parser.parse(jsonPayload)

      val layoutEngine = LayoutEngine(receipt.config)
      val layoutLines = layoutEngine.layout(receipt.elements)

      val renderer = TextRenderer()
      val commands = renderer.render(layoutLines)

      val encoder = EscPosEncoder()
      val bytes = encoder.encode(commands)

      // Return as hex string for debugging
      val hexString = bytes.joinToString(" ") { "%02X".format(it) }
      promise.resolve(hexString)
    } catch (e: Exception) {
      promise.reject("ENCODE_ERROR", e.message, e)
    }
  }

  companion object {
    const val NAME = "NativeThermalPrinter"
  }
}
