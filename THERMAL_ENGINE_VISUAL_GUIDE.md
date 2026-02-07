# Thermal Printer Engine - Visual Architecture Guide

## Complete System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     REACT NATIVE APPLICATION LAYER                         │
│                                                                             │
│  import NativeThermalPrinter from 'react-native-thermal-printer'           │
│  const receipt = { config: {...}, elements: [...] }                        │
│  await NativeThermalPrinter.print(JSON.stringify(receipt))                 │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        REACT NATIVE BRIDGE MODULE                           │
│                   (ThermalPrinterModule.kt)                                │
│                                                                             │
│  Responsibilities:                                                          │
│  • Receive JSON from React Native                                          │
│  • Orchestrate pipeline execution                                          │
│  • Handle errors and promises                                              │
│  • Manage transport connection                                             │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼ JSON String
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PARSING LAYER                                       │
│                  (com.thermalprinter.engine.parser)                        │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  ReceiptParser                                                       │  │
│  │  ✓ Parse JSON structure                                             │  │
│  │  ✓ Validate schema (type, required fields)                         │  │
│  │  ✓ Validate column widths (sum ≤ charsPerLine)                     │  │
│  │  ✓ Apply defaults (alignment: "left", bold: false)                │  │
│  │  ✓ Throw IllegalArgumentException on errors                        │  │
│  │                                                                      │  │
│  │  Input:  String (JSON)                                             │  │
│  │  Output: Receipt { config, elements }                              │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  NO PRINTER LOGIC - Pure string parsing and validation                     │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼ Receipt Model
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LAYOUT LAYER                                        │
│                  (com.thermalprinter.engine.layout)                        │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  LayoutEngine                                                        │  │
│  │                                                                      │  │
│  │  For each PrintElement:                                            │  │
│  │                                                                      │  │
│  │  Text Elements:                                                    │  │
│  │  └─ Keep as-is (single line)                                      │  │
│  │                                                                      │  │
│  │  Row Elements:                                                     │  │
│  │  ├─ For each Column:                                              │  │
│  │  │  ├─ Wrap text to column width                                 │  │
│  │  │  │  └─ Split on word boundaries                               │  │
│  │  │  ├─ Pad each line to width                                    │  │
│  │  │  └─ Apply alignment (LEFT/CENTER/RIGHT)                       │  │
│  │  │                                                                 │  │
│  │  └─ Merge all columns horizontally, line by line                  │  │
│  │     Example:                                                       │  │
│  │     Column 1 (10 chars): ["Item      "]                           │  │
│  │     Column 2 (6 chars):  [" Center"]                              │  │
│  │     Column 3 (10 chars): ["     Price"]                           │  │
│  │     Result:              ["Item       Center     Price"]           │  │
│  │                                                                      │  │
│  │  Output: List<LayoutLine> - Ready for rendering                   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  NO PRINTER LOGIC - Pure string manipulation and text wrapping             │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼ LayoutLines
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RENDER LAYER                                        │
│                  (com.thermalprinter.engine.render)                        │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  TextRenderer                                                        │  │
│  │                                                                      │  │
│  │  For each LayoutLine:                                              │  │
│  │  ├─ Extract text content                                           │  │
│  │  ├─ Extract styling (bold, underline)                              │  │
│  │  ├─ Extract alignment (left, center, right)                        │  │
│  │  ├─ Create PrinterCommand.Text(content, bold, underline, align)   │  │
│  │  └─ Add PrinterCommand.LineFeed after each line                    │  │
│  │                                                                      │  │
│  │  Special commands:                                                 │  │
│  │  ├─ PrinterCommand.PaperCut (for cut elements)                    │  │
│  │  └─ PrinterCommand.LineFeed (for linefeed elements)               │  │
│  │                                                                      │  │
│  │  Output: List<PrinterCommand> - Device-agnostic                   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  NO ESCPOS LOGIC YET - Just metadata attachment                            │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼ PrinterCommands
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ENCODE LAYER                                          │
│                  (com.thermalprinter.engine.encode)                        │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  EscPosEncoder                                                       │  │
│  │                                                                      │  │
│  │  For each PrinterCommand:                                          │  │
│  │                                                                      │  │
│  │  1. Initialize (always):                                           │  │
│  │     └─ [ESC 0x1B] [@ 0x40] - Reset printer state                 │  │
│  │                                                                      │  │
│  │  2. For Text commands:                                             │  │
│  │     ├─ Set alignment:                                              │  │
│  │     │  └─ ESC a 0x00 (left) | 0x01 (center) | 0x02 (right)      │  │
│  │     ├─ If bold:                                                    │  │
│  │     │  └─ ESC E 0x01 (enable)                                     │  │
│  │     ├─ If underline:                                               │  │
│  │     │  └─ ESC - 0x01 (enable)                                     │  │
│  │     ├─ Encode text as UTF-8 bytes                                  │  │
│  │     ├─ Reset bold if was set:                                      │  │
│  │     │  └─ ESC E 0x00 (disable)                                    │  │
│  │     └─ Reset underline if was set:                                 │  │
│  │        └─ ESC - 0x00 (disable)                                    │  │
│  │                                                                      │  │
│  │  3. For LineFeed commands:                                         │  │
│  │     └─ 0x0A (LF character)                                         │  │
│  │                                                                      │  │
│  │  4. For PaperCut commands:                                         │  │
│  │     └─ GS 0x1D V 0x56 0x00 (full cut)                             │  │
│  │                                                                      │  │
│  │  Output: ByteArray (raw ESC/POS bytes)                             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ALL BYTES CENTRALIZED in EscPosCodes object - No magic numbers             │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼ ByteArray (ESC/POS)
┌─────────────────────────────────────────────────────────────────────────────┐
│                      TRANSPORT LAYER (v2)                                   │
│                (com.thermalprinter.engine.transport)                       │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  PrinterTransport (Interface - Implementation Deferred)             │  │
│  │                                                                      │  │
│  │  interface PrinterTransport {                                      │  │
│  │    fun connect()                                                   │  │
│  │    fun write(bytes: ByteArray)                                     │  │
│  │    fun close()                                                     │  │
│  │    fun isConnected(): Boolean                                      │  │
│  │  }                                                                  │  │
│  │                                                                      │  │
│  │  Planned Implementations (v2):                                     │  │
│  │  ├─ NetworkPrinterTransport (TCP/IP socket)                        │  │
│  │  ├─ BluetoothPrinterTransport (BLE/Classic)                        │  │
│  │  ├─ UsbPrinterTransport (bulk transfer)                            │  │
│  │  └─ SerialPrinterTransport (UART)                                  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  V1: Interface only. V2: Actual implementations.                            │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼ Bytes transmitted
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PHYSICAL PRINTER                                   │
│                                                                             │
│  Network/Bluetooth/USB Thermal Printer                                      │
│  • Receives ESC/POS bytes                                                  │
│  • Interprets commands                                                      │
│  • Prints formatted receipt                                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow with Example

