package app.simplecloud.updater.launcher

import app.simplecloud.updater.ReposiliteAutoUpdater
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path

class AutoUpdaterStartCommand : CliktCommand() {

    val applicationConfig by option("--application-config", help = "Path to application.yml")
        .path(mustExist = true, canBeDir = false)
        .default(Path("application.yml"))

    val currentVersionFile by option("--current-version-file", help = "Path to current_version.txt")
        .path(canBeDir = false)
        .default(Path("current_version.txt"))

    val channel by option("--channel", help = "Update channel")
        .default("release")

    override fun run() {
        ReposiliteAutoUpdater(this).start()
    }

}