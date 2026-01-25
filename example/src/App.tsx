import React from 'react';
import {
  Text,
  View,
  StyleSheet,
  Button,
  TextInput,
  Switch,
} from 'react-native';
import NativeThermalPrinter from 'react-native-thermal-printer';

export default function App() {
  const [state, setState] = React.useState({
    ip: '192.168.1.23',
    port: 9100,
    cutPaper: true,
  });

  const ThermalPrint = async () => {
    try {
      const receipt = {
        config: {
          maxCharPerLine: 48,
          printerWidthMm: 80,
        },
        header: [
          {
            type: 'text',
            text: 'Store Name',
            options: { align: 'center', bold: true, size: 'large' },
          },
          {
            type: 'text',
            text: '123 Main St, City, Country',
            options: { align: 'center' },
          },
          {
            type: 'text',
            text: 'Tel: +1234567890',
            options: { align: 'center' },
          },
          { type: 'divider' },
        ],
        body: [
          {
            type: 'table',
            columns: [
              { text: 'Item', width: 30, align: 'left' },
              { text: 'Qty', width: 20, align: 'center' },
              { text: 'Price', width: 25, align: 'right' },
              { text: 'Total', width: 25, align: 'right' },
            ],
            data: [
              ['Apple', '2', '$3.00', '$6.00'],
              ['Banana', '5', '$5.00', '$25.00'],
              ['Orange', '3', '$4.50', '$13.50'],
            ],
            options: { align: 'left' },
          },
          { type: 'divider' },
          {
            type: 'text',
            text: 'Total: $12.50',
            options: { align: 'right', bold: true },
          },
        ],
        footer: [
          { type: 'divider' },
          {
            type: 'text',
            text: 'Thank you for your purchase!',
            options: { align: 'center' },
          },
          { type: 'text', text: 'Visit again!', options: { align: 'center' } },
        ],
      };

      const connection = await NativeThermalPrinter.connect({
        ip: state.ip,
        port: state.port,
        timeout: 3000,
      });
      if (!connection) throw new Error('Connection to printer failed');

      const result = await NativeThermalPrinter.print(JSON.stringify(receipt));
      if (!result) throw new Error('Printing failed');
    } catch (error: any) {
      throw new Error(error.message || 'An unexpected error occurred');
    }
  };

  return (
    <View style={style.container}>
      <Text style={style.title}>React Native Thermal Printer</Text>

      <View style={style.formfield}>
        <Text>IP Address</Text>
        <TextInput
          value={state.ip}
          style={style.input}
          placeholder="Printer IP Address"
          onChangeText={(text) => setState({ ...state, ip: text })}
        />
      </View>

      <View style={style.formfield}>
        <Text>Port</Text>
        <TextInput
          value={state.port.toString()}
          style={style.input}
          placeholder="Printer Port"
          onChangeText={(text) => setState({ ...state, port: Number(text) })}
        />
      </View>

      <View style={style.formfield}>
        <Text>Cut Paper</Text>
        <Switch
          value={state.cutPaper}
          onValueChange={(value) => setState({ ...state, cutPaper: value })}
        />
      </View>

      <Button title="Print" onPress={ThermalPrint} />
    </View>
  );
}

const style = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    padding: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  formfield: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 5,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    padding: 8,
    borderRadius: 4,
  },
});
