export type Align = 'left' | 'center' | 'right';

export type TextElement = {
  type: 'text';
  value: string;
  align?: Align;
  bold?: boolean;
  underline?: boolean;
  /** 2 prints double width and height. */
  size?: 1 | 2;
  /** White text on a full-width black band. */
  invert?: boolean;
  /** Blank paper below, in dots (8 dots = 1 mm). */
  spaceAfter?: number;
};

export type Column = {
  text: string;
  /** In characters of the printer's built-in font. A row's widths must fit `charsPerLine`. */
  width: number;
  align?: Align;
  bold?: boolean;
  underline?: boolean;
};

export type RowElement = {
  type: 'row';
  columns: Column[];
  /** White text on a full-width black band. */
  invert?: boolean;
  /** Blank paper below, in dots (8 dots = 1 mm). */
  spaceAfter?: number;
};
export type DividerElement = {
  type: 'divider';
  char?: string;
  /** Blank paper below, in dots (8 dots = 1 mm). */
  spaceAfter?: number;
};
export type FeedElement = { type: 'feed'; lines?: number };
/** Feeds to the cutter and cuts; no extra feeds needed before it. */
export type CutElement = { type: 'cut' };

export type ReceiptElement =
  TextElement | RowElement | DividerElement | FeedElement | CutElement;

/**
 * - `text`: printer's built-in font. Fast, but characters outside the code page print as `?`.
 * - `image`: every element is drawn as a bitmap.
 * - `auto`: built-in font where possible, bitmap for elements the code page can't print (Tamil, ₹).
 */
export type RenderMode = 'text' | 'image' | 'auto';

export type CodePage = 'PC437' | 'PC850' | 'PC858' | 'WPC1252';

export type PrinterProfile = {
  dotsPerLine: number;
  charsPerLine: number;
  codePage?: CodePage;
  /**
   * Image rows per raster command (default 256). The printer pauses between commands, so larger
   * prints smoother; lower it only if a printer prints garbage on large images.
   */
  bandRows?: number;
  /**
   * Distance from the print head to the cutter blade, in mm (default 13). It equals the blank gap
   * at the top of a bill, so measure that gap and put it here.
   */
  cutterDistanceMm?: number;
  /** Blank paper below the last line, in mm. Defaults to `cutterDistanceMm`, matching the top. */
  bottomMarginMm?: number;
};

export type ImageOptions = {
  /** Text height in dots. The built-in font is 24; Tamil reads better a little larger. */
  fontSize?: number;
  /** Font file in the app's Android assets, e.g. `fonts/NotoSansTamil-Regular.ttf`. */
  font?: string;
};

export type PrintOptions = {
  mode?: RenderMode;
  profile?: PrinterProfile;
  image?: ImageOptions;
};

export type NetworkPrinter = {
  host: string;
  /** Raw printing port. Defaults to 9100. */
  port?: number;
  /** Per connection attempt. Defaults to 3000. */
  timeoutMs?: number;
  /** Extra attempts while another device is printing. Defaults to 8 (about 12s). */
  retries?: number;
  /**
   * Wait for a printer that doesn't report status to finish before disconnecting. Defaults to 10000.
   * Printers that answer status requests disconnect as soon as the bill is done.
   */
  drainTimeoutMs?: number;
};

export type PrintResult = {
  bytesSent: number;
  /** Connection attempts; more than 1 means another device was printing. */
  attempts: number;
  /** Elements that were sent as bitmaps. */
  imageBlocks: number;
};

export type PreviewResult = {
  /** PNG, ready for `<Image source={{ uri: 'data:image/png;base64,' + base64 }} />`. */
  base64: string;
  width: number;
  height: number;
};

/** Error codes the returned promises reject with. */
export type PrinterErrorCode =
  | 'INVALID_RECEIPT'
  | 'PRINTER_UNREACHABLE'
  /** The connection dropped mid-send; the bill may be partly printed. Not retried automatically. */
  | 'PRINT_INTERRUPTED'
  | 'PRINT_ERROR'
  | 'UNSUPPORTED';
