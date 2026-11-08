import type { Spec } from './NativeThermalPrinter';

const unsupported = () =>
  Promise.reject(
    Object.assign(new Error('Thermal printing is not supported on web'), {
      code: 'UNSUPPORTED',
    })
  );

export const bridge: Pick<
  Spec,
  'print' | 'preview' | 'encode' | 'checkConnection'
> = {
  print: unsupported,
  preview: unsupported,
  encode: unsupported,
  checkConnection: unsupported,
};
