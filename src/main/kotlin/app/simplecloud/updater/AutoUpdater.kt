package app.simplecloud.updater

import app.simplecloud.updater.config.ApplicationConfig
import app.simplecloud.updater.config.UpdateEntryConfig
import app.simplecloud.updater.config.VersionConfig
import app.simplecloud.updater.launcher.AutoUpdaterStartCommand
import org.apache.logging.log4j.LogManager
import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.net.URI

class AutoUpdater(
    private val autoUpdaterStartCommand: AutoUpdaterStartCommand
) {

    private val logger = LogManager.getLogger(AutoUpdater::class.java)

    private val applicationConfig = ApplicationConfig.Loader.load(autoUpdaterStartCommand.applicationConfig)
    private val versionConfigs = VersionConfig.Loader.load(autoUpdaterStartCommand.versionsConfig)

    private val selectedChannelVersionConfig = versionConfigs.find { it.channel == autoUpdaterStartCommand.channel }
        ?: throw IllegalArgumentException("Version config for channel ${autoUpdaterStartCommand.channel} not found")

    private val currentVersion = getLastUpdatedVersion()

    fun start() {
        logger.info("Starting AutoUpdater...")
        logger.info("Found ${selectedChannelVersionConfig.channel} version channel")

        val github = connectToGitHub()
        val versionToRelease = getSortedReleasesForCurrentChannel(github)
        if (versionToRelease.isEmpty()) {
            logger.info("No releases found")
            return
        }

        checkAvailableUpdates(versionToRelease)
    }

    private fun connectToGitHub(): GitHub {
        logger.info("Connecting to GitHub...")
        return if (applicationConfig.githubToken != null) {
            logger.info("Using GitHub token from application config")
            GitHubBuilder().withOAuthToken(applicationConfig.githubToken).build()
        } else if (System.getenv("SC_GITHUB_TOKEN") != null) {
            logger.info("Using GitHub token from environment variable SC_GITHUB_TOKEN")
            GitHubBuilder().withOAuthToken(System.getenv("SC_GITHUB_TOKEN")).build()
        } else {
            logger.info("Using anonymous GitHub connection")
            GitHub.connectAnonymously()
        }
    }

    /**
     * Returns a list of releases for the current channel sorted by version (latest first).
     */
    private fun getSortedReleasesForCurrentChannel(github: GitHub): Map<Version, GHRelease> {
        logger.info("Loading releases for ${applicationConfig.githubRepository}...")
        return github.getRepository(applicationConfig.githubRepository)
            .listReleases()
            .mapNotNull {
                val version = Regex(selectedChannelVersionConfig.releaseTagRegex).find(it.tagName)?.groupValues
                    ?: return@mapNotNull null
                Version(version[1].toInt(), version[2].toInt(), version[3].toInt()) to it
            }
            .filter { (version) ->
                currentVersion.isZero()
                        || (autoUpdaterStartCommand.allowMajorUpdates || version.major == currentVersion.major)
            }
            .sortedByDescending { it.first }
            .toMap()
    }

    private fun checkAvailableUpdates(
        versionToRelease: Map<Version, GHRelease>
    ) {
        val latestVersion = versionToRelease.keys.first()
        if (latestVersion <= currentVersion) {
            logger.info("No new version available")
            return
        }

        logger.info("New version available: $latestVersion (Current: ${currentVersion})")
        downloadNewVersion(latestVersion, versionToRelease[latestVersion]!!)
    }

    private fun downloadNewVersion(
        version: Version,
        release: GHRelease
    ) {
        logger.info("Downloading new version $version...")
        val assets = release.listAssets().toList()

        applicationConfig.files.forEach { updateEntryConfig ->
            downloadNewVersion(version, assets, updateEntryConfig)
        }

        logger.info("Successfully downloaded and installed new version $version")
    }

    private fun downloadNewVersion(
        version: Version,
        assets: List<GHAsset>,
        updateEntryConfig: UpdateEntryConfig
    ) {
        val asset = assets.firstOrNull { it.name == updateEntryConfig.releaseFile }
            ?: throw IllegalArgumentException("Release file not found")

        val file = File(updateEntryConfig.outputFile)
        logger.info("Downloading to ${file.absolutePath}")
        downloadAsset(asset, file)

        saveLastUpdatedVersion(version)
    }

    private fun downloadAsset(asset: GHAsset, outputFile: File) {
        val connection =
            URI("https://api.github.com/repos/${applicationConfig.githubRepository}/releases/assets/${asset.id}")
                .toURL()
                .openConnection()

        connection.setRequestProperty("Accept", "application/octet-stream")

        if (applicationConfig.githubToken != null || System.getenv("SC_GITHUB_TOKEN") != null) {
            connection.setRequestProperty("Authorization", "Bearer ${applicationConfig.githubToken ?: System.getenv("SC_GITHUB_TOKEN")}")
        }

        outputFile.delete()
        outputFile.parentFile?.mkdirs()
        outputFile.createNewFile()

        connection.inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun getLastUpdatedVersion(): Version {
        val lastVersionFile = File(autoUpdaterStartCommand.currentVersionFile.toString())
        if (!lastVersionFile.exists()) {
            val version = Version(0, 0, 0)
            saveLastUpdatedVersion(version)
            return version
        }

        return lastVersionFile.readText().let {
            val version = it.split(".").map { it.toInt() }
            Version(version[0], version[1], version[2])
        }
    }

    private fun saveLastUpdatedVersion(version: Version) {
        val lastVersionFile = File(autoUpdaterStartCommand.currentVersionFile.toString())
        lastVersionFile.writeText("$version")
    }

}