import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type NetPrinter = {
  ip: string;
  port: number;
  timeout: number;
};

export interface Spec extends TurboModule {
  connect(printer: NetPrinter): Promise<boolean>;
  print(data: string): Promise<boolean>;
  disconnect(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativeThermalPrinter');
