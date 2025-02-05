package app.simplecloud.updater.config

import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import kotlin.io.path.exists

@ConfigSerializable
data class ReposiliteConfig(
    val groupId: String,
    val files: List<ReposiliteFileConfig>,
) {

    companion object {
        /**
         * Loads ReposiliteConfig from the specified path.
         *
         * @param path The path to the configuration file
         * @return The loaded ReposiliteConfig
         * @throws IllegalArgumentException if the file doesn't exist or can't be loaded
         */
        fun load(path: Path): ReposiliteConfig {
            require(path.exists()) { "Configuration file does not exist: $path" }

            return YamlConfigurationLoader.builder()
                .path(path)
                .defaultOptions { options ->
                    options.serializers { builder ->
                        builder.registerAnnotatedObjects(objectMapperFactory())
                    }
                }
                .nodeStyle(NodeStyle.BLOCK)
                .build()
                .load()
                .get<ReposiliteConfig>() ?: throw IllegalArgumentException("Failed to load configuration from $path")
        }
    }
}
