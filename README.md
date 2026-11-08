# react-native-thermal-printer

Print receipts in any language, including Tamil, on ESC/POS thermal printers from React Native.

## Installation

```sh
npm install react-native-thermal-printer
```

## Usage

Android only for now; network (Wi-Fi/LAN, port 9100) printers.

```ts
import { createPrinter } from 'react-native-thermal-printer';

const printer = createPrinter({ host: '192.168.1.23' });

await printer.print([
  { type: 'text', value: 'அன்னை மளிகை', align: 'center', bold: true, size: 2 },
  { type: 'divider' },
  {
    type: 'row',
    columns: [
      { text: 'தக்காளி', width: 30 },
      { text: '80.00', width: 18, align: 'right' },
    ],
  },
  { type: 'cut' },
]);
```

Defaults are 80mm paper (576 dots, 48 characters) and `auto` mode. Pass `{ profile: PAPER_58MM }` for 58mm printers.

### Render modes

| Mode             | What prints                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------ |
| `auto` (default) | The printer's own font where it can, and an image for elements it can't print, such as Tamil text or ₹ |
| `image`          | Every element as an image                                                                              |
| `text`           | The printer's own font only; unsupported characters print as `?`                                       |

Column widths are always in characters, so a receipt lays out the same in every mode. Image height is measured from the content, so you never set a page size.

To use your own font for image text, put it in `android/app/src/main/assets/fonts/` and pass `{ image: { font: 'fonts/NotoSansTamil-Regular.ttf', fontSize: 28 } }`.

### Styling

- `invert: true` on a `text` or `row` prints white on a full-width black band. Use it for the shop name or the grand total.
- `spaceAfter` on a `text`, `row` or `divider` leaves blank paper below it, in dots (8 dots = 1 mm). Around 12 dots between line items makes them easy to tell apart.
- `bold`, `underline` and `size: 2` (on `text`) work as you'd expect.

For Tamil bills, `image` mode gives the most consistent look, because every line uses the same font. In `auto` mode, English-only lines use the printer's own font.

### Several devices, one printer

Each `print` call opens a connection, sends the whole bill, and closes it. If another device is printing, the call retries for about 12 seconds before rejecting with `PRINTER_UNREACHABLE`. A `PRINT_INTERRUPTED` error means the connection dropped mid-bill; it isn't retried, because the bill may have partly printed.

### Preview

```ts
const { base64, width, height } = await printer.preview(elements);
<Image source={{ uri: `data:image/png;base64,${base64}` }} style={{ width: '100%', aspectRatio: width / height }} />
```

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
