package app.simplecloud.updater

import app.simplecloud.updater.config.ApplicationConfig
import app.simplecloud.updater.config.UpdateEntryConfig
import app.simplecloud.updater.config.VersionConfig
import app.simplecloud.updater.launcher.AutoUpdaterStartCommand
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.apache.logging.log4j.LogManager
import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class AutoUpdater(
    private val autoUpdaterStartCommand: AutoUpdaterStartCommand
) {

    private val logger = LogManager.getLogger(AutoUpdater::class.java)

    private val applicationConfig = ApplicationConfig.Loader.load(autoUpdaterStartCommand.applicationConfig)
    private val versionConfigs = VersionConfig.Loader.load(autoUpdaterStartCommand.versionsConfig)

    private val selectedChannelVersionConfig = versionConfigs.find { it.channel == autoUpdaterStartCommand.channel }
        ?: throw IllegalArgumentException("Version config for channel ${autoUpdaterStartCommand.channel} not found")

    private val currentVersion = getLastUpdatedVersion()

    private val githubUrlInterceptor = Interceptor { chain ->
        val original = chain.request()
        val originalUrl = original.url.toString()

        val newUrl = originalUrl.replace(
            "api.github.com",
            "gha.simplecloud.app"
        )

        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()

        chain.proceed(newRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(githubUrlInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
        val builder = GitHubBuilder()
            .withConnector(OkHttpGitHubConnector(okHttpClient))

        return when {
            applicationConfig.githubToken != null -> {
                logger.info("Using GitHub token from application config")
                builder.withOAuthToken(applicationConfig.githubToken).build()
            }

            System.getenv("SC_GITHUB_TOKEN") != null -> {
                logger.info("Using GitHub token from environment variable SC_GITHUB_TOKEN")
                builder.withOAuthToken(System.getenv("SC_GITHUB_TOKEN")).build()
            }

            else -> {
                logger.info("Using anonymous GitHub connection")
                builder.build()
            }
        }
    }

    /**
     * Returns a list of releases for the current channel sorted by version (latest first).
     */
    private fun getSortedReleasesForCurrentChannel(github: GitHub): Map<Version, GHRelease> {
        logger.info("Loading releases for ${applicationConfig.githubRepository}...")
        val repository = github.getRepository(applicationConfig.githubRepository)

        // First try to get the latest release
        try {
            val latestRelease = repository.latestRelease
            val version = Regex(selectedChannelVersionConfig.releaseTagRegex).find(latestRelease.tagName)?.groupValues
            if (version != null) {
                val parsedVersion = Version(version[1].toInt(), version[2].toInt(), version[3].toInt())
                // Check if the version matches our criteria
                if (currentVersion.isZero() ||
                    (autoUpdaterStartCommand.allowMajorUpdates || parsedVersion.major == currentVersion.major)
                ) {
                    logger.info("Found matching latest release: ${latestRelease.tagName}")
                    return mapOf(parsedVersion to latestRelease)
                }
            }
            logger.info("Latest release doesn't match criteria, falling back to release list")
        } catch (e: Exception) {
            logger.info("Failed to get latest release, falling back to release list: ${e.message}")
        }

        // Fallback to listing all releases
        return repository.listReleases()
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
            URI("https://gha.simplecloud.app/repos/${applicationConfig.githubRepository}/releases/assets/${asset.id}")
                .toURL()
                .openConnection()

        connection.setRequestProperty("Accept", "application/octet-stream")

        if (applicationConfig.githubToken != null || System.getenv("SC_GITHUB_TOKEN") != null) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer ${applicationConfig.githubToken ?: System.getenv("SC_GITHUB_TOKEN")}"
            )
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