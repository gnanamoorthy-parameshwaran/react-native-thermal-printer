import React from 'react';
import {
  Text,
  View,
  StyleSheet,
  Button,
  TextInput,
  ScrollView,
  Alert,
} from 'react-native';
import ThermalPrinterAPI, {
  type ReceiptData,
} from 'react-native-thermal-printer';

export default function App() {
  const [state, setState] = React.useState({
    ip: '192.168.1.100',
    port: 9100,
    timeout: 5000,
    connected: false,
  });
  const [loading, setLoading] = React.useState(false);

  /**
   * Connect to the thermal printer
   */
  const handleConnect = async () => {
    try {
      setLoading(true);
      const result = await ThermalPrinterAPI.connect({
        ip: state.ip,
        port: state.port,
        timeout: state.timeout,
      });
      setState({ ...state, connected: true });
      Alert.alert('Success', result);
    } catch (error: any) {
      Alert.alert('Connection Error', error.message);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Disconnect from the printer
   */
  const handleDisconnect = async () => {
    try {
      setLoading(true);
      const result = await ThermalPrinterAPI.disconnect();
      setState({ ...state, connected: false });
      Alert.alert('Success', result);
    } catch (error: any) {
      Alert.alert('Disconnection Error', error.message);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Print a sample receipt using the new engine
   */
  const handlePrint = async () => {
    try {
      setLoading(true);

      // Create a receipt using the new engine JSON schema
      const receipt: ReceiptData = {
        config: {
          charsPerLine: 48, // Extended width for better table spacing
        },
        elements: [
          {
            type: 'text',
            value: 'Welcome to Store',
            align: 'center',
            bold: true,
          },
          {
            type: 'linefeed',
          },
          {
            type: 'text',
            value: '123 Main Street, City',
            align: 'center',
          },
          {
            type: 'text',
            value: 'Phone: +1-234-567-8900',
            align: 'center',
          },
          {
            type: 'linefeed',
          },
          // Items table
          {
            type: 'row',
            columns: [
              { text: 'Item', width: 28, align: 'left' },
              { text: 'Qty', width: 10, align: 'center' },
              { text: 'Price', width: 10, align: 'right' },
            ],
          },
          {
            type: 'divider',
            char: '-',
          },
          {
            type: 'row',
            columns: [
              {
                text: 'Coffee is good for health and not that easy',
                width: 28,
                align: 'left',
              },
              { text: '2', width: 10, align: 'center' },
              { text: '$5.00', width: 10, align: 'right' },
            ],
          },
          {
            type: 'row',
            columns: [
              { text: 'Pastry', width: 28, align: 'left' },
              { text: '1', width: 10, align: 'center' },
              { text: '$3.50', width: 10, align: 'right' },
            ],
          },
          {
            type: 'divider',
            char: '-',
          },
          {
            type: 'row',
            columns: [
              { text: 'Total', width: 28, align: 'right', bold: true },
              { text: '$8.50', width: 20, align: 'right', bold: true },
            ],
          },
          {
            type: 'linefeed',
          },
          {
            type: 'linefeed',
          },
          {
            type: 'text',
            value: 'Thank you for your purchase!',
            align: 'center',
          },
          {
            type: 'linefeed',
            count: 6,
          },
          {
            type: 'cut',
          },
        ],
      };

      const byteCount = await ThermalPrinterAPI.print(receipt);
      Alert.alert(
        'Print Success',
        `Receipt printed successfully.\nBytes sent: ${byteCount}`
      );
    } catch (error: any) {
      Alert.alert('Print Error', error.message);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Get ESC/POS bytes for debugging
   */
  const handleDebug = async () => {
    try {
      setLoading(true);

      const receipt: ReceiptData = {
        config: {
          charsPerLine: 32,
        },
        elements: [
          {
            type: 'text',
            value: 'Test Receipt',
            align: 'center',
            bold: true,
          },
          {
            type: 'linefeed',
          },
          {
            type: 'text',
            value: 'ESC/POS Bytes Debug',
            align: 'left',
          },
          {
            type: 'cut',
          },
        ],
      };

      const hexBytes = await ThermalPrinterAPI.getEscPosBytes(receipt);
      Alert.alert('ESC/POS Bytes', `${hexBytes.substring(0, 200)}...`);
    } catch (error: any) {
      Alert.alert('Debug Error', error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>React Native Thermal Printer</Text>
      <Text style={styles.subtitle}>Professional Kotlin Engine</Text>

      {/* Connection Status */}
      <View style={styles.statusBox}>
        <Text style={styles.statusLabel}>Status:</Text>
        <Text
          style={[
            styles.statusText,
            { color: state.connected ? '#4CAF50' : '#f44336' },
          ]}
        >
          {state.connected ? '✓ Connected' : '✗ Disconnected'}
        </Text>
      </View>

      {/* IP Address Input */}
      <View style={styles.formGroup}>
        <Text style={styles.label}>Printer IP Address</Text>
        <TextInput
          style={styles.input}
          placeholder="192.168.1.23"
          value={state.ip}
          onChangeText={(text) => setState({ ...state, ip: text })}
          editable={!state.connected}
        />
      </View>

      {/* Port Input */}
      <View style={styles.formGroup}>
        <Text style={styles.label}>Port</Text>
        <TextInput
          style={styles.input}
          placeholder="9100"
          value={state.port.toString()}
          keyboardType="numeric"
          onChangeText={(text) => setState({ ...state, port: parseInt(text) })}
          editable={!state.connected}
        />
      </View>

      {/* Timeout Input */}
      <View style={styles.formGroup}>
        <Text style={styles.label}>Timeout (ms)</Text>
        <TextInput
          style={styles.input}
          placeholder="5000"
          value={state.timeout.toString()}
          keyboardType="numeric"
          onChangeText={(text) =>
            setState({ ...state, timeout: parseInt(text) })
          }
          editable={!state.connected}
        />
      </View>

      {/* Action Buttons */}
      <View style={styles.buttonGroup}>
        {!state.connected ? (
          <Button
            title="Connect"
            onPress={handleConnect}
            disabled={loading || !state.ip}
            color="#2196F3"
          />
        ) : (
          <Button
            title="Disconnect"
            onPress={handleDisconnect}
            disabled={loading}
            color="#f44336"
          />
        )}
      </View>

      {state.connected && (
        <>
          <View style={styles.divider} />

          <View style={styles.buttonGroup}>
            <Button
              title="Print Receipt"
              onPress={handlePrint}
              disabled={loading}
              color="#4CAF50"
            />
          </View>

          <View style={styles.buttonGroup}>
            <Button
              title="Debug: Show ESC/POS Bytes"
              onPress={handleDebug}
              disabled={loading}
              color="#FF9800"
            />
          </View>

          <View style={styles.infoBox}>
            <Text style={styles.infoTitle}>Info:</Text>
            <Text style={styles.infoText}>
              • Prints a sample receipt with items, total, and cut command
            </Text>
            <Text style={styles.infoText}>
              • Receipt data follows the thermal printer engine JSON schema
            </Text>
            <Text style={styles.infoText}>
              • Supports text, rows, linefeeds, and cut commands
            </Text>
          </View>
        </>
      )}

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          React Native Thermal Printer v0.1.0
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#f5f5f5',
    padding: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 20,
  },
  statusBox: {
    backgroundColor: '#fff',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    elevation: 2,
  },
  statusLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  statusText: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  formGroup: {
    marginBottom: 12,
  },
  label: {
    fontSize: 12,
    fontWeight: '600',
    color: '#333',
    marginBottom: 6,
  },
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    padding: 10,
    fontSize: 14,
  },
  buttonGroup: {
    marginVertical: 8,
  },
  divider: {
    height: 1,
    backgroundColor: '#ddd',
    marginVertical: 16,
  },
  infoBox: {
    backgroundColor: '#e3f2fd',
    borderLeftWidth: 4,
    borderLeftColor: '#2196F3',
    padding: 12,
    borderRadius: 4,
    marginTop: 16,
  },
  infoTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#1976D2',
    marginBottom: 6,
  },
  infoText: {
    fontSize: 12,
    color: '#1565C0',
    marginBottom: 4,
  },
  footer: {
    marginTop: 32,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#ddd',
    alignItems: 'center',
  },
  footerText: {
    fontSize: 12,
    color: '#999',
  },
});