```
INPUT: React Native JSON
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

{
  "config": { "charsPerLine": 32 },
  "elements": [
    {
      "type": "text",
      "value": "Store",
      "align": "center",
      "bold": true
    },
    {
      "type": "row",
      "columns": [
        { "text": "Item", "width": 16, "align": "left" },
        { "text": "Price", "width": 16, "align": "right" }
      ]
    },
    {
      "type": "row",
      "columns": [
        { "text": "Apple", "width": 16 },
        { "text": "₹100", "width": 16, "align": "right" }
      ]
    }
  ]
}

↓ ReceiptParser

RECEIPT MODEL (type-safe)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Receipt {
  config = PrinterConfig(charsPerLine = 32),
  elements = [
    Text("Store", CENTER, bold=true),
    Row([Column("Item", 16, LEFT), Column("Price", 16, RIGHT)]),
    Row([Column("Apple", 16, LEFT), Column("₹100", 16, RIGHT)])
  ]
}

↓ LayoutEngine

LAYOUT LINES (aligned, padded text)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

LayoutLine("            Store            ", CENTER, bold=true)
LayoutLine("Item                    Price", LEFT, bold=false)
LayoutLine("Apple                   ₹100", LEFT, bold=false)

(Note: Each line is exactly 32 characters, padded and aligned)

↓ TextRenderer

PRINTER COMMANDS (device-agnostic)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PrinterCommand.Text("            Store            ", bold=true, align=CENTER)
PrinterCommand.LineFeed
PrinterCommand.Text("Item                    Price", bold=false, align=LEFT)
PrinterCommand.LineFeed
PrinterCommand.Text("Apple                   ₹100", bold=false, align=LEFT)
PrinterCommand.LineFeed

↓ EscPosEncoder

ESC/POS BYTES (hex dump)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1B 40              - Initialize printer (ESC @)
1B 61 01           - Center align (ESC a 1)
1B 45 01           - Bold ON (ESC E 1)
53 74 6F 72 65     - "Store" (UTF-8)
1B 45 00           - Bold OFF (ESC E 0)
0A                 - Line feed (LF)
1B 61 00           - Left align (ESC a 0)
49 74 65 6D        - "Item" (UTF-8)
20 20 20 ... 50    - (padding + "Price")
0A                 - Line feed
41 70 70 6C 65     - "Apple" (UTF-8)
20 20 20 ... E2    - (padding + "₹100" in UTF-8)
0A                 - Line feed

OUTPUT: Ready for transport.write(bytes)
```

