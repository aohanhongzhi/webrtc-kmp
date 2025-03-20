// androidMain/kotlin/Network.kt
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

actual fun createUnsafeHttpClient(): HttpClient = HttpClient(CIO) {
    engine {
        https {
            trustManager = object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            // 创建并初始化 SSLContext
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), java.security.SecureRandom())
            }

            // 应用配置
            sslContext = sslContext
            hostnameVerifier = HostnameVerifier { _, _ -> true } // 必须关闭主机名验证
        }
    }
    install(Logging) {
        level = LogLevel.ALL // 输出所有日志
    }
    // 安装 WebSocket 插件并配置参数
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE // 最大帧大小（默认无限制）
    }
}
