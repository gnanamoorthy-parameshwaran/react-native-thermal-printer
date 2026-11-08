import { bridge } from './bridge';
import type {
  NetworkPrinter,
  PreviewResult,
  PrinterProfile,
  PrintOptions,
  PrintResult,
  ReceiptElement,
} from './types';

export * from './types';

export const PAPER_80MM: PrinterProfile = {
  dotsPerLine: 576,
  charsPerLine: 48,
  codePage: 'PC437',
};

export const PAPER_58MM: PrinterProfile = {
  dotsPerLine: 384,
  charsPerLine: 32,
  codePage: 'PC437',
};

const job = (elements: ReceiptElement[], options: PrintOptions = {}) =>
  JSON.stringify({
    mode: options.mode ?? 'auto',
    profile: options.profile ?? PAPER_80MM,
    image: options.image ?? {},
    elements,
  });

/**
 * Sends a receipt as one job. The connection is opened for this job only, so other devices can
 * print to the same printer; if one is printing, this retries until it finishes.
 */
export function print(
  printer: NetworkPrinter,
  elements: ReceiptElement[],
  options?: PrintOptions
): Promise<PrintResult> {
  return bridge.print(JSON.stringify(printer), job(elements, options));
}

/** Renders the receipt as it will appear on paper, without a printer. */
export function preview(
  elements: ReceiptElement[],
  options?: PrintOptions
): Promise<PreviewResult> {
  return bridge.preview(job(elements, options));
}

/** Returns the ESC/POS bytes for the receipt, base64-encoded. */
export function encode(
  elements: ReceiptElement[],
  options?: PrintOptions
): Promise<string> {
  return bridge.encode(job(elements, options));
}

/** Whether the printer accepts a connection right now. */
export function checkConnection(printer: NetworkPrinter): Promise<boolean> {
  return bridge.checkConnection(JSON.stringify(printer));
}

/** Binds a printer and default options, so call sites only pass the receipt. */
export function createPrinter(
  printer: NetworkPrinter,
  defaults: PrintOptions = {}
) {
  const merge = (options?: PrintOptions): PrintOptions => ({
    ...defaults,
    ...options,
    image: { ...defaults.image, ...options?.image },
  });
  return {
    print: (elements: ReceiptElement[], options?: PrintOptions) =>
      print(printer, elements, merge(options)),
    preview: (elements: ReceiptElement[], options?: PrintOptions) =>
      preview(elements, merge(options)),
    encode: (elements: ReceiptElement[], options?: PrintOptions) =>
      encode(elements, merge(options)),
    checkConnection: () => checkConnection(printer),
  };
}
