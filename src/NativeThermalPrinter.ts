import { TurboModuleRegistry, type TurboModule } from 'react-native';

/**
 * Printer connection configuration for network/Bluetooth/USB printers
 */
export type PrinterConfig = {
  ip?: string;
  port?: number;
  timeout?: number;
  deviceAddress?: string;
};

/**
 * Receipt data structure for thermal printing
 * This matches the JSON schema expected by the Kotlin engine
 */
export type ReceiptData = {
  config: {
    charsPerLine: number;
  };
  elements: Array<any>;
};

/**
 * Native Thermal Printer Module Spec
 * Exposes the Kotlin thermal printer engine to React Native
 *
 * NOTE: Must be named 'Spec' for TurboModule code generation
 */
export interface Spec extends TurboModule {
  /**
   * Connect to a printer
   * @param config Printer connection configuration
   */
  connect(config: PrinterConfig): Promise<string>;

  /**
   * Disconnect from the printer
   */
  disconnect(): Promise<string>;

  /**
   * Print a receipt from JSON payload
   * @param jsonPayload Receipt data as JSON string
   * @returns Number of bytes sent
   */
  print(jsonPayload: string): Promise<number>;

  /**
   * Check if printer is connected
   */
  isConnected(): Promise<boolean>;

  /**
   * Get printer configuration
   */
  getPrinterConfig(): Promise<any>;

  /**
   * Debug: Get raw ESC/POS bytes without printing
   */
  getEscPosBytes(jsonPayload: string): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativeThermalPrinter');
