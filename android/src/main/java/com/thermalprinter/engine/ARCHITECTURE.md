# Thermal Printer Engine Architecture

## Overview

The thermal printer engine is a **pipeline-based architecture** designed for commercial-grade POS receipt printing. It transforms unstructured React Native JSON payloads into ESC/POS byte sequences ready for network/USB transmission.

### Design Philosophy

- **Single Responsibility**: Each layer handles one concern
- **Testability**: Every component is independently unit-testable
- **Extensibility**: New features (bitmap, barcode, QR) require no refactoring of core logic
- **Type Safety**: Kotlin sealed classes prevent invalid state
- **Zero Dependencies**: Pure Kotlin, no external libraries

---

## Pipeline Architecture

```
React Native JSON
      ↓
 [PARSER] ← JSON → Receipt Model (type-safe)
      ↓
 [LAYOUT] ← Receipt → Layout Lines (aligned text)
      ↓
 [RENDER] ← Layout → Printer Commands (device-agnostic)
      ↓
 [ENCODE] ← Commands → ESC/POS Bytes (printer-specific)
      ↓
 [TRANSPORT] ← Bytes → Network/USB/Bluetooth (deferred)
```

Each layer is **decoupled**: you can swap or test individual stages.

---

## Layer Responsibilities

### 1. Parser (`ReceiptParser`)

**Input**: JSON string from React Native  
**Output**: `Receipt` model with validated config and elements

**Responsibilities**:

- Parse JSON structure
- Validate column widths (sum ≤ `charsPerLine`)
- Apply defaults for missing fields
- Throw `IllegalArgumentException` on schema violations

**No Printer Logic**:

- No ESC/POS knowledge
- No Android APIs
- Pure string parsing

**Example**:

```kotlin
val parser = ReceiptParser()
val receipt = parser.parse(jsonString)
```

---

### 2. Layout Engine (`LayoutEngine`)

**Input**: `List<PrintElement>`  
**Output**: `List<LayoutLine>` (padded, aligned strings)

**Responsibilities**:

- Convert `Row` elements into character-aligned strings
- Wrap text within column widths
- Apply padding and alignment
- Merge multi-line columns horizontally
- Handle Unicode text (Tamil, Hindi, etc.)

**Key Algorithm**:

1. Split each column text by word breaks
2. Fit lines to column width
3. Pad to width (left/center/right align)
4. Merge columns horizontally line-by-line

**No Printer Logic**:

- No ESC/POS commands
- No Android APIs
- Pure string manipulation

**Example**:

```kotlin
val layoutEngine = LayoutEngine(config)
val lines = layoutEngine.layout(elements)
// Result: ["Item            Qty Price", "Apple Juice      2 ₹150"]
```

---

### 3. Render Engine (`TextRenderer` + `RenderPipeline`)

**Input**: `List<LayoutLine>`  
**Output**: `List<PrinterCommand>`

**Responsibilities**:

- Convert layout lines to printer commands
- Attach styling (bold, underline, alignment)
- Generate line feeds and paper cuts

**Design for Extension**:

- `PrinterCommand` is sealed; `BitmapCommand`, `BarcodeCommand` can be added later
- Renderer selection can be dynamic (strategy pattern)

**Example**:

```kotlin
val renderer = TextRenderer()
val commands = renderer.render(layoutLines)
// Result: [Text("Item..."), LineFeed, Text("Apple..."), LineFeed, ...]
```

---

### 4. ESC/POS Encoder (`EscPosEncoder`)

**Input**: `List<PrinterCommand>`  
**Output**: `ByteArray` (raw ESC/POS bytes)

**Responsibilities**:

- Encode commands to ESC/POS byte sequences
- Centralize all magic numbers
- Handle UTF-8 encoding for Unicode text

**Key Design**:

- All byte constants in `EscPosCodes` object
- No magic numbers scattered in code
- Easy to add new commands (barcode, QR) later

**ESC/POS Commands Used (v1)**:

```
ESC @ (0x1B 0x40)     - Initialize printer
ESC E (0x1B 0x45)     - Bold on/off
ESC - (0x1B 0x2D)     - Underline on/off
ESC a (0x1B 0x61)     - Alignment (0=left, 1=center, 2=right)
LF   (0x0A)           - Line feed
GS V (0x1D 0x56)      - Paper cut
```

**Example**:

```kotlin
val encoder = EscPosEncoder()
val bytes = encoder.encode(commands)
// Result: [0x1B, 0x40, 0x1B, 0x61, 0x01, ...] (raw bytes)
```

