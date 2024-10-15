package app.simplecloud.updater

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        return when {
            major != other.major -> major.compareTo(other.major)
            minor != other.minor -> minor.compareTo(other.minor)
            else -> patch.compareTo(other.patch)
        }
    }

    override fun toString(): String = "$major.$minor.$patch"

    fun isZero(): Boolean = major == 0 && minor == 0 && patch == 0

}
