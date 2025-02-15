package app.simplecloud.updater.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class ReposiliteFileConfig(
    val artifact: String,
    val outputFile: String,
) {

    /**
     * Gets the release file name
     *
     * @param version The version
     * @return the release file name
     */
    fun getReleaseFile(version: String): String = "$artifact-$version-all.jar"

}
