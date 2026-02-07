/**
 * Real-world Integration Example
 *
 * This shows how the thermal printer engine integrates with a React Native module. This is the
 * bridge between RN and the native Kotlin engine.
 */
package com.thermalprinter.module

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.thermalprinter.engine.encode.EscPosEncoder
import com.thermalprinter.engine.layout.LayoutEngine
import com.thermalprinter.engine.parser.ReceiptParser
import com.thermalprinter.engine.render.TextRenderer
import com.thermalprinter.engine.transport.PrinterTransport

/**
 * React Native module that exposes the thermal printer engine.
 *
 * USAGE FROM RN: import NativeThermalPrinter from 'react-native-thermal-printer';
 *
 * const jsonPayload = { config: { charsPerLine: 32 }, elements: [...] };
 *
 * NativeThermalPrinter.print(JSON.stringify(jsonPayload)) .then(() => console.log('Print
 * successful')) .catch(e => console.error(e));
 */
class ThermalPrinterModule(reactContext: ReactApplicationContext) :
        ReactContextBaseJavaModule(reactContext) {

    // Transport will be injected at runtime (v2+)
    private var transport: PrinterTransport? = null

    override fun getName(): String = "NativeThermalPrinter"

    /**
     * Initialize the printer with connection details. This is called before print().
     *
     * @param config JSON config with connection details: { "ip": "192.168.1.100", "port": 9100,
     * "timeout": 3000 }
     */
    @ReactMethod
    fun connect(config: com.facebook.react.bridge.ReadableMap, promise: Promise) {
        try {
            val ip = config.getString("ip") ?: throw IllegalArgumentException("Missing 'ip'")
            val port = config.getInt("port")
            val timeout = config.getInt("timeout")

            // TODO: Create transport based on type (Network, Bluetooth, USB)
            // transport = NetworkPrinterTransport(ip, port, timeout)
            // transport?.connect()

            promise.resolve("Connected")
        } catch (e: Exception) {
            promise.reject("CONNECT_ERROR", e.message, e)
        }
    }

    /** Disconnect from the printer. */
    @ReactMethod
    fun disconnect(promise: Promise) {
        try {
            transport?.close()
            transport = null
            promise.resolve("Disconnected")
        } catch (e: Exception) {
            promise.reject("DISCONNECT_ERROR", e.message, e)
        }
    }

    /**
     * Print a receipt from React Native JSON payload.
     *
     * @param jsonPayload Receipt structure as JSON string
     * @return Promise that resolves with bytes count or rejects with error
     *
     * EXAMPLE FROM RN: const receipt = { config: { charsPerLine: 32 }, elements: [
     * ```
     *     {
     *       type: "text",
     *       value: "Store Receipt",
     *       align: "center",
     *       bold: true
     *     },
     *     {
     *       type: "row",
     *       columns: [
     *         { text: "Item", width: 16 },
     *         { text: "Price", width: 16, align: "right" }
     *       ]
     *     }
     * ```
     * ] };
     *
     * NativeThermalPrinter.print(JSON.stringify(receipt)) .then(bytes => console.log(`Printed
     * ${bytes} bytes`)) .catch(e => console.error(e.message));
     */
    @ReactMethod
    fun print(jsonPayload: String, promise: Promise) {
        try {
            // Validate transport is connected
            if (transport == null || !transport!!.isConnected()) {
                throw IllegalStateException("Printer not connected. Call connect() first.")
            }

            // Step 1: Parse JSON → Receipt Model
            val parser = ReceiptParser()
            val receipt = parser.parse(jsonPayload)

            // Step 2: Layout → LayoutLines
            val layoutEngine = LayoutEngine(receipt.config)
            val layoutLines = layoutEngine.layout(receipt.elements)

            // Step 3: Render → PrinterCommands
            val renderer = TextRenderer()
            val commands = renderer.render(layoutLines)

            // Step 4: Encode → ESC/POS Bytes
            val encoder = EscPosEncoder()
            val escPosBytes = encoder.encode(commands)

            // Step 5: Send to printer
            transport!!.write(escPosBytes)

            // Return success with byte count
            promise.resolve(escPosBytes.size)
        } catch (e: IllegalArgumentException) {
            // Schema validation error
            promise.reject("PARSE_ERROR", "Invalid receipt JSON: ${e.message}", e)
        } catch (e: IllegalStateException) {
            // Not connected
            promise.reject("NOT_CONNECTED", e.message, e)
        } catch (e: Exception) {
            // Other errors (transport, encoding, etc.)
            promise.reject("PRINT_ERROR", e.message, e)
        }
    }

    /**
     * Get printer configuration.
     * @return Promise resolving to config JSON
     */
    @ReactMethod
    fun getPrinterConfig(promise: Promise) {
        try {
            val config =
                    mapOf(
                            "charsPerLine" to 32,
                            "printerWidthMm" to 80,
                            "name" to "Thermal Printer",
                            "connected" to (transport?.isConnected() ?: false)
                    )
            promise.resolve(config)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", e.message, e)
        }
    }

    /**
     * Advanced: Get raw ESC/POS bytes without printing. Useful for debugging and testing.
     *
     * @param jsonPayload Receipt JSON
     * @return Promise resolving to hex-encoded bytes
     */
    @ReactMethod
    fun getEscPosBytes(jsonPayload: String, promise: Promise) {
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

    /**
     * Test printer connectivity.
     * @return Promise resolving to true if connected, false otherwise
     */
    @ReactMethod
    fun isConnected(promise: Promise) {
        try {
            val connected = transport?.isConnected() ?: false
            promise.resolve(connected)
        } catch (e: Exception) {
            promise.reject("TEST_ERROR", e.message, e)
        }
    }
}

/**
 * Example Transport Implementation (Deferred to v2). This is a placeholder showing the interface
 * contract.
 *
 * For v1, the transport interface is defined but not implemented. Implementations (Network,
 * Bluetooth, USB) will be added in v2.
 */
/*
class NetworkPrinterTransport(
    private val ip: String,
    private val port: Int,
    private val timeout: Int
) : PrinterTransport {

    private var socket: java.net.Socket? = null

    override fun connect() {
        socket = java.net.Socket()
        socket!!.connect(
            java.net.InetSocketAddress(ip, port),
            timeout
        )
    }

    override fun write(bytes: ByteArray) {
        socket?.getOutputStream()?.write(bytes)
        socket?.getOutputStream()?.flush()
    }

    override fun close() {
        socket?.close()
        socket = null
    }

    override fun isConnected(): Boolean = socket?.isConnected ?: false
}

class BluetoothPrinterTransport(
    private val deviceAddress: String
) : PrinterTransport {
    // Implementation deferred
}

class UsbPrinterTransport(
    private val deviceId: Int
) : PrinterTransport {
    // Implementation deferred
}
*/

/** Package for the native module. Registers ThermalPrinterModule with React Native. */
class ThermalPrinterPackage : com.facebook.react.TurboReactPackage() {
    override fun getModule(
            name: String,
            reactContext: ReactApplicationContext
    ): com.facebook.react.bridge.NativeModule? {
        return if (name == "NativeThermalPrinter") {
            ThermalPrinterModule(reactContext)
        } else {
            null
        }
    }

    override fun getReactModuleInfoProvider():
            com.facebook.react.module.model.ReactModuleInfoProvider {
        return com.facebook.react.module.model.ReactModuleInfoProvider {
            mapOf(
                    "NativeThermalPrinter" to
                            com.facebook.react.module.model.ReactModuleInfo(
                                    "NativeThermalPrinter",
                                    "com.thermalprinter.module.ThermalPrinterModule",
                                    false, // canOverrideExistingModule
                                    false, // needsEagerInit
                                    false, // hasConstants
                                    true, // isTurboModule
                                    false // isProvider
                            )
            )
        }
    }
}

/**
 * REACT NATIVE USAGE EXAMPLE
 *
 * import { NativeModules } from 'react-native'; const NativeThermalPrinter =
 * NativeModules.NativeThermalPrinter;
 *
 * // Step 1: Connect await NativeThermalPrinter.connect({ ip: '192.168.1.100', port: 9100, timeout:
 * 3000 });
 *
 * // Step 2: Prepare receipt const receipt = { config: { charsPerLine: 32 }, elements: [
 * ```
 *     {
 *       type: "text",
 *       value: "Store Receipt",
 *       align: "center",
 *       bold: true
 *     },
 *     {
 *       type: "row",
 *       columns: [
 *         { text: "Item", width: 16 },
 *         { text: "Price", width: 16, align: "right" }
 *       ]
 *     },
 *     {
 *       type: "row",
 *       columns: [
 *         { text: "Subtotal", width: 22 },
 *         { text: "₹100", width: 10, align: "right" }
 *       ]
 *     },
 *     {
 *       type: "row",
 *       columns: [
 *         { text: "TOTAL", width: 22, bold: true },
 *         { text: "₹100", width: 10, align: "right", bold: true }
 *       ]
 *     },
 *     {
 *       type: "text",
 *       value: "Thank you!",
 *       align: "center"
 *     },
 *     {
 *       type: "cut"
 *     }
 * ```
 * ] };
 *
 * // Step 3: Print try { const bytes = await NativeThermalPrinter.print(JSON.stringify(receipt));
 * console.log(`Printed ${bytes} bytes`); } catch (error) { console.error(`Print failed:
 * ${error.message}`); }
 *
 * // Step 4: Disconnect await NativeThermalPrinter.disconnect();
 *
 * // For debugging: Get raw ESC/POS bytes const hexBytes = await
 * NativeThermalPrinter.getEscPosBytes( JSON.stringify(receipt) ); console.log('ESC/POS bytes:',
 * hexBytes);
 */
