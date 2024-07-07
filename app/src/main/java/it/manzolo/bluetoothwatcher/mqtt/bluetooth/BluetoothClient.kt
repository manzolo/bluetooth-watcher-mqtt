package it.manzolo.bluetoothwatcher.mqtt.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import it.manzolo.bluetoothwatcher.mqtt.device.DeviceInfo
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import it.manzolo.bluetoothwatcher.mqtt.utils.Date.now
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Objects
import java.util.UUID
import kotlin.concurrent.Volatile

class BluetoothClient(private val context: Context, private val deviceAddress: String) {
    @Volatile
    private var stopWorker = false
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothOutputStream: OutputStream? = null
    private var bluetoothInputStream: InputStream? = null
    private var readBufferPosition = 0
    private lateinit var readBuffer: ByteArray

    @SuppressLint("MissingPermission")
    @Throws(Exception::class)
    private fun open(): Boolean {
        this.findBT()
        var retryCount = 0
        val maxRetries = 2
        val retryDelay: Long = 1000 // 1 second delay between retries

        while (retryCount < maxRetries) {
            try {
                Log.d(
                    TAG,
                    "Connecting to " + bluetoothSocket!!.remoteDevice.address + " (attempt " + (retryCount + 1) + ")"
                )
                bluetoothSocket!!.connect()
                Log.d(TAG, "Connected")
                bluetoothOutputStream = bluetoothSocket!!.outputStream
                bluetoothInputStream = bluetoothSocket!!.inputStream
                return true
            } catch (e: IOException) {
                Log.e(TAG, "Error during connection (attempt " + (retryCount + 1) + ")", e)
                if (retryCount == maxRetries - 1) {
                    throw Exception(
                        "Unable to connect to " + this.deviceAddress + " after " + maxRetries + " attempts",
                        e
                    )
                } else {
                    // Wait before retrying
                    try {
                        Thread.sleep(retryDelay)
                    } catch (ie: InterruptedException) {
                        Log.e(TAG, "Retry delay interrupted", ie)
                        Thread.currentThread().interrupt()
                        throw Exception("Retry delay interrupted", ie)
                    }
                    retryCount++
                }
            }
        }
        return false // Should never reach here
    }

    private val closeBluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Try closing Bluetooth...")
            close()
        }
    }

    init {
        context.registerReceiver(
            closeBluetoothReceiver,
            IntentFilter(BluetoothEvents.CONNECTION_CLOSE)
        )
    }

    @Throws(Exception::class)
    fun retrieveData() {
        try {
            if (this.open()) {
                Thread.sleep(100)
                this.sendCommand(0xd0.toByte(), "setBacklight")
                Thread.sleep(100)
                this.sendCommand(0xe0.toByte(), "setScreenTimeout")
                Thread.sleep(100)
                this.dataDump()
            }
            Thread.sleep(100)
        } catch (e: Exception) {
            this.close()
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    @Throws(Exception::class)
    private fun findBT() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        bluetoothAdapter.cancelDiscovery()

        if (!bluetoothAdapter.isEnabled) {
            throw Exception("Bluetooth not enabled")
        }

        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(this.deviceAddress)
        Log.d(TAG, "Bluetooth Device Found: " + bluetoothDevice.address)
        this.bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(
            UUID_SERIAL_PORT_SERVICE
        )
    }

    private fun sendCommand(command: Byte, commandName: String) {
        try {
            Log.d(TAG, commandName)
            bluetoothOutputStream!!.write(command.toInt())
        } catch (e: IOException) {
            Log.e(TAG, "Error in $commandName", e)
        }
    }

    private fun close() {
        stopWorker = true

        // Chiudi l'OutputStream
        if (bluetoothOutputStream != null) {
            try {
                bluetoothOutputStream!!.close()
                Log.d(TAG, "OutputStream closed successfully")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing OutputStream: " + e.message)
            } finally {
                bluetoothOutputStream = null
            }
        }

        // Chiudi l'InputStream
        if (bluetoothInputStream != null) {
            try {
                bluetoothInputStream!!.close()
                Log.d(TAG, "InputStream closed successfully")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing InputStream: " + e.message)
            } finally {
                bluetoothInputStream = null
            }
        }

        // Chiudi il Socket
        if (bluetoothSocket != null) {
            if (bluetoothSocket!!.isConnected) {
                try {
                    bluetoothSocket!!.close()
                    Log.d(TAG, "Socket closed successfully")
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing socket: " + e.message)
                }
            } else {
                Log.d(TAG, "Socket is already closed or not connected")
            }
            bluetoothSocket = null
        }

        // Unregister receiver
        try {
            context.unregisterReceiver(closeBluetoothReceiver)
            Log.d(TAG, "Receiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver not registered: " + e.message)
        }

        Log.d(TAG, "Bluetooth Closed!")
    }

    private fun dataDump() {
        try {
            Log.d(TAG, "Requesting Bluetooth data...")
            this.sendCommand(0xf0.toByte(), "dataDump")
            this.listen()
        } catch (e: Exception) {
            Log.e(TAG, "Error in dataDump", e)
        }
    }

    private fun listen() {
        readBufferPosition = 0
        readBuffer = ByteArray(BUFFER_LENGTH)
        stopWorker = false

        val workerThread = Thread {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                try {
                    val bytesAvailable = bluetoothInputStream!!.available()
                    if (bytesAvailable > 0) {
                        val packetBytes = ByteArray(bytesAvailable)
                        var length: Int
                        while ((bluetoothInputStream!!.read(packetBytes)
                                .also { length = it }) != -1
                        ) {
                            System.arraycopy(packetBytes, 0, readBuffer, readBufferPosition, length)
                            readBufferPosition += length
                            if (readBufferPosition >= BUFFER_LENGTH) {
                                processBuffer()
                                break
                            }
                        }
                    }
                } catch (ex: IOException) {
                    Log.e(TAG, "Error while listening", ex)
                    stopWorker = true
                }
            }
        }

        workerThread.start()
    }

    private fun processBuffer() {
        try {
            val deviceInfo = DeviceInfo(deviceAddress, readBuffer)
            Log.d(TAG, "Device: " + deviceInfo.address)
            Log.d(TAG, deviceInfo.volt.toString() + " Volt")
            Log.d(TAG, deviceInfo.amp.toString() + " A")
            Log.d(TAG, deviceInfo.getmW().toString() + " mW")
            Log.d(TAG, deviceInfo.tempC.toString() + "°C")
            Log.d(TAG, deviceInfo.tempF.toString() + "°F")

            val now = now()
            val intentBt = Intent(BluetoothEvents.DATA_RETRIEVED)
            intentBt.putExtra("device", deviceInfo.address)
            intentBt.putExtra("volt", Objects.requireNonNull(deviceInfo.volt).toString())
            intentBt.putExtra("data", now)
            intentBt.putExtra("tempC", Objects.requireNonNull(deviceInfo.tempC).toString())
            intentBt.putExtra("tempF", Objects.requireNonNull(deviceInfo.tempF).toString())
            intentBt.putExtra("amp", Objects.requireNonNull(deviceInfo.amp).toString())
            intentBt.putExtra(
                "message",
                deviceInfo.address + " " + deviceInfo.volt.toString() + "v " + deviceInfo.tempC.toString() + "°"
            )
            context.sendBroadcast(intentBt)

            context.sendBroadcast(Intent(BluetoothEvents.CONNECTION_CLOSE))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing buffer", e)
        }
    }


    companion object {
        val TAG: String = BluetoothClient::class.toString()
        private val UUID_SERIAL_PORT_SERVICE: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val BUFFER_LENGTH = 130
    }
}