---

### 5. Transport Interface (`PrinterTransport`)

**Status**: Interface only; implementations deferred

**Contract**:

```kotlin
interface PrinterTransport {
    fun connect()
    fun write(bytes: ByteArray)
    fun close()
    fun isConnected(): Boolean
}
```

**Future Implementations**:

- `NetworkTransport` (TCP/IP to network printer)
- `BluetoothTransport` (BLE/Classic)
- `UsbTransport` (bulk transfer)
- `SerialTransport` (RS-232)

---

## Data Models

### Sealed Classes (Type-Safe)

```kotlin
sealed class PrintElement {
    data class Text(val value: String, val align: Align, val style: TextStyle)
    data class Row(val columns: List<Column>)
    data object LineFeed
    data object PaperCut
}
```

**Design Benefit**: Adding `BitmapCommand` later doesn't require changing existing code.

### Supporting Types

```kotlin
data class Column(
    val text: String,
    val width: Int,        // Character width
    val align: Align,
    val style: TextStyle
)

data class TextStyle(
    val bold: Boolean = false,
    val underline: Boolean = false
)

enum class Align { LEFT, CENTER, RIGHT }

data class PrinterConfig(val charsPerLine: Int)
```

---

## React Native JSON Contract

The engine accepts this JSON structure from React Native:

```json
{
  "config": {
    "charsPerLine": 32
  },
  "elements": [
    {
      "type": "text",
      "value": "Store Name",
      "align": "center",
      "bold": true
    },
    {
      "type": "row",
      "columns": [
        { "text": "Item", "width": 16 },
        { "text": "Qty", "width": 6, "align": "center" },
        { "text": "Price", "width": 10, "align": "right" }
      ]
    }
  ]
}
```

**Key Rules**:

- React Native sends **intent only**, never pre-padded strings
- Column widths are **character-based** (text mode)
- Alignment values: `"left"`, `"center"`, `"right"`
- This schema **remains stable** as bitmap/barcode features are added

---

## Example: Full Pipeline

```kotlin
// Input: React Native JSON
val json = """{"config": {"charsPerLine": 32}, "elements": [...]}"""

// Pipeline: JSON → Receipt → Layout → Commands → Bytes
val parser = ReceiptParser()
val receipt = parser.parse(json)

val layoutEngine = LayoutEngine(receipt.config)
val layoutLines = layoutEngine.layout(receipt.elements)

val renderer = TextRenderer()
val commands = renderer.render(layoutLines)

val encoder = EscPosEncoder()
val bytes = encoder.encode(commands)

// Ready to send to printer
transport.write(bytes)
```

**Or use the facade**:

```kotlin
val pipeline = RenderPipeline(config)
val bytes = EscPosEncoder().encode(
    TextRenderer().render(
        LayoutEngine(config).layout(
            ReceiptParser().parse(json).elements
        )
    )
)
```

---

## Extension Points for Future Features

### 1. Bitmap Rendering

Add to `PrintElement`:

```kotlin
sealed class PrintElement {
    // ... existing ...
    data class Bitmap(
        val imageData: ByteArray,
        val width: Int,
        val align: Align
    ) : PrintElement()
}
```

Create new renderer:

```kotlin
class BitmapRenderer(config: PrinterConfig) : Renderer {
    override fun render(elements: List<PrintElement>): List<PrinterCommand> {
        // Handle Bitmap elements
    }
}
```

No changes needed to `LayoutEngine` or `EscPosEncoder`.

### 2. Barcode / QR Code

Add command:

```kotlin
sealed class PrinterCommand {
    // ... existing ...
    data class Barcode1D(val code: String, val type: String) : PrinterCommand()
    data class QRCode(val data: String, val size: Int) : PrinterCommand()
}
```

Extend encoder:

```kotlin
class EscPosEncoder {
    private fun encodeBarcode(cmd: Barcode1D): List<Byte> {
        // GS k command implementation
    }
}
```

### 3. Dynamic Renderer Selection

```kotlin
val renderer = when (feature) {
    Feature.TEXT -> TextRenderer()
    Feature.BITMAP -> BitmapRenderer()
    Feature.BARCODE -> BarcodeRenderer()
}
val commands = renderer.render(layoutLines)
```

---

## Testing Strategy

Each layer is independently testable:

### Parser Tests

