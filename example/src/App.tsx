import React from 'react';
import {
  Alert,
  Button,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  checkConnection,
  PAPER_80MM,
  preview,
  print,
  type PreviewResult,
  type ReceiptElement,
  type RenderMode,
  type RowElement,
} from 'react-native-thermal-printer';

// 80mm paper: 48 columns. Item 22 + Qty 6 + Rate 9 + Amount 11 = 48.
const item = (
  name: string,
  qty: string,
  rate: string,
  amount: string
): RowElement => ({
  type: 'row',
  columns: [
    { text: name, width: 22 },
    { text: qty, width: 6, align: 'right' },
    { text: rate, width: 9, align: 'right' },
    { text: amount, width: 11, align: 'right' },
  ],
});

const bill: ReceiptElement[] = [
  { type: 'text', value: 'அன்னை மளிகை', align: 'center', bold: true, size: 2 },
  { type: 'text', value: '12, Gandhi Road, Madurai', align: 'center' },
  { type: 'text', value: 'Bill #1042   01-11-2026 10:42', align: 'center' },
  { type: 'divider' },
  item('Item', 'Qty', 'Rate', 'Amount'),
  { type: 'divider' },
  item('தக்காளி (Tomato)', '2 kg', '40.00', '80.00'),
  item('வெங்காயம்', '1 kg', '35.00', '35.00'),
  item('பொன்னி அரிசி 25 கிலோ மூட்டை', '1', '1450.00', '1450.00'),
  item('Sugar', '1 kg', '45.00', '45.00'),
  { type: 'divider' },
  {
    type: 'row',
    columns: [
      { text: 'மொத்தம்', width: 30, bold: true },
      { text: '₹1610.00', width: 18, align: 'right', bold: true },
    ],
  },
  { type: 'divider', char: '=' },
  { type: 'text', value: 'நன்றி! மீண்டும் வருக', align: 'center' },
  { type: 'cut' },
];

const MODES: RenderMode[] = ['auto', 'image', 'text'];

export default function App() {
  const [host, setHost] = React.useState('192.168.1.23');
  const [port, setPort] = React.useState('9100');
  const [mode, setMode] = React.useState<RenderMode>('auto');
  const [image, setImage] = React.useState<PreviewResult | null>(null);
  const [busy, setBusy] = React.useState(false);

  // The top gap is the head-to-cutter distance; the bottom margin is set to match it.
  const [topGap, setTopGap] = React.useState('13');

  const printer = { host, port: Number(port) || 9100 };
  const profile = {
    ...PAPER_80MM,
    cutterDistanceMm: Number(topGap) || 13,
  };

  const run = async (task: () => Promise<void>) => {
    setBusy(true);
    try {
      await task();
    } catch (e: any) {
      Alert.alert(e.code ?? 'Error', e.message);
    } finally {
      setBusy(false);
    }
  };

  React.useEffect(() => {
    preview(bill, { mode }).then(setImage, () => setImage(null));
  }, [mode]);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Thermal Printer</Text>

      <Text style={styles.label}>Printer IP</Text>
      <TextInput style={styles.input} value={host} onChangeText={setHost} />
      <Text style={styles.label}>Port</Text>
      <TextInput
        style={styles.input}
        value={port}
        onChangeText={setPort}
        keyboardType="numeric"
      />
      <Text style={styles.label}>Measured top gap (mm)</Text>
      <TextInput
        style={styles.input}
        value={topGap}
        onChangeText={setTopGap}
        keyboardType="decimal-pad"
      />

      <Text style={styles.label}>Render mode</Text>
      <View style={styles.row}>
        {MODES.map((m) => (
          <View key={m} style={styles.flex}>
            <Button
              title={m}
              color={m === mode ? '#2196F3' : '#9E9E9E'}
              onPress={() => setMode(m)}
            />
          </View>
        ))}
      </View>

      <View style={styles.row}>
        <View style={styles.flex}>
          <Button
            title="Check"
            disabled={busy}
            onPress={() =>
              run(async () => {
                const ok = await checkConnection(printer);
                Alert.alert(ok ? 'Printer reachable' : 'Printer not reachable');
              })
            }
          />
        </View>
        <View style={styles.flex}>
          <Button
            title="Print"
            color="#4CAF50"
            disabled={busy}
            onPress={() =>
              run(async () => {
                const r = await print(printer, bill, { mode, profile });
                Alert.alert(
                  'Printed',
                  `${r.bytesSent} bytes, ${r.imageBlocks} image blocks, ` +
                    `${r.attempts} attempt(s)`
                );
              })
            }
          />
        </View>
      </View>

      {image && (
        <View style={styles.paper}>
          <Image
            source={{ uri: `data:image/png;base64,${image.base64}` }}
            style={[
              styles.preview,
              { aspectRatio: image.width / image.height },
            ]}
            resizeMode="contain"
          />
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, backgroundColor: '#f5f5f5', flexGrow: 1 },
  title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12, color: '#333' },
  label: { fontSize: 12, fontWeight: '600', color: '#333', marginTop: 8 },
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    padding: 10,
    marginTop: 4,
  },
  row: { flexDirection: 'row', gap: 8, marginTop: 12 },
  flex: { flex: 1 },
  paper: {
    marginTop: 16,
    backgroundColor: '#fff',
    padding: 8,
    borderRadius: 4,
    elevation: 2,
  },
  preview: { width: '100%' },
});
