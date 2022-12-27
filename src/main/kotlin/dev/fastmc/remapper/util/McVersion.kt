package dev.fastmc.remapper.util

@JvmInline
value class McVersion private constructor(private val packed: Int) : Comparable<McVersion> {
    constructor(major: Int, minor: Int, patch: Int) : this((major shl 16) or (minor shl 8) or (patch))

    val one: Int
        get() = packed ushr 16
    val two: Int
        get() = packed ushr 8 and 0xFF
    val three: Int
        get() = packed and 0xFF

    override fun compareTo(other: McVersion): Int {
        var result = one.compareTo(other.one)
        if (result != 0) return result
        result = two.compareTo(other.two)
        if (result != 0) return result
        return three.compareTo(other.three)
    }

    override fun toString(): String {
        return if (three == 0) {
            "$one.$two"
        } else {
            "$one.$two.$three"
        }
    }

    companion object {
        val UNKNOWN = McVersion(0)

        operator fun invoke(version: String): McVersion {
            val split = version.split(".")
            val major = split[0].toInt()
            val minor = split[1].toInt()
            val patch = if (split.size == 3) split[2].toInt() else 0
            return McVersion(major, minor, patch)
        }
    }
}