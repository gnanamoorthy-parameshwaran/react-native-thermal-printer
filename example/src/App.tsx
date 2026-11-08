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
  PAPER_112MM,
  PAPER_58MM,
  PAPER_80MM,
  preview,
  print,
  type PreviewResult,
  type ReceiptElement,
  type RenderMode,
  type RowElement,
} from '@gnanamoorthy/react-native-thermal-printer';

type Lang = 'english' | 'tamil';

// English lines use the printer's own font (text mode); Tamil needs image mode.
const DEFAULT_MODE: Record<Lang, RenderMode> = {
  english: 'text',
  tamil: 'image',
};

type Item = {
  name: Record<Lang, string>;
  qty: number;
  unit: Record<Lang, string>;
  rate: number;
};

const KG = { english: 'kg', tamil: 'கி' };
const PC = { english: 'pc', tamil: 'எண்' };

const items: Item[] = [
  { name: { english: 'Tomato', tamil: 'தக்காளி' }, qty: 2, unit: KG, rate: 40 },
  {
    name: { english: 'Onion', tamil: 'வெங்காயம்' },
    qty: 1,
    unit: KG,
    rate: 35,
  },
  {
    name: {
      english: 'Ponni Rice 25 kg Bag',
      tamil: 'பொன்னி அரிசி 25 கிலோ மூட்டை',
    },
    qty: 1,
    unit: { english: 'bag', tamil: 'மூட்டை' },
    rate: 1450,
  },
  { name: { english: 'Sugar', tamil: 'சர்க்கரை' }, qty: 1, unit: KG, rate: 45 },
  {
    name: {
      english: 'Aashirvaad Select Premium Sharbati Whole Wheat Atta 10 kg',
      tamil: 'ஆசீர்வாத் செலக்ட் பிரீமியம் சர்பதி கோதுமை மாவு 10 கிலோ',
    },
    qty: 12,
    unit: PC,
    rate: 545,
  },
  {
    name: {
      english: 'Aachi Sambar and Rasam Powder Combo Pack 500 g',
      tamil: 'ஆச்சி சாம்பார் பொடி மற்றும் ரசப் பொடி காம்போ பேக் 500 கிராம்',
    },
    qty: 3,
    unit: PC,
    rate: 120,
  },
];

const LABELS: Record<
  Lang,
  {
    shop: string;
    address: string;
    phone: string;
    bill: string;
    items: string;
    subtotal: string;
    total: string;
    currency: string;
    thanks: string;
  }
> = {
  english: {
    shop: 'ANNAI STORES',
    address: '12, Gandhi Road, Madurai',
    phone: 'Ph: 98765 43210',
    bill: 'Bill',
    items: 'items',
    subtotal: 'Subtotal',
    total: 'TOTAL',
    // ₹ isn't in the printer's code page; "Rs." keeps the line in the printer font.
    currency: 'Rs.',
    thanks: 'Thank you, visit again!',
  },
  tamil: {
    shop: 'அன்னை மளிகை',
    address: '12, காந்தி சாலை, மதுரை',
    phone: 'தொலைபேசி: 98765 43210',
    bill: 'பில்',
    items: 'பொருட்கள்',
    subtotal: 'கூட்டுத்தொகை',
    total: 'மொத்தம்',
    currency: '₹',
    thanks: 'நன்றி! மீண்டும் வருக',
  },
};

const money = (n: number) =>
  n.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

// Columns use flex shares, so the bill fits any paper width. Each item takes two lines: a
// numbered, bold name, then the quantity and rate with the amount on the right, then a gap.
const itemLines =
  (lang: Lang) =>
  (item: Item, index: number): RowElement[] => [
    {
      type: 'row',
      columns: [
        { text: `${index + 1}.`, width: 3 },
        { text: item.name[lang], flex: 1, bold: true },
      ],
    },
    {
      type: 'row',
      spaceAfter: 12,
      columns: [
        { text: '', width: 3 },
        {
          text: `${item.qty} ${item.unit[lang]} x ${money(item.rate)}`,
          flex: 3,
        },
        { text: money(item.qty * item.rate), flex: 2, align: 'right' },
      ],
    },
  ];

const total = items.reduce((sum, i) => sum + i.qty * i.rate, 0);

const makeBill = (lang: Lang): ReceiptElement[] => {
  const t = LABELS[lang];
  return [
    {
      type: 'text',
      value: t.shop,
      align: 'center',
      bold: true,
      size: 2,
      invert: true,
      spaceAfter: 8,
    },
    { type: 'text', value: t.address, align: 'center' },
    { type: 'text', value: t.phone, align: 'center', spaceAfter: 8 },
    {
      type: 'row',
      columns: [
        { text: `${t.bill} #1042`, bold: true },
        { text: '01-11-2026 10:42', flex: 2, align: 'right' },
      ],
    },
    { type: 'divider', spaceAfter: 8 },
    ...items.flatMap(itemLines(lang)),
    { type: 'divider' },
    {
      type: 'row',
      spaceAfter: 6,
      columns: [
        { text: `${items.length} ${t.items}` },
        { text: `${t.subtotal} ${money(total)}`, flex: 2, align: 'right' },
      ],
    },
    {
      type: 'row',
      invert: true,
      spaceAfter: 16,
      columns: [
        { text: t.total, flex: 3, bold: true },
        {
          text: `${t.currency} ${money(total)}`,
          flex: 2,
          align: 'right',
          bold: true,
        },
      ],
    },
    { type: 'text', value: t.thanks, align: 'center' },
    { type: 'cut' },
  ];
};

const BILLS: Record<Lang, ReceiptElement[]> = {
  english: makeBill('english'),
  tamil: makeBill('tamil'),
};

const MODES: RenderMode[] = ['auto', 'image', 'text'];

const PAPERS = {
  '58mm': PAPER_58MM,
  '80mm': PAPER_80MM,
  '112mm': PAPER_112MM,
};
type Paper = keyof typeof PAPERS;

export default function App() {
  const [host, setHost] = React.useState('192.168.1.23');
  const [port, setPort] = React.useState('9100');
  const [lang, setLang] = React.useState<Lang>('tamil');
  const [mode, setMode] = React.useState<RenderMode>(DEFAULT_MODE.tamil);
  const bill = BILLS[lang];
  const [paper, setPaper] = React.useState<Paper>('80mm');
  const [image, setImage] = React.useState<PreviewResult | null>(null);
  const [busy, setBusy] = React.useState(false);

  // The top gap is the head-to-cutter distance; the bottom margin is set to match it.
  const [topGap, setTopGap] = React.useState('13');

  const printer = { host, port: Number(port) || 9100 };
  const profile = {
    ...PAPERS[paper],
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
    preview(bill, { mode, profile: PAPERS[paper] }).then(setImage, () =>
      setImage(null)
    );
  }, [bill, mode, paper]);

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

      <Text style={styles.label}>Bill</Text>
      <View style={styles.row}>
        {(Object.keys(BILLS) as Lang[]).map((l) => (
          <View key={l} style={styles.flex}>
            <Button
              title={l}
              color={l === lang ? '#2196F3' : '#9E9E9E'}
              onPress={() => {
                setLang(l);
                setMode(DEFAULT_MODE[l]);
              }}
            />
          </View>
        ))}
      </View>

      <Text style={styles.label}>Paper</Text>
      <View style={styles.row}>
        {(Object.keys(PAPERS) as Paper[]).map((p) => (
          <View key={p} style={styles.flex}>
            <Button
              title={p}
              color={p === paper ? '#2196F3' : '#9E9E9E'}
              onPress={() => setPaper(p)}
            />
          </View>
        ))}
      </View>

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
