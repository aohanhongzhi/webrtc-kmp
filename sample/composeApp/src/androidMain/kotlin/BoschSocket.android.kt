import co.touchlab.kermit.Logger
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

actual class BoschSocket actual constructor(private val socketUrl: String) {

    private lateinit var socket: Socket

    init {
        try {
            socket = createInsecureSocket(socketUrl)
            setupSocketEvents()
            socket.connect()
        } catch (e: Exception) {
            Logger.e("$e")
        }
    }

    fun createInsecureSocket(url: String): Socket {
        // 创建一个不验证证书的TrustManager
        val trustAllCerts = arrayOf<_root_ide_package_.javax.net.ssl.TrustManager>(object : _root_ide_package_.javax.net.ssl.X509TrustManager {

            override fun checkClientTrusted(
                chain: Array<out java.security.cert.X509Certificate>?, authType: String?
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out java.security.cert.X509Certificate>?, authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<out java.security.cert.X509Certificate> =
                emptyArray()

        })
        // 创建一个不验证主机名的HostnameVerifier

        val allHostsValid = HostnameVerifier { hostname: String, session: SSLSession? -> true }
        // 使用不安全的TrustManager和HostnameVerifier创建一个SSLContext
        val sslContext = _root_ide_package_.javax.net.ssl.SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        // 配置OkHttpClient以使用上述的SSLContext和HostnameVerifier
        val client = _root_ide_package_.okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as _root_ide_package_.javax.net.ssl.X509TrustManager)
            .hostnameVerifier(allHostsValid).build()/*配置忽略https证书 结束*/
        val opt = IO.Options()
        opt.forceNew = true
        opt.reconnection = true
        opt.callFactory = client
        opt.webSocketFactory = client

        return IO.socket(url, opt)

    }


    private fun setupSocketEvents() {
        socket.on(Socket.EVENT_CONNECT) {
            Logger.d("Socket connected，连接信令服务器成功")
        }
    }

    actual fun id(): String {
        return socket.id()
    }

    actual fun on(event: String, callback: (args: Array<Any?>) -> Unit) {
//        socket.on(event, callback)
        socket.on(event) { args ->
            // 将 org.json.JSONArray 转换为 List<Any?>
            val convertedArgs = args.map { arg ->
                when (arg) {
                    is JSONArray -> convertJsonArrayToList(arg)
                    else -> arg
                }
            }.toTypedArray() // List 转 Array
            callback(convertedArgs)
        }
    }

    // 转换 JSONArray 到 List<Any?>
    private fun convertJsonArrayToList(jsonArray: JSONArray): List<Any?> {
        return List(jsonArray.length()) { index ->
            jsonArray.get(index)?.let { value ->
                if (value is JSONArray) convertJsonArrayToList(value) else value
            }
        }
    }

    actual fun connect() {
        socket.connect()
    }

    actual fun disconnect() {
        socket.disconnect()
    }

    actual fun emit(event: String, vararg args: Any?) {
        socket.emit(event, args)
    }

}
