package it.manzolo.bluetoothwatcher.mqtt.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

import it.manzolo.bluetoothwatcher.mqtt.device.DeviceInfo;
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents;
import it.manzolo.bluetoothwatcher.mqtt.utils.Date;

public final class BluetoothClient {
    public static final String TAG = "BluetoothClient";
    private static final UUID UUID_SERIAL_PORT_SERVICE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int BUFFER_LENGTH = 130;

    private volatile boolean stopWorker;
    private final Context context;
    private BluetoothSocket bluetoothSocket;
    private OutputStream bluetoothOutputStream;
    private InputStream bluetoothInputStream;
    private int readBufferPosition;
    private byte[] readBuffer;
    private final String deviceAddress;

    private final BroadcastReceiver closeBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Try closing Bluetooth...");
            try {
                close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    };

    public BluetoothClient(Context context, String deviceAddress) {
        this.deviceAddress = deviceAddress;
        this.context = context;
        context.registerReceiver(closeBluetoothReceiver, new IntentFilter(BluetoothEvents.CLOSECONNECTION));
    }

    public void retrieveData() throws Exception {
        try {
            if (this.open()) {
                Thread.sleep(100);
                this.sendCommand((byte) 0xd0, "setBacklight");
                Thread.sleep(100);
                this.sendCommand((byte) 0xe0, "setScreenTimeout");
                Thread.sleep(100);
                this.dataDump();
            }
            Thread.sleep(100);
        } catch (Exception e) {
            this.close();
            throw e;
        }
    }

    @SuppressLint("MissingPermission")
    private void findBT() throws Exception {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        bluetoothAdapter.cancelDiscovery();

        if (!bluetoothAdapter.isEnabled()) {
            throw new Exception("Bluetooth not enabled");
        }

        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(this.deviceAddress);
        Log.d(TAG, "Bluetooth Device Found: " + bluetoothDevice.getAddress());
        this.bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID_SERIAL_PORT_SERVICE);
    }

    private void sendCommand(byte command, String commandName) {
        try {
            Log.d(TAG, commandName);
            this.bluetoothOutputStream.write(command);
        } catch (IOException e) {
            Log.e(TAG, "Error in " + commandName, e);
        }
    }

    @SuppressLint("MissingPermission")
    private boolean open() throws Exception {
        this.findBT();
        try {
            Log.d(TAG, "Connecting to " + bluetoothSocket.getRemoteDevice().getAddress());
            bluetoothSocket.connect();
            Log.d(TAG, "Connected");
            bluetoothOutputStream = bluetoothSocket.getOutputStream();
            bluetoothInputStream = bluetoothSocket.getInputStream();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error during connection", e);
            throw new Exception("Unable to connect to " + this.deviceAddress, e);
            //close();

        }
    }

    private void dataDump() {
        try {
            Log.d(TAG, "Requesting Bluetooth data...");
            this.sendCommand((byte) 0xf0, "dataDump");
            this.listen();
        } catch (Exception e) {
            Log.e(TAG, "Error in dataDump", e);
        }
    }

    private void listen() {
        readBufferPosition = 0;
        readBuffer = new byte[BUFFER_LENGTH];
        stopWorker = false;

        Thread workerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                try {
                    int bytesAvailable = bluetoothInputStream.available();
                    if (bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];
                        int length;
                        while ((length = bluetoothInputStream.read(packetBytes)) != -1) {
                            System.arraycopy(packetBytes, 0, readBuffer, readBufferPosition, length);
                            readBufferPosition += length;
                            if (readBufferPosition >= BUFFER_LENGTH) {
                                processBuffer();
                                break;
                            }
                        }
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "Error while listening", ex);
                    stopWorker = true;
                }
            }
        });

        workerThread.start();
    }

    private void processBuffer() {
        try {
            DeviceInfo deviceInfo = new DeviceInfo(deviceAddress, readBuffer);
            Log.d(TAG, "Device: " + deviceInfo.getAddress());
            Log.d(TAG, deviceInfo.getVolt() + " Volt");
            Log.d(TAG, deviceInfo.getAmp() + " A");
            Log.d(TAG, deviceInfo.getmW() + " mW");
            Log.d(TAG, deviceInfo.getTempC() + "°C");
            Log.d(TAG, deviceInfo.getTempF() + "°F");

            String now = Date.now();
            Intent intentBt = new Intent(BluetoothEvents.DATA_RETRIEVED);
            intentBt.putExtra("device", deviceInfo.getAddress());
            intentBt.putExtra("volt", Objects.requireNonNull(deviceInfo.getVolt()).toString());
            intentBt.putExtra("data", now);
            intentBt.putExtra("tempC", Objects.requireNonNull(deviceInfo.getTempC()).toString());
            intentBt.putExtra("tempF", Objects.requireNonNull(deviceInfo.getTempF()).toString());
            intentBt.putExtra("amp", Objects.requireNonNull(deviceInfo.getAmp()).toString());
            intentBt.putExtra("message", deviceInfo.getAddress() + " " + deviceInfo.getVolt().toString() + "v " + deviceInfo.getTempC().toString() + "°");
            context.sendBroadcast(intentBt);

            context.sendBroadcast(new Intent(BluetoothEvents.CLOSECONNECTION));
        } catch (Exception e) {
            Log.e(TAG, "Error processing buffer", e);
        }
    }

    private void close() throws IOException {
        stopWorker = true;
        if (bluetoothOutputStream != null) {
            bluetoothOutputStream.close();
        }
        if (bluetoothInputStream != null) {
            bluetoothInputStream.close();
        }
        if (bluetoothSocket != null) {
            bluetoothSocket.close();
        }

        context.unregisterReceiver(closeBluetoothReceiver);
        Log.d(TAG, "Bluetooth Closed!");
    }
}