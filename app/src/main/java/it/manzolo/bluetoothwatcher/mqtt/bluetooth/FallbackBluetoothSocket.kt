package it.manzolo.bluetoothwatcher.mqtt.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal interface BluetoothSocketWrapper {
    @get:Throws(IOException::class)
    val inputStream: InputStream

    @get:Throws(IOException::class)
    val outputStream: OutputStream
    val remoteDeviceName: String?

    @Throws(IOException::class)
    fun connect()
    val remoteDeviceAddress: String?

    @Throws(IOException::class)
    fun close()
    val underlyingSocket: BluetoothSocket
}

class FallbackBluetoothSocket(fallbackBluetoothSocket: BluetoothSocket) :
    NativeBluetoothSocket(fallbackBluetoothSocket) {
    private var fallbackSocket: BluetoothSocket? = null

    @get:Throws(IOException::class)
    override val inputStream: InputStream
        get() = fallbackSocket!!.inputStream

    @get:Throws(IOException::class)
    override val outputStream: OutputStream
        get() = fallbackSocket!!.outputStream

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    override fun connect() {
        fallbackSocket!!.connect()
    }

    @Throws(IOException::class)
    override fun close() {
        fallbackSocket!!.close()
    }

    init {
        fallbackSocket = try {
            val fallbackBluetoothSocketClass: Class<*> =
                fallbackBluetoothSocket.remoteDevice.javaClass
            val fallbackBluetoothSocketParamTypes = arrayOf<Class<*>>(Integer.TYPE)
            val m = fallbackBluetoothSocketClass.getMethod(
                "createRfcommSocket",
                *fallbackBluetoothSocketParamTypes
            )
            val params = arrayOf<Any>(1)
            m.invoke(fallbackBluetoothSocket.remoteDevice, *params) as BluetoothSocket
        } catch (e: Exception) {
            throw FallbackException(e)
        }
    }
}

open class NativeBluetoothSocket(override val underlyingSocket: BluetoothSocket) :
    BluetoothSocketWrapper {

    @get:Throws(IOException::class)
    override val inputStream: InputStream
        get() = underlyingSocket.inputStream

    @get:Throws(IOException::class)
    override val outputStream: OutputStream
        get() = underlyingSocket.outputStream
    override val remoteDeviceName: String?
        @SuppressLint("MissingPermission")
        get() = underlyingSocket.remoteDevice.name

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    override fun connect() {
        underlyingSocket.connect()
    }

    override val remoteDeviceAddress: String?
        get() = underlyingSocket.remoteDevice.address

    @Throws(IOException::class)
    override fun close() {
        underlyingSocket.close()
    }
}

internal class FallbackException(e: Exception?) : Exception(e) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}