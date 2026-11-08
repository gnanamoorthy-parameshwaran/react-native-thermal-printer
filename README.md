# @gnanamoorthy/react-native-thermal-printer

Print receipts in any language, including Tamil, on ESC/POS thermal printers from React Native.

## Installation

Install from a GitHub release tag. npm builds the package on install.

```sh
npm install github:gnanamoorthy-parameshwaran/react-native-thermal-printer#v0.1.0
```

Then rebuild the native app (`npx expo run:android` for Expo dev-client apps, or `npx react-native run-android`).

## Usage

Android only for now; network (Wi-Fi/LAN, port 9100) printers.

```ts
import { createPrinter } from '@gnanamoorthy/react-native-thermal-printer';

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

Defaults are 80mm paper (576 dots, 48 characters) and `auto` mode.

### Paper sizes

Pass a profile for other printers: `PAPER_58MM` (384 dots, 32 chars), `PAPER_80MM` or `PAPER_112MM` (832 dots, 69 chars). For anything else, set `dotsPerLine` and `charsPerLine` yourself; the printer's self-test page usually lists its dots per line.

```ts
const printer = createPrinter(
  { host: '192.168.1.23' },
  { profile: PAPER_58MM }
);
```

To make one bill layout fit every paper size, give columns a `flex` share instead of a fixed `width`. Flex columns split whatever space fixed-width columns leave, and a row with any flex column fills the line:

```ts
columns: [
  { text: '1.', width: 3 }, // fixed
  { text: '2 kg x 40.00', flex: 3 }, // 3/5 of the rest
  { text: '80.00', flex: 2, align: 'right' }, // 2/5
];
```

In image mode, a cell that only just overflows its column is drawn slightly smaller rather than wrapping.

### Render modes

| Mode             | What prints                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------ |
| `auto` (default) | The printer's own font where it can, and an image for elements it can't print, such as Tamil text or ₹ |
| `image`          | Every element as an image                                                                              |
| `text`           | The printer's own font only; unsupported characters print as `?`                                       |

Column widths are in characters (or `flex` shares), so a receipt lays out the same in every mode. Image height is measured from the content, so you never set a page size.

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
