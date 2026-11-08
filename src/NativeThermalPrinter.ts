import { TurboModuleRegistry, type TurboModule } from 'react-native';

type PrintResult = {
  bytesSent: number;
  attempts: number;
  imageBlocks: number;
};

type PreviewResult = {
  base64: string;
  width: number;
  height: number;
};

// Printer and job are passed as JSON so the receipt schema can grow without codegen changes.
export interface Spec extends TurboModule {
  print(printerJson: string, jobJson: string): Promise<PrintResult>;
  preview(jobJson: string): Promise<PreviewResult>;
  encode(jobJson: string): Promise<string>;
  checkConnection(printerJson: string): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('ThermalPrinter');
