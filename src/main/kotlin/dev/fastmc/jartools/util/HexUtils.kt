package dev.fastmc.jartools.util

private val hexChars = "0123456789ABCDEF".toCharArray()

fun Appendable.appendHexInt(value: Int): Appendable {
    append(hexChars[(value ushr 28) and 0xF])
    append(hexChars[(value ushr 24) and 0xF])
    append(hexChars[(value ushr 20) and 0xF])
    append(hexChars[(value ushr 16) and 0xF])
    append(hexChars[(value ushr 12) and 0xF])
    append(hexChars[(value ushr 8) and 0xF])
    append(hexChars[(value ushr 4) and 0xF])
    append(hexChars[value and 0xF])
    return this
}

fun Appendable.appendHexLong(value: Long): Appendable {
    append(hexChars[(value ushr 60).toInt() and 0xF])
    append(hexChars[(value ushr 56).toInt() and 0xF])
    append(hexChars[(value ushr 52).toInt() and 0xF])
    append(hexChars[(value ushr 48).toInt() and 0xF])
    append(hexChars[(value ushr 44).toInt() and 0xF])
    append(hexChars[(value ushr 40).toInt() and 0xF])
    append(hexChars[(value ushr 36).toInt() and 0xF])
    append(hexChars[(value ushr 32).toInt() and 0xF])
    append(hexChars[(value ushr 28).toInt() and 0xF])
    append(hexChars[(value ushr 24).toInt() and 0xF])
    append(hexChars[(value ushr 20).toInt() and 0xF])
    append(hexChars[(value ushr 16).toInt() and 0xF])
    append(hexChars[(value ushr 12).toInt() and 0xF])
    append(hexChars[(value ushr 8).toInt() and 0xF])
    append(hexChars[(value ushr 4).toInt() and 0xF])
    append(hexChars[value.toInt() and 0xF])
    return this
}

fun Int.toHexString(): String {
    return buildString(8) {
        appendHexInt(this@toHexString)
    }
}

fun Long.toHexString(): String {
    return buildString(16) {
        appendHexLong(this@toHexString)
    }
}

private val hexValues = IntArray(128).apply {
    for (i in 0..9) {
        this[i + '0'.code] = i
    }
    for (i in 0..5) {
        this[i + 'A'.code] = i + 10
        this[i + 'a'.code] = i + 10
    }
}

fun String.toHexInt(index: Int = 0): Int {
    var value = 0
    value = value or (hexValues[this[0 + index].code and 0x7F] shl 28)
    value = value or (hexValues[this[1 + index].code and 0x7F] shl 24)
    value = value or (hexValues[this[2 + index].code and 0x7F] shl 20)
    value = value or (hexValues[this[3 + index].code and 0x7F] shl 16)
    value = value or (hexValues[this[4 + index].code and 0x7F] shl 12)
    value = value or (hexValues[this[5 + index].code and 0x7F] shl 8)
    value = value or (hexValues[this[6 + index].code and 0x7F] shl 4)
    value = value or hexValues[this[7 + index].code and 0x7F]
    return value
}

fun String.toHexLong(index: Int = 0): Long {
    var value = 0L
    value = value or (hexValues[this[0 + index].code and 0x7F].toLong() shl 60)
    value = value or (hexValues[this[1 + index].code and 0x7F].toLong() shl 56)
    value = value or (hexValues[this[2 + index].code and 0x7F].toLong() shl 52)
    value = value or (hexValues[this[3 + index].code and 0x7F].toLong() shl 48)
    value = value or (hexValues[this[4 + index].code and 0x7F].toLong() shl 44)
    value = value or (hexValues[this[5 + index].code and 0x7F].toLong() shl 40)
    value = value or (hexValues[this[6 + index].code and 0x7F].toLong() shl 36)
    value = value or (hexValues[this[7 + index].code and 0x7F].toLong() shl 32)
    value = value or (hexValues[this[8 + index].code and 0x7F].toLong() shl 28)
    value = value or (hexValues[this[9 + index].code and 0x7F].toLong() shl 24)
    value = value or (hexValues[this[10 + index].code and 0x7F].toLong() shl 20)
    value = value or (hexValues[this[11 + index].code and 0x7F].toLong() shl 16)
    value = value or (hexValues[this[12 + index].code and 0x7F].toLong() shl 12)
    value = value or (hexValues[this[13 + index].code and 0x7F].toLong() shl 8)
    value = value or (hexValues[this[14 + index].code and 0x7F].toLong() shl 4)
    value = value or hexValues[this[15 + index].code and 0x7F].toLong()
    return value
}

fun ByteArray.toHexInt(index: Int = 0): Int {
    var value = 0
    value = value or (this[0 + index].toInt() shl 24)
    value = value or (this[1 + index].toInt() shl 16)
    value = value or (this[2 + index].toInt() shl 8)
    value = value or this[3 + index].toInt()
    return value
}

fun ByteArray.toHexLong(index: Int = 0): Long {
    var value = 0L
    value = value or (this[0 + index].toLong() shl 56)
    value = value or (this[1 + index].toLong() shl 48)
    value = value or (this[2 + index].toLong() shl 40)
    value = value or (this[3 + index].toLong() shl 32)
    value = value or (this[4 + index].toLong() shl 24)
    value = value or (this[5 + index].toLong() shl 16)
    value = value or (this[6 + index].toLong() shl 8)
    value = value or this[7 + index].toLong()
    return value
}

fun ByteArray.toHexString(): String {
    return toHexString(0, size)
}

fun ByteArray.toHexString(index: Int, length: Int): String {
    return buildString(length * 2) {
        for (i in 0 until length) {
            val b = this@toHexString[index + i]
            append(hexChars[(b.toInt() ushr 4) and 0xF])
            append(hexChars[b.toInt() and 0xF])
        }
    }
}