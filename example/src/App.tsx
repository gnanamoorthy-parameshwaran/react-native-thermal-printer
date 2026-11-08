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

type Item = { name: string; qty: number; unit: string; rate: number };

const items: Item[] = [
  { name: 'தக்காளி (Tomato)', qty: 2, unit: 'kg', rate: 40 },
  { name: 'வெங்காயம்', qty: 1, unit: 'kg', rate: 35 },
  { name: 'பொன்னி அரிசி 25 கிலோ மூட்டை', qty: 1, unit: 'bag', rate: 1450 },
  { name: 'Sugar', qty: 1, unit: 'kg', rate: 45 },
  {
    name: 'Aashirvaad Select Premium Sharbati Whole Wheat Atta 10 kg',
    qty: 12,
    unit: 'pc',
    rate: 545,
  },
  {
    name: 'ஆச்சி சாம்பார் பொடி மற்றும் ரசப் பொடி காம்போ பேக் 500 கிராம்',
    qty: 3,
    unit: 'pc',
    rate: 120,
  },
];

const money = (n: number) =>
  n.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

// 80mm paper is 48 columns. Each item takes two lines: a numbered, bold name, then the
// quantity and rate under it with the amount on the right, and a gap before the next item.
const itemLines = (item: Item, index: number): RowElement[] => [
  {
    type: 'row',
    columns: [
      { text: `${index + 1}.`, width: 4 },
      { text: item.name, width: 44, bold: true },
    ],
  },
  {
    type: 'row',
    spaceAfter: 12,
    columns: [
      { text: '', width: 4 },
      { text: `${item.qty} ${item.unit} x ${money(item.rate)}`, width: 28 },
      { text: money(item.qty * item.rate), width: 16, align: 'right' },
    ],
  },
];

const total = items.reduce((sum, i) => sum + i.qty * i.rate, 0);

const bill: ReceiptElement[] = [
  {
    type: 'text',
    value: 'அன்னை மளிகை',
    align: 'center',
    bold: true,
    size: 2,
    invert: true,
    spaceAfter: 8,
  },
  { type: 'text', value: '12, Gandhi Road, Madurai', align: 'center' },
  {
    type: 'text',
    value: 'Ph: 98765 43210',
    align: 'center',
    spaceAfter: 8,
  },
  {
    type: 'row',
    columns: [
      { text: 'Bill #1042', width: 24, bold: true },
      { text: '01-11-2026 10:42', width: 24, align: 'right' },
    ],
  },
  { type: 'divider', spaceAfter: 8 },
  ...items.flatMap(itemLines),
  { type: 'divider' },
  {
    type: 'row',
    spaceAfter: 6,
    columns: [
      { text: `${items.length} items`, width: 24 },
      { text: `Subtotal ${money(total)}`, width: 24, align: 'right' },
    ],
  },
  {
    type: 'row',
    invert: true,
    spaceAfter: 16,
    columns: [
      { text: 'மொத்தம் TOTAL', width: 24, bold: true },
      { text: `₹ ${money(total)}`, width: 24, align: 'right', bold: true },
    ],
  },
  { type: 'text', value: 'நன்றி! மீண்டும் வருக', align: 'center' },
  { type: 'text', value: 'Thank you, visit again', align: 'center' },
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
