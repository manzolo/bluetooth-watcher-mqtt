package it.manzolo.bluetoothwatcher.mqtt.device

import android.util.Log
import it.manzolo.bluetoothwatcher.mqtt.bluetooth.Struct
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

class DeviceInfo(val address: String, private val data: ByteArray) {
    var volt: Double? = null
    var amp: Double? = null
    private var mW: Double? = null
    var tempF: Int? = null
        private set
    var tempC: Int? = null
        private set

    @Throws(Exception::class)
    private fun load() {
        val header = byteArrayOf(data[0], data[1])
        val struct = Struct()

        val voltsShort: Short
        if (byteArrayOf(0x09.toByte(), 0x63.toByte()).contentEquals(header)) {
            val unpackedVolts = struct.unpack("!H", Arrays.copyOfRange(data, 2, 4))
            voltsShort = unpackedVolts[0].toShort() // Explicitly convert Long to Short
            volt = BigDecimal(voltsShort / 100.0).setScale(2, RoundingMode.HALF_UP).toDouble()
        } else if (byteArrayOf(0x09.toByte(), 0xc9.toByte()).contentEquals(header)) {
            val unpackedVolts = struct.unpack("!H", Arrays.copyOfRange(data, 2, 4))
            voltsShort = unpackedVolts[0].toShort() // Explicitly convert Long to Short
            volt = BigDecimal(voltsShort / 1000.0).setScale(2, RoundingMode.HALF_UP).toDouble()
        } else {
            throw Exception("Unknown header: ${header.contentToString()} for device $address")
        }

        val ampsShort = struct.unpack("!H", Arrays.copyOfRange(data, 4, 6))[0].toShort() // Convert to Short
        val mWsInt = struct.unpack("!I", Arrays.copyOfRange(data, 6, 10))[0].toInt()     // Convert to Int
        val tempCShort = struct.unpack("!H", Arrays.copyOfRange(data, 10, 12))[0].toShort() // Convert to Short
        val tempFShort = struct.unpack("!H", Arrays.copyOfRange(data, 12, 14))[0].toShort() // Convert to Short

        amp = (ampsShort / 1000.0).toString().toDouble()
        tempC = tempCShort.toInt()
        tempF = tempFShort.toInt()
        mW = (mWsInt / 1000.0).toString().toDouble()
    }

    fun getmW(): Double? {
        return mW
    }

    init {
        try {
            load()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data for device $address: ${e.message}")
            volt = null
            amp = null
            mW = null
            tempF = null
            tempC = null
        }
    }

    companion object {
        private const val TAG = "DeviceInfo"
    }
}