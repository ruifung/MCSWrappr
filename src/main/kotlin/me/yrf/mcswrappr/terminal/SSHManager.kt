package me.yrf.mcswrappr.terminal

import me.yrf.mcswrappr.Constants
import me.yrf.mcswrappr.WrapperProperties
import org.apache.sshd.common.Factory
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.Command
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.jline.terminal.Terminal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Paths
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
class SSHManager(private val termManager: TerminalManager, private val props: WrapperProperties) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val sshd = SshServer.setUpDefaultServer()

    @PostConstruct
    fun init() {
        val hostKey = Paths.get(Constants.configDir).resolve("ssh_host.key").toAbsolutePath()
        val authKeys = Paths.get(Constants.configDir).resolve(Constants.sshKeys).toAbsolutePath()

        sshd.port = props.remotePort
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(hostKey.toFile())
        sshd.shellFactory = Factory { RemoteTerminal() }
        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            props.remotePass.isNotEmpty() && username == props.remoteUser && password == props.remotePass
        }
        sshd.publickeyAuthenticator = object : AuthorizedKeysAuthenticator(authKeys.toFile()) {
            override fun isValidUsername(username: String?, session: ServerSession?): Boolean {
                return username?.equals(props.remoteUser) == true
            }
        }
        sshd.start()
    }

    @PreDestroy
    fun onStop() {
        sshd.stop()
    }

    inner class RemoteTerminal : Command {
        private var exitCallback: ExitCallback? = null
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null

        private var term: Terminal? = null

        override fun setExitCallback(callback: ExitCallback?) {
            exitCallback = callback
        }

        override fun setInputStream(`in`: InputStream?) {
            inputStream = `in`
        }

        override fun start(env: Environment?) {
            term = termManager.createRemoteTerminal(inputStream!!, outputStream!!, {
                exitCallback?.onExit(0)
                termManager.removeRemoteTerminal(term!!)
            })
        }

        override fun destroy() {
            termManager.removeRemoteTerminal(term!!)
        }

        //StdErr not used.
        override fun setErrorStream(err: OutputStream?) {}

        override fun setOutputStream(out: OutputStream?) {
            outputStream = out
        }

    }
}