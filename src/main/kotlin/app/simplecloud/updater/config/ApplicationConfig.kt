package app.simplecloud.updater.config

import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import kotlin.io.path.exists

@ConfigSerializable
data class ApplicationConfig(
    val githubRepository: String = "",
    val githubToken: String? = null,
    val releaseFile: String = "",
    val outputFile: String = ""
) {

    object Loader {

        fun load(path: Path): ApplicationConfig {
            if (!path.exists()) {
                throw IllegalArgumentException("Application config file does not exist")
            }

            val loader = YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.BLOCK)
                .path(path)
                .defaultOptions {
                    it.serializers { builder ->
                        builder.registerAnnotatedObjects(objectMapperFactory())
                    }
                }
                .build()

            val node = loader.load()
            return node.get() ?: throw IllegalArgumentException("Application config could not be loaded")
        }

    }

}