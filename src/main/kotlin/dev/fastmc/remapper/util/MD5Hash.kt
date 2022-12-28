package dev.fastmc.remapper.util

import java.io.File
import java.security.MessageDigest

data class MD5Hash(val lower: Long, val higher: Long) {
    override fun toString(): String {
        return lower.toString(16) + higher.toString(16)
    }

    companion object {
        fun fromString(string: String): MD5Hash {
            val lower = string.toHexLong(0)
            val higher = string.toHexLong(16)
            return MD5Hash(lower, higher)
        }

        fun fromBytes(bytes: ByteArray): MD5Hash {
            val lower = bytes.toHexLong(0)
            val higher = bytes.toHexLong(8)
            return MD5Hash(lower, higher)
        }

        fun fromFile(file: File): MD5Hash {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(4096)
                var read = input.read(buffer)
                while (read != -1) {
                    md.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return fromBytes(md.digest())
        }
    }
}