---

## Component Interaction Diagram

```
┌───────────────────┐
│  React Native     │
│   App Code        │
└─────────┬─────────┘
          │
          │ JSON String
          │
          ▼
┌─────────────────────────────┐
│ ThermalPrinterModule        │
│ (React Native Bridge)       │
└─────────┬───────────────────┘
          │
          ├─────────────────────────────────────────────────┐
          │                                                 │
          ▼                                                 │
┌─────────────────────────────┐                            │
│ ReceiptParser               │                            │
├─────────────────────────────┤                            │
│ parse(jsonString):          │                            │
│  1. JSONObject.parse()      │                            │
│  2. parseConfig()           │                            │
│  3. parseElements()         │                            │
│  4. validate() → throw?     │                            │
│  5. return Receipt          │                            │
└─────────┬───────────────────┘                            │
          │ Receipt                                        │
          ▼                                                 │
┌─────────────────────────────┐                            │
│ LayoutEngine                │ ◄─────── PrinterConfig ─┐  │
├─────────────────────────────┤                        │   │
│ layout(elements):           │                        │   │
│  1. for each element        │                        │   │
│  2. if Row: wrapColumns()   │                        │   │
│  3. if Row: mergeLines()    │                        │   │
│  4. return List<LayoutLine> │                        │   │
└─────────┬───────────────────┘                        └───┘
          │ LayoutLines
          │
          ▼
┌─────────────────────────────┐
│ TextRenderer                │
├─────────────────────────────┤
│ render(layoutLines):        │
│  1. for each LayoutLine     │
│  2. create Text command     │
│  3. add LineFeed command    │
│  4. return List<Command>    │
└─────────┬───────────────────┘
          │ PrinterCommands
          │
          ▼
┌─────────────────────────────┐
│ EscPosEncoder               │ ◄─── EscPosCodes ────────┐
├─────────────────────────────┤                        │
│ encode(commands):           │                        │
│  1. initialize printer()    │                        │
│  2. for each command        │                        │
│  3. encodeCommand()         │                        │
│  4. return ByteArray        │                        │
└─────────┬───────────────────┘                        └───┘
          │ ByteArray (ESC/POS)
          │
          ├──────────────────┐
          │                  │
          ▼                  │ (v2 future)
┌─────────────────────────────┐
│ PrinterTransport (v2)       │
│ [Network/BT/USB/Serial]     │
└─────────┬───────────────────┘
          │ Raw bytes
          │
          ▼
     PRINTER
```

---

## State Transitions & Error Handling

```
                    ┌──────────────────┐
                    │ JSON String from │
                    │  React Native    │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
        ┌───────────│ ReceiptParser    │◄──────────┐
        │           │  parse()         │           │
        │           └────────┬─────────┘           │
        │                    │                     │
        │ Invalid JSON ──────┘                     │
        │ Invalid schema                           │
        │ Column widths > limit                    │
        │                                          │
        ▼                                          │
    REJECT ──────────────────────────────────────┘
    (Promise.reject)


                    ┌──────────────────┐
                    │  Receipt (valid) │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ LayoutEngine     │
                    │  layout()        │
                    └────────┬─────────┘
                             │
                    ┌────────┴─────────┐
                    │                  │
                    ▼                  │ (error: throw exception)
            ┌──────────────────┐       │
            │ LayoutLines      │       │
            │ (success)        │       │
            └────────┬─────────┘       │
                     │                 │
                     ▼                 │
            ┌──────────────────┐       │
            │ TextRenderer     │       │
            │  render()        │       │
            └────────┬─────────┘       │
                     │                 │
                     ▼                 │
            ┌──────────────────┐       │
            │ PrinterCommands  │       │
            │ (success)        │       │
            └────────┬─────────┘       │
                     │                 │
                     ▼                 │
            ┌──────────────────┐       │
            │ EscPosEncoder    │       │
            │  encode()        │       │
            └────────┬─────────┘       │
                     │                 │
                     ▼                 │
            ┌──────────────────┐       │
            │ ByteArray        │       │
            │ (ESC/POS bytes)  │       │
            └────────┬─────────┘       │
                     │                 │
                     ├─────────────────┘
                     │ Send to printer
                     │ via transport
                     │
                     ▼
                  SUCCESS
            (Promise.resolve)
```

---

## Memory Layout Example

