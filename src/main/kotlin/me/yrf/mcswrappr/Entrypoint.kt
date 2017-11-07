package me.yrf.mcswrappr

import org.fusesource.jansi.AnsiConsole
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

fun main(args: Array<String>) {
    AnsiConsole.systemInstall()
    val path = Paths.get(Constants.configDir)
    Files.createDirectories(path)

    val config = path.resolve("${Constants.configName}.yml")
    if (Files.notExists(config))
        Files.copy(Constants.javaClass.classLoader.getResourceAsStream("config.yml.sample"), config)

    val authorizedKeys = path.resolve(Constants.sshKeys)
    if (Files.notExists(authorizedKeys))
        Files.copy(Constants.javaClass.classLoader.getResourceAsStream("authorized_keys.sample"), authorizedKeys)

    val defProps = Properties()
    defProps.setProperty("spring.config.location", "file:./${Constants.configDir}/")
    defProps.setProperty("spring.config.name", Constants.configName)
    defProps.setProperty("spring.main.banner-mode", "off")

    SpringApplicationBuilder(Application::class.java)
            .properties(defProps)
            .main(MCSWrappr.javaClass)
            .run(*args)
}

object Constants {
    val configName = "mcsw-config"
    val configDir = "MCSWrapper"
    val sshKeys = "ssh_authorized_keys"
}

@SpringBootApplication
@EnableScheduling
@EnableAsync
class Application {
    @Bean
    fun scheduledThreadPool(): TaskScheduler {
        return ThreadPoolTaskScheduler()
    }
}

/**
 * Dummy object for anchoring startup messages.
 */
object MCSWrappr