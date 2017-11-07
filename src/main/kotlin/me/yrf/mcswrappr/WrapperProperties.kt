package me.yrf.mcswrappr

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Paths

@Component
@ConfigurationProperties("mcswrappr")
class WrapperProperties {
    var jarPath: String = ""
    var jvmArgs: List<String> = ArrayList()
    var jarArgs: List<String> = ArrayList()
    var minMemory: String = ""
    var maxMemory: String = ""
    var javaPath: String = Paths.get(System.getProperty("java.home"))
            .resolve("bin")
            .resolve("java.exe")
            .toString()
        set(value) {if (value.isNotEmpty()) field = value}
    var consoleLineBuffer: Int = 1000
    var remoteUser: String = "admin"
    var remotePass: String = "password"
    var remotePort: Int = 25522
}