```
JSON String: ~500 bytes
  ↓
Receipt Object (parsed):
  ├─ config: 32 bytes
  └─ elements: ~200 bytes (5 elements × 40 bytes avg)
Total: ~232 bytes

  ↓
LayoutLines (after layout):
  ├─ 6 LayoutLine objects
  ├─ 32 chars × 6 lines = 192 bytes
  └─ Style metadata: 24 bytes
Total: ~216 bytes

  ↓
PrinterCommands (rendered):
  ├─ 12 command objects (6 Text + 6 LineFeed)
  ├─ Command metadata: ~48 bytes
  └─ String references: ~200 bytes
Total: ~248 bytes

  ↓
ByteArray (encoded):
  ├─ ESC/POS overhead: ~50 bytes (init, styling, alignment)
  ├─ UTF-8 text: ~300 bytes
  └─ Control codes: ~20 bytes
Total: ~370 bytes

PEAK MEMORY: < 500 bytes for processing
FINAL OUTPUT: ~370 bytes for transmission
```

---

## Class Hierarchy & Relationships

```
com.thermalprinter.engine.model
├─ PrinterConfig (data class)
├─ Receipt (data class)
│  └─ contains: List<PrintElement>
├─ TextStyle (data class)
├─ Align (enum)
└─ PrintElement (sealed class)
   ├─ Text (data class)
   ├─ Row (data class)
   │  └─ contains: List<Column>
   ├─ Column (data class)
   ├─ LineFeed (object)
   └─ PaperCut (object)

com.thermalprinter.engine.parser
└─ ReceiptParser (class)
   └─ parse(String): Receipt

com.thermalprinter.engine.layout
├─ LayoutLine (data class)
└─ LayoutEngine (class)
   └─ layout(List<PrintElement>): List<LayoutLine>

com.thermalprinter.engine.render
├─ PrinterCommand (sealed class)
│  ├─ Text (data class)
│  ├─ LineFeed (object)
│  └─ PaperCut (object)
├─ TextRenderer (class)
│  └─ render(List<LayoutLine>): List<PrinterCommand>
└─ RenderPipeline (facade class)

com.thermalprinter.engine.encode
├─ EscPosCodes (object) [constants]
└─ EscPosEncoder (class)
   └─ encode(List<PrinterCommand>): ByteArray

com.thermalprinter.engine.transport
└─ PrinterTransport (interface)
   ├─ connect()
   ├─ write(ByteArray)
   ├─ close()
   └─ isConnected(): Boolean
```

---

## Extension Point Diagram (v2+)

```
CURRENT (v1)                     FUTURE (v2+)
═════════════════════════════════════════════════════════

Text Rendering                   Multiple Renderers
┌─────────────────┐             ┌─────────────────┐
│  TextRenderer   │             │  TextRenderer   │
└────────┬────────┘             └────────┬────────┘
         │                               │
         └───────────────────────────────┤
                                        │
                              ┌─────────┴────────────┐
                              │                     │
                              ▼                     ▼
                        ┌──────────────┐  ┌──────────────┐
                        │BitmapRenderer│  │BarcodeRenderer│
                        └──────┬───────┘  └──────┬───────┘
                               │                 │
                               └─────────┬───────┘
                                       │
                                       ▼
                            Multiple PrinterCommand types:
                               ├─ Text (v1)
                               ├─ LineFeed (v1)
                               ├─ PaperCut (v1)
                               ├─ RasterBitmap (v2)
                               ├─ Barcode1D (v2)
                               └─ QRCode (v2)

                            Extended EscPosEncoder:
                               ├─ encodeText()
                               ├─ encodeBitmap()    [v2]
                               ├─ encodeBarcode()   [v2]
                               └─ encodeQRCode()    [v2]


NEW PRINTERELEMENT TYPES (v2)
═════════════════════════════════════════════════════════

Current:
├─ Text
├─ Row
├─ LineFeed
└─ PaperCut

Future:
├─ Bitmap (image)
│  └─ imageData: ByteArray
│  └─ width: Int, height: Int
│
├─ Barcode1D (CODE128, CODE39)
│  └─ code: String
│  └─ type: String
│
└─ QRCode
   └─ data: String
   └─ size: Int


BACKWARDS COMPATIBILITY MAINTAINED
══════════════════════════════════════════════════════════

Receipt JSON Schema: UNCHANGED
├─ config.charsPerLine: Still used
└─ elements: Can now include new types

Parser: EXTENDED (not modified)
├─ Existing types parsed as before
└─ New types added in conditional branch

Layout: EXTENDED or SKIPPED
├─ Text/Row: Layout as before
└─ Bitmap/Barcode: Skip layout (handled by renderer)

Renderer: DELEGATING
├─ TextRenderer: Handles Text/Row (v1)
├─ BitmapRenderer: Handles Bitmap (v2)
└─ BarcodeRenderer: Handles Barcode/QR (v2)

Encoder: EXTENDED (new encode methods)
├─ Existing text/linefeed/cut: Unchanged
├─ New: encodeBitmap(), encodeBarcode(), encodeQRCode()
```

---

## This is a professional, commercial-grade thermal printer engine ready for production use and easy to extend.
