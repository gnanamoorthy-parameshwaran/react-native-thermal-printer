/**
 * Transport Layer Interface. Defines the contract for sending byte data to printers.
 *
 * DESIGN PRINCIPLE:
 * - Engine produces bytes, transport delivers them
 * - This interface is the boundary; implementations are outside this scope
 * - Supports future extensibility: Network, USB, Bluetooth, Serial
 *
 * IMPLEMENTATION STATUS (v1):
 * - Interface defined only
 * - No concrete implementations (deferred to later versions)
 * - Each concrete transport is a separate module
 */
package com.thermalprinter.engine.transport

/**
 * Abstract transport for sending data to a printer. Implementations handle connection management
 * and data transmission.
 *
 * EXTENSION POINT: Future implementations could include:
 * - NetworkTransport (TCP/IP, UDP)
 * - BluetoothTransport (BLE, Classic)
 * - UsbTransport (USB bulk transfer)
 * - SerialTransport (UART)
 */
interface PrinterTransport {

    /**
     * Establish connection to the printer.
     * @throws IOException if connection fails
     */
    @Throws(Exception::class) fun connect()

    /**
     * Send raw bytes to the printer.
     * @param bytes The ESC/POS encoded byte data
     * @throws IOException if write fails
     */
    @Throws(Exception::class) fun write(bytes: ByteArray)

    /** Close the connection gracefully. */
    fun close()

    /** Check if currently connected. */
    fun isConnected(): Boolean
}

/**
 * Composite transport for managing multiple transports. Useful for scenarios where a printer might
 * support multiple connection types.
 *
 * USAGE: val transport = CompositeTransport( listOf(networkTransport, bluetoothTransport) )
 * transport.connectToFirst() // Try each until one succeeds
 *
 * FUTURE FEATURE: Allows fallback connection strategies without changing caller code.
 */
class CompositeTransport(private val transports: List<PrinterTransport>) : PrinterTransport {

    private var activeTransport: PrinterTransport? = null

    override fun connect() {
        for (transport in transports) {
            try {
                transport.connect()
                activeTransport = transport
                return
            } catch (e: Exception) {
                // Try next transport
            }
        }
        throw Exception("All transports failed to connect")
    }

    override fun write(bytes: ByteArray) {
        val transport = activeTransport ?: throw IllegalStateException("Not connected")
        transport.write(bytes)
    }

    override fun close() {
        activeTransport?.close()
        activeTransport = null
    }

    override fun isConnected(): Boolean {
        return activeTransport?.isConnected() ?: false
    }
}