```kotlin
fun testParserValidatesColumnWidths() {
    val json = """{"config": {"charsPerLine": 10}, "elements": [
        {"type": "row", "columns": [
            {"text": "Item", "width": 7},
            {"text": "Price", "width": 5}  // Total: 12 > 10
        ]}
    ]}"""

    assertThrows<IllegalArgumentException> {
        ReceiptParser().parse(json)
    }
}
```

### Layout Tests

```kotlin
fun testLayoutWrappsLongText() {
    val element = PrintElement.Row(listOf(
        Column("Very Long Text", width = 5, align = Align.LEFT, style = TextStyle())
    ))
    val lines = LayoutEngine(config).layout(listOf(element))
    assertEquals(3, lines.size)  // Should wrap to 3 lines
}
```

### Encoder Tests

```kotlin
fun testEncoderAddsBoldCommand() {
    val cmd = PrinterCommand.Text("Hello", bold = true)
    val bytes = EscPosEncoder().encode(listOf(cmd))
    // Verify [0x1B, 0x45, 0x01] is present
    assertTrue(bytes.contains(0x1B) && bytes.contains(0x45))
}
```

---

## Performance Characteristics

- **Parser**: O(n) where n = element count
- **Layout**: O(m·w) where m = elements, w = average column width
- **Render**: O(l) where l = layout lines
- **Encode**: O(b) where b = output bytes

**Memory**: Minimal; each stage operates on small datasets

---

## Constraints & Rules

### Strict Constraints

- ✓ Kotlin only; no third-party libraries
- ✓ No Android UI APIs (no Context, Activity, etc.)
- ✓ No WebView
- ✓ No bitmap rendering in v1
- ✓ Unit-testable (no file I/O, network calls in core)

### Design Rules

- ✓ Parser produces Receipt; never mutate it
- ✓ LayoutEngine is stateless; reusable
- ✓ Encoder produces bytes; reversible (future auditing)
- ✓ Transport is interface only; implementations separate

---

## Encoding Examples

### Example 1: Simple Text

```
Input: PrinterCommand.Text("Hello", bold=false, align=LEFT)
Output (hex): 1B 61 00 48 65 6C 6C 6F 0A
              │  │  │   │ ├─────────────┘ │
              │  │  │   │ └─ "Hello" (UTF-8)
              │  │  └─ Alignment: 0=left
              │  └─ Alignment command: ESC a
              └─ Initialize
```

### Example 2: Bold Text

```
Input: PrinterCommand.Text("Total", bold=true, align=CENTER)
Output (hex): 1B 61 01 1B 45 01 54 6F 74 61 6C 1B 45 00 0A
              │  │  │  │  │  │  └───────────────┘ │  │  │
              │  │  │  │  │  └─ Bold ON          │  │  └─ Line feed
              │  │  │  │  └─ Bold command        │  └─ Bold OFF
              │  │  │  └─ Reset bold at end      └─ "Total"
              │  │  └─ Alignment: 1=center
              │  └─ Alignment command: ESC a
              └─ Initialize
```

### Example 3: Row with Alignment

```
Input: Row with 3 columns: "Item" (width=10, left), "Qty" (width=6, center), "Price" (width=10, right)
Output:
  1B 61 00        - Left align
  49 74 65 6D ... - "Item      " (padded to width 10)
  1B 61 01        - Center align
  20 20 51 74 79  - "  Qty  " (centered in width 6)
  1B 61 02        - Right align
  20 20 50 72 69  - "     Price" (right-aligned in width 10)
  0A              - Line feed
```

---

## Deliverables Checklist

- [x] Architecture explanation (above)
- [x] Kotlin source code
  - `Models.kt` - Data classes and sealed classes
  - `ReceiptParser.kt` - JSON parsing
  - `LayoutEngine.kt` - Column layout and wrapping
  - `RenderEngine.kt` - Command generation
  - `EscPosEncoder.kt` - Byte encoding
  - `PrinterTransport.kt` - Transport interface
- [x] Example showing RN payload parsing
- [x] ESC/POS hex dump documentation
- [x] Comments highlighting extension points

---

## Summary

This engine provides a **production-ready foundation** for thermal printer integration:

1. **Clean separation of concerns**: Each layer is independently testable
2. **Stable API contract**: React Native payload doesn't change as features grow
3. **Commercial quality**: Type-safe, well-documented, extensible
4. **Zero technical debt**: Pure Kotlin, no hacks, no shortcuts
5. **Future-proof**: Bitmap, barcode, QR can be added without core refactoring

The architecture is ready to scale from simple text to full POS receipt rendering.
