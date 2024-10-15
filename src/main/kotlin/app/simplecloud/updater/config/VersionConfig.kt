package app.simplecloud.updater.config

import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import kotlin.io.path.exists

@ConfigSerializable
data class VersionConfig(
    val channel: String = "",
    val releaseTagRegex: String = ""
) {

    object Loader {

        fun load(path: Path): List<VersionConfig> {
            if (!path.exists()) {
                throw IllegalArgumentException("Versions config file does not exist")
            }

            val loader = YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.FLOW)
                .path(path)
                .defaultOptions {
                    it.serializers { builder ->
                        builder.registerAnnotatedObjects(objectMapperFactory())
                    }
                }
                .build()

            val node = loader.load()
            return node.getList(VersionConfig::class.java) ?: throw IllegalArgumentException("Versions config could not be loaded")
        }

    }

}