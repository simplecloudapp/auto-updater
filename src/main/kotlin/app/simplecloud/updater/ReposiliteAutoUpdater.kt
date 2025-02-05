package app.simplecloud.updater

import app.simplecloud.updater.config.ReposiliteConfig
import app.simplecloud.updater.config.ReposiliteFileConfig
import app.simplecloud.updater.exception.ForbiddenException
import app.simplecloud.updater.exception.NotFoundException
import app.simplecloud.updater.exception.UnauthorizedException
import app.simplecloud.updater.launcher.AutoUpdaterStartCommand
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.logging.log4j.LogManager
import org.w3c.dom.Document
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Downloads artifacts from a Reposilite Maven repository.
 *
 * This class handles downloading artifacts and retrieving version information from
 * a Reposilite Maven repository. It supports automatic version resolution using maven-metadata.xml
 * and provides safe file downloading with proper error handling.
 */
class ReposiliteAutoUpdater(
    private val startCommand: AutoUpdaterStartCommand
) {

    private val logger = LogManager.getLogger(ReposiliteAutoUpdater::class.java)

    private val config = ReposiliteConfig.load(startCommand.applicationConfig)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .build()
    }

    fun start() {
        logger.info("Starting AutoUpdater for GroupID: ${config.groupId}...")

        if (config.files.isEmpty()) {
            throw IllegalStateException("No FileConfig found. Aborting AutoUpdater...")
        }

        val currentVersion = runCatching {
            Files.readString(startCommand.currentVersionFile)
        }.getOrDefault("").trim()

        var latestVersion: String? = null

        config.files.forEach { fileConfig ->
            latestVersion = latestVersion ?: getLatestVersion(fileConfig.artifact).getOrElse { error ->
                logger.error("Failed to fetch latest version for artifact '${fileConfig.artifact}': ${error.message}")
                return@forEach
            }

            if (latestVersion == currentVersion) {
                logger.info("${config.groupId} is up-to-date ($currentVersion)")
                return@forEach
            }

            logger.info("Found update for ${config.groupId}, updating to version $latestVersion...")

            val version = latestVersion ?: return@forEach
            downloadArtifact(version, fileConfig).onSuccess {
                runCatching {
                    Files.writeString(
                        startCommand.currentVersionFile,
                        version,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                }.onFailure { error ->
                    logger.error(
                        "Failed to update version file '${startCommand.currentVersionFile}': ${error.message}",
                        error
                    )
                }
            }.onFailure { error ->
                logger.error("Download failed for artifact '${fileConfig.artifact}': ${error.message}", error)
            }
        }
    }

    /**
     * Retrieves the latest version available in the repository.
     *
     * @return A [Result] containing the version string
     */
    private fun getLatestVersion(artifactId: String): Result<String> = runCatching {
        val metadataPath = buildMetadataPath(artifactId)

        executeRequest(metadataPath, ::parseVersionFromResponse)
    }

    private fun parseVersionFromResponse(response: Response): String {
        val xml = response.body?.string() ?: throw RuntimeException("Empty metadata response")

        return parseVersionFromMetadata(xml)
    }

    private fun downloadArtifact(version: String, config: ReposiliteFileConfig): Result<File> = runCatching {
        val artifactPath = buildArtifactPath(version, config)
        val destinationFile = File(config.outputFile)
        destinationFile.parentFile?.mkdirs()

        val tempFile = File(
            destinationFile.parentFile,
            destinationFile.name + ".tmp"
        )

        try {
            executeRequest(artifactPath) { response ->
                saveResponseToFile(response, tempFile)
            }

            if (destinationFile.exists()) {
                if (!destinationFile.delete()) {
                    throw RuntimeException("Failed to delete existing file: ${destinationFile.absolutePath}")
                }
            }

            if (!tempFile.renameTo(destinationFile)) {
                throw RuntimeException("Failed to move temp file to destination: ${destinationFile.absolutePath}")
            }

            destinationFile
        } catch (exception: Exception) {
            tempFile.delete()
            throw exception
        }
    }

    private fun saveResponseToFile(response: Response, file: File): File {
        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw RuntimeException("Empty response body")

        return file
    }

    private fun <T> executeRequest(path: String, handler: (Response) -> T): T {
        val request = buildRequest(path)

        return client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> handler(response)
                401 -> throw UnauthorizedException("Authentication failed")
                403 -> throw ForbiddenException("Access denied")
                404 -> throw NotFoundException("Artifact not found")
                else -> throw RuntimeException("Unexpected response ${response.code}: ${response.message}")
            }
        }
    }

    private fun parseVersionFromMetadata(xml: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream())

        return findVersionInXml(document) ?: throw RuntimeException("Couldn't find version information in metadata")
    }

    private fun findVersionInXml(document: Document): String? =
        document.getElementsByTagName("latest").item(0)?.textContent

    private fun buildMetadataPath(artifactId: String): String =
        buildPath(artifactId, "maven-metadata.xml")

    private fun buildArtifactPath(version: String, config: ReposiliteFileConfig): String =
        buildPath(config.artifact, "$version/${config.getReleaseFile(version)}")

    private fun buildPath(artifactId: String, suffix: String): String =
        "${config.groupId.replace('.', '/')}/${artifactId}/$suffix"

    private fun buildRequest(path: String): Request =
        Request.Builder()
            .url("https://repo.simplecloud.app/${startCommand.channel}/$path")
            .build()

}