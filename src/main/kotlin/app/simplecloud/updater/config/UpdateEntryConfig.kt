package app.simplecloud.updater.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class UpdateEntryConfig(
    val releaseFile: String = "",
    val outputFile: String = ""
)