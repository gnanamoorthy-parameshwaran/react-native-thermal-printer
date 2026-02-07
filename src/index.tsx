import ThermalPrinter, {
  type PrinterConfig,
  type ReceiptData,
  type Spec,
} from './NativeThermalPrinter';

/**
 * Thermal Printer API
 *
 * High-level API for thermal printing in React Native
 * Wraps the native Kotlin thermal printer engine
 */
export class ThermalPrinterAPI {
  /**
   * Connect to a thermal printer
   * @param config Printer connection configuration
   * @example
   * await ThermalPrinterAPI.connect({
   *   ip: '192.168.1.100',
   *   port: 9100,
   *   timeout: 3000
   * });
   */
  static async connect(config: PrinterConfig): Promise<string> {
    return ThermalPrinter.connect(config);
  }

  /**
   * Disconnect from the printer
   * @example
   * await ThermalPrinterAPI.disconnect();
   */
  static async disconnect(): Promise<string> {
    return ThermalPrinter.disconnect();
  }

  /**
   * Check if printer is connected
   * @example
   * const connected = await ThermalPrinterAPI.isConnected();
   */
  static async isConnected(): Promise<boolean> {
    return ThermalPrinter.isConnected();
  }

  /**
   * Print a receipt
   * @param receipt Receipt data with config and elements
   * @returns Number of bytes sent to printer
   * @example
   * const receipt = {
   *   config: { charsPerLine: 32 },
   *   elements: [
   *     { type: 'text', value: 'Receipt', align: 'center', bold: true },
   *     { type: 'row', columns: [
   *       { text: 'Item', width: 16 },
   *       { text: 'Price', width: 16, align: 'right' }
   *     ]},
   *     { type: 'cut' }
   *   ]
   * };
   * const bytes = await ThermalPrinterAPI.print(receipt);
   */
  static async print(receipt: ReceiptData): Promise<number> {
    return ThermalPrinter.print(JSON.stringify(receipt));
  }

  /**
   * Get printer configuration
   * @example
   * const config = await ThermalPrinterAPI.getPrinterConfig();
   */
  static async getPrinterConfig(): Promise<any> {
    return ThermalPrinter.getPrinterConfig();
  }

  /**
   * Debug: Get raw ESC/POS bytes without printing
   * Useful for debugging and testing
   * @param receipt Receipt data
   * @returns Hex-encoded ESC/POS bytes
   * @example
   * const hexBytes = await ThermalPrinterAPI.getEscPosBytes(receipt);
   * console.log('ESC/POS bytes:', hexBytes);
   */
  static async getEscPosBytes(receipt: ReceiptData): Promise<string> {
    return ThermalPrinter.getEscPosBytes(JSON.stringify(receipt));
  }
}

// Export types for TypeScript users
export type { PrinterConfig, ReceiptData, Spec };

// Export native module for advanced users
export { ThermalPrinter };

// Default export
export default ThermalPrinterAPI;
