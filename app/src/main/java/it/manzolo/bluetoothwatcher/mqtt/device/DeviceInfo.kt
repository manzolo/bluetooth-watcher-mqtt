package it.manzolo.bluetoothwatcher.mqtt.device

import it.manzolo.bluetoothwatcher.mqtt.bluetooth.Struct
import java.util.*

class DeviceInfo(val address: String, private val data: ByteArray) {
    var volt: Double? = null
        private set
    var amp: Double? = null
        private set
    private var mW: Double? = null
    var tempF: Int? = null
        private set
    var tempC: Int? = null
        private set

    @Throws(Exception::class)
    private fun load() {
        val struct = Struct()
        val volts = struct.unpack("!H", Arrays.copyOfRange(data, 2, 4))
        val amps = struct.unpack("!H", Arrays.copyOfRange(data, 4, 6))
        val mWs = struct.unpack("!I", Arrays.copyOfRange(data, 6, 10))
        val tempCs = struct.unpack("!H", Arrays.copyOfRange(data, 10, 12))
        val tempFs = struct.unpack("!H", Arrays.copyOfRange(data, 12, 14))
        volt = (volts[0] / 100.0).toString().toDouble()
        amp = (amps[0] / 1000.0).toString().toDouble()
        tempC = tempCs[0].toString().toInt()
        tempF = tempFs[0].toString().toInt()
        mW = (mWs[0] / 1000.0).toString().toDouble()
    }

    fun getmW(): Double? {
        return mW
    }

    init {
        load()
    }
}