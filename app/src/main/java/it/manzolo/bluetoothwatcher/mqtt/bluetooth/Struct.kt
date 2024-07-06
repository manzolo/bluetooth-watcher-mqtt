package it.manzolo.bluetoothwatcher.mqtt.bluetooth

import java.io.ByteArrayInputStream
import java.nio.ByteOrder

class Struct {
    companion object {
        const val BIG_ENDIAN: Short = 0
        const val LITTLE_ENDIAN: Short = 1
    }

    private var byteOrder: Short
    private val nativeByteOrder: Short =
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) LITTLE_ENDIAN else BIG_ENDIAN

    init {
        byteOrder = nativeByteOrder
    }

    private fun ByteArray.reverseBytes(): ByteArray {
        for (i in 0 until this.size / 2) {
            val tmp = this[i]
            this[i] = this[this.size - i - 1]
            this[this.size - i - 1] = tmp
        }
        return this
    }

    private fun unpackRaw16b(value: ByteArray): Long {
        if (byteOrder == LITTLE_ENDIAN) value.reverseBytes()
        var x: Long = (value[0].toInt() shl 8 or (value[1].toInt() and 0xff)).toLong()
        if ((x ushr 15 and 1L) == 1L) {
            x = ((x xor 0x7fffL) and 0x7fffL) + 1 //2's complement 16 bit
            x *= -1
        }
        return x
    }

    private fun unpackRawU16b(value: ByteArray): Long {
        if (byteOrder == LITTLE_ENDIAN) value.reverseBytes()
        return ((value[0].toInt() and 0xff shl 8) or (value[1].toInt() and 0xff)).toLong()
    }

    private fun unpackRaw32b(value: ByteArray): Long {
        if (byteOrder == LITTLE_ENDIAN) value.reverseBytes()
        var x: Long =
            (value[0].toInt() shl 24 or (value[1].toInt() shl 16) or (value[2].toInt() shl 8) or value[3].toInt()).toLong()
        if ((x ushr 31 and 1L) == 1L) {
            x = ((x xor 0x7fffffffL) and 0x7fffffffL) + 1 //2's complement 32 bit
            x *= -1
        }
        return x
    }

    private fun unpackRawU32b(value: ByteArray): Long {
        if (byteOrder == LITTLE_ENDIAN) value.reverseBytes()
        return (value[0].toInt() and 0xff).toLong() shl 24 or
                (value[1].toInt() and 0xff).toLong() shl 16 or
                (value[2].toInt() and 0xff).toLong() shl 8 or
                (value[3].toInt() and 0xff).toLong()
    }

    @Throws(Exception::class)
    fun unpackSingleData(format: Char, value: ByteArray): Long {
        return when (format) {
            'h' -> {
                if (value.size != 2) throw Exception("Byte length mismatch")
                unpackRaw16b(value)
            }

            'H' -> {
                if (value.size != 2) throw Exception("Byte length mismatch")
                unpackRawU16b(value)
            }

            'i' -> {
                if (value.size != 4) throw Exception("Byte length mismatch")
                unpackRaw32b(value)
            }

            'I' -> {
                if (value.size != 4) throw Exception("Byte length mismatch")
                unpackRawU32b(value)
            }

            else -> throw Exception("Invalid format specifier")
        }
    }

    private fun estimateLength(format: String): Int {
        return format.sumBy({
            when (it) {
                'i', 'I' -> 4
                'h', 'H' -> 2
                else -> 0
            }
        })
    }

    @Throws(Exception::class)
    fun unpack(format: String, values: ByteArray): LongArray {
        val len = estimateLength(format)
        if (len != values.size) throw Exception("Format length and values aren't equal")

        val initialChar = format.firstOrNull()
        val result = if (initialChar in listOf('@', '>', '<', '!')) {
            LongArray(format.length - 1)
        } else {
            LongArray(format.length)
        }

        val bShort = ByteArray(2)
        val bLong = ByteArray(4)
        val byteArrayInputStream = ByteArrayInputStream(values)

        var position = 0
        format.forEachIndexed { index, char ->
            if (index == 0 && char in listOf('>', '<', '@', '!')) {
                byteOrder = when (char) {
                    '>' -> BIG_ENDIAN
                    '<' -> LITTLE_ENDIAN
                    '!' -> BIG_ENDIAN
                    else -> nativeByteOrder
                }
            } else {
                when (char) {
                    'h', 'H' -> {
                        byteArrayInputStream.read(bShort)
                        result[position++] = unpackSingleData(char, bShort)
                    }

                    'i', 'I' -> {
                        byteArrayInputStream.read(bLong)
                        result[position++] = unpackSingleData(char, bLong)
                    }
                }
            }
        }
        return result
    }
}