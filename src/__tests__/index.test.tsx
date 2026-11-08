import { beforeEach, describe, expect, it, jest } from '@jest/globals';

const mockNative = {
  print: jest.fn(() => Promise.resolve({})),
  preview: jest.fn(() => Promise.resolve({})),
  encode: jest.fn(() => Promise.resolve('')),
  checkConnection: jest.fn(() => Promise.resolve(true)),
};
jest.mock('../NativeThermalPrinter', () => ({
  __esModule: true,
  default: mockNative,
}));

const { PAPER_58MM, createPrinter, print } = require('../index');

const lastArgs = (fn: jest.Mock) => fn.mock.calls[fn.mock.calls.length - 1]!;

describe('print', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('defaults to auto mode on 80mm paper', async () => {
    await print({ host: '10.0.0.5' }, [{ type: 'cut' }]);
    const [printer, job] = lastArgs(mockNative.print) as [string, string];
    expect(JSON.parse(printer)).toEqual({ host: '10.0.0.5' });
    expect(JSON.parse(job)).toEqual({
      mode: 'auto',
      profile: { dotsPerLine: 576, charsPerLine: 48, codePage: 'PC437' },
      image: {},
      elements: [{ type: 'cut' }],
    });
  });

  it('merges printer defaults with per-call options', async () => {
    const printer = createPrinter(
      { host: '10.0.0.5' },
      { profile: PAPER_58MM, image: { fontSize: 30, font: 'fonts/a.ttf' } }
    );
    await printer.print([], { mode: 'image', image: { fontSize: 32 } });
    const job = JSON.parse((lastArgs(mockNative.print) as string[])[1]!);
    expect(job.mode).toBe('image');
    expect(job.profile).toEqual(PAPER_58MM);
    expect(job.image).toEqual({ fontSize: 32, font: 'fonts/a.ttf' });
  });
});
