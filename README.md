# react-native-thermal-printer

A professional-grade React Native thermal printer library with a pure Kotlin engine for ESC/POS receipt printing. Supports network printers with configurable text styling, columnar layouts, dividers, and paper cut commands.

## Features

✅ **Network Printer Support** - Connect to thermal printers via IP and port  
✅ **ESC/POS Compatible** - Full support for ESC/POS command generation  
✅ **Rich Text Formatting** - Bold, underline, left/center/right alignment  
✅ **Columnar Layouts** - Professional receipt tables with automatic column wrapping  
✅ **Divider Lines** - Automatic full-width dividers  
✅ **Line Feeds** - Configurable blank lines with count parameter  
✅ **Paper Cut** - Full and partial paper cut commands  
✅ **Zero Dependencies** - Pure Kotlin engine, no external dependencies  

## Installation

```sh
npm install react-native-thermal-printer
```

## Quick Start

### 1. Import the API

```typescript
import ThermalPrinterAPI, { type ReceiptData } from 'react-native-thermal-printer';
```

### 2. Connect to Printer

```typescript
try {
  const result = await ThermalPrinterAPI.connect({
    ip: '192.168.1.100',
    port: 9100,
    timeout: 5000,
  });
  console.log(result); // "Connected to printer successfully"
} catch (error) {
  console.error('Connection failed:', error.message);
}
```

### 3. Print a Receipt

```typescript
const receipt: ReceiptData = {
  config: {
    charsPerLine: 48, // Characters per line (typically 32-48)
  },
  elements: [
    {
      type: 'text',
      value: 'Welcome to Store',
      align: 'center',
      bold: true,
    },
    {
      type: 'text',
      value: '123 Main Street',
      align: 'center',
    },
    {
      type: 'linefeed',
      count: 1,
    },
    {
      type: 'divider',
      char: '-',
    },
    {
      type: 'row',
      columns: [
        { text: 'Item', width: 20, align: 'left' },
        { text: 'Qty', width: 10, align: 'center' },
        { text: 'Price', width: 18, align: 'right' },
      ],
    },
    {
      type: 'divider',
    },
    {
      type: 'row',
      columns: [
        { text: 'Coffee', width: 20, align: 'left' },
        { text: '2', width: 10, align: 'center' },
        { text: '$5.00', width: 18, align: 'right' },
      ],
    },
    {
      type: 'divider',
    },
    {
      type: 'row',
      columns: [
        { text: 'Total', width: 30, align: 'right', bold: true },
        { text: '$5.00', width: 18, align: 'right', bold: true },
      ],
    },
    {
      type: 'linefeed',
      count: 2,
    },
    {
      type: 'text',
      value: 'Thank you for your purchase!',
      align: 'center',
    },
    {
      type: 'linefeed',
      count: 2,
    },
    {
      type: 'cut',
    },
  ],
};

try {
  const byteCount = await ThermalPrinterAPI.print(receipt);
  console.log(`Printed ${byteCount} bytes`);
} catch (error) {
  console.error('Print failed:', error.message);
}
```

### 4. Disconnect

```typescript
try {
  const result = await ThermalPrinterAPI.disconnect();
  console.log(result);
} catch (error) {
  console.error('Disconnection failed:', error.message);
}
```

## API Reference

### ThermalPrinterAPI

#### `connect(config: PrinterConfig): Promise<string>`

Establishes connection to a network printer.

**Parameters:**
```typescript
type PrinterConfig = {
  ip?: string;              // Printer IP address (e.g., '192.168.1.100')
  port?: number;            // Printer port (default: 9100)
  timeout?: number;         // Connection timeout in ms (default: 5000)
  deviceAddress?: string;   // Bluetooth device address (future)
}
```

**Returns:** Success message string

#### `disconnect(): Promise<string>`

Closes the printer connection.

**Returns:** Success message string

#### `print(receipt: ReceiptData): Promise<number>`

Sends receipt data to the printer.

**Parameters:**
```typescript
type ReceiptData = {
  config: {
    charsPerLine: number;   // Characters per line (e.g., 32, 48)
  };
  elements: PrintElement[];
}
```

**Returns:** Number of bytes sent

#### `isConnected(): Promise<boolean>`

Checks if printer is currently connected.

#### `getPrinterConfig(): Promise<PrinterConfig>`

Retrieves current printer configuration.

#### `getEscPosBytes(receipt: ReceiptData): Promise<string>`

Returns ESC/POS bytes as hex string (for debugging).

## Receipt Elements

### Text Element

```typescript
{
  type: 'text',
  value: 'Item name',
  align?: 'left' | 'center' | 'right', // default: 'left'
  bold?: boolean,                        // default: false
  underline?: boolean,                   // default: false
}
```

### Row (Columnar Layout)

```typescript
{
  type: 'row',
  columns: [
    {
      text: 'Column header',
      width: 15,                          // Character width
      align?: 'left' | 'center' | 'right',
      bold?: boolean,
      underline?: boolean,
    },
    // ... more columns
  ],
}
```

### Linefeed

```typescript
{
  type: 'linefeed',
  count?: 1,  // Number of blank lines (default: 1)
}
```

### Divider

```typescript
{
  type: 'divider',
  char?: '-',  // Character to repeat (default: '-')
}
```

### Cut Command

```typescript
{
  type: 'cut',
}
```

## Example App

See the [example app](example/) for a complete working implementation with connection management and receipt printing UI.

```bash
cd example
npm install
npm run android  # or npm run ios
```

## Best Practices

1. **Always disconnect** after printing to close the socket connection
2. **Use linefeeds** before cut command to prevent cutting through text
3. **Test charsPerLine** - most 80mm printers use 32-40 characters, 58mm use 32
4. **Column widths** should sum to `charsPerLine` for best results
5. **Long text** in columns wraps automatically, adjust width accordingly

## Architecture

The library consists of:

- **React Native Bridge** - TypeScript API (`src/`)
- **Kotlin Engine** - Pure zero-dependency printer engine (`android/src/main/java/com/thermalprinter/`)
  - **ReceiptParser** - Converts JSON to internal models
  - **LayoutEngine** - Handles text wrapping and column alignment
  - **TextRenderer** - Applies styling and transforms to printer commands
  - **EscPosEncoder** - Encodes commands to ESC/POS bytes
  - **PrinterTransport** - Network socket communication

## Troubleshooting

### Connection fails
- Verify printer IP and port
- Ensure printer is on the same network
- Check firewall rules
- Increase timeout if network is slow

### Text is cut off
- Increase `charsPerLine` in receipt config
- Reduce column widths
- Ensure divider character length matches printer width

### Printer not responding
- Check printer power and network connection
- Try printing a test page from printer control panel
- Verify printer supports ESC/POS commands

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

MIT

---

Made with ❤️ for thermal printer enthusiasts
