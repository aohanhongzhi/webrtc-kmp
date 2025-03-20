import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SignalingClient(private val socketUrl: String) {
    private lateinit var client: HttpClient
    private lateinit var socket: DefaultWebSocketSession
    var isLeftRoom: Boolean = false

    // 保存当前连接的peerId,当前应该为摄像头的peerId
    private var peerId: String? = null

    // 保存当前连接的roomName,当前应该为摄像头的roomName
    private var roomName: String? = null
    private var signalingListener: SignalingListener? = null

    companion object {
        const val EVENT_JOIN_ROOM = "join"
        const val EVENT_ROOM_JOINED = "joined"
        const val EVENT_LEAVE_ROOM = "leave"
        const val EVENT_MESSAGE = "message"
        const val EVENT_GOT_USER_MEDIA = "got user media"
        const val MESSAGE_TYPE_OFFER = "offer"
        const val MESSAGE_TYPE_ANSWER = "answer"
        const val MESSAGE_TYPE_CANDIDATE = "candidate"
    }

    init {
        try {

            CoroutineScope(Dispatchers.Default).launch {
                client = createUnsafeHttpClient()
                connect()
            }
        } catch (e: Exception) {
            signalingListener?.onConnectionError(e.message ?: "URI Syntax Exception")
        }
    }

    private suspend fun connect() {
        try {
            client.webSocket(
                method = HttpMethod.Get,
                host = URLBuilder(socketUrl).host,
                port = URLBuilder(socketUrl).port,
                path = URLBuilder(socketUrl).encodedPath
            ) {
                socket = this
                setupSocketEvents()
            }
        } catch (e: Exception) {
            Logger.e { "${e}"  }
            signalingListener?.onConnectionError(e.message ?: "Connection error")
        }
    }

    private fun setupSocketEvents() {
        GlobalScope.launch {
            try {
                for (frame in socket.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        Logger.d("Received message: $text")
                        val jsonObject = Json.decodeFromString<JsonObject>(text)
                        when (jsonObject["event"]?.jsonPrimitive?.content) {
                            EVENT_MESSAGE -> {
                                val message = jsonObject["data"] as JsonObject
                                Logger.d("Socket on message ${message["type"]?.jsonPrimitive?.content}")
                                when (message["type"]?.jsonPrimitive?.content) {
                                    MESSAGE_TYPE_OFFER -> {
                                        signalingListener?.onOfferReceived(message)
                                    }

                                    MESSAGE_TYPE_ANSWER -> {
                                        signalingListener?.onAnswerReceived(message)
                                    }

                                    MESSAGE_TYPE_CANDIDATE -> {
                                        signalingListener?.onIceCandidateReceived(message)
                                    }
                                }
                            }

                            EVENT_ROOM_JOINED -> {
                                // 第一个参数是roomName
                                roomName = jsonObject["roomName"]?.jsonPrimitive?.content
                                // 第二个参数是socketId
                                val socketId = jsonObject["socketId"]?.jsonPrimitive?.content
                                // 其他用户id
                                val otherIds = jsonObject["otherIds"] as JsonArray
                                val myId = jsonObject["myId"]?.jsonPrimitive?.content
                                //自己加入房间
                                if (socketId == myId) {
                                    //检查摄像头是否在线
                                    if (otherIds.isNotEmpty()) {
                                        peerId = otherIds[0].jsonPrimitive.content
                                        Logger.d("Room joined:roomName:$roomName socketId：$socketId myId:$myId otherIds:$otherIds")
                                        signalingListener?.onRemoteConnected()
                                    }
                                } else {
                                    //摄像头端上线
                                    peerId = socketId
                                    //可以请求offer
                                    Logger.d("Room joined:roomName:$roomName socketId：$socketId myId:$myId otherIds:$otherIds")
                                    signalingListener?.onRemoteConnected()
                                }
                            }

                            EVENT_LEAVE_ROOM -> {
                                val roomName = jsonObject["roomName"]?.jsonPrimitive?.content
                                val id = jsonObject["id"]?.jsonPrimitive?.content
                                Logger.d("Room left:roomName:$roomName id:$id")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                signalingListener?.onConnectionError(e.message ?: "Connection error")
            } finally {
                signalingListener?.onDisconnected()
            }
        }
    }

    fun sendOffer(offer: JsonObject) {
        sendMessage(offer)
    }

    fun setSignalingListener(listener: SignalingListener) {
        this.signalingListener = listener
    }

    fun disconnect() {
        CoroutineScope(Dispatchers.Default).launch {
            roomName?.let { leaveRoom(it) }
            socket.close()
            Logger.d("断开连接")
        }
    }

    fun joinRoom(roomName: String) {
        Logger.d("开始加入房间 ： $roomName")
        if (isLeftRoom) {
            this.signalingListener?.onRejoin()
        }
        CoroutineScope(Dispatchers.Default).launch {

            val message = buildJsonObject {
                put("event", EVENT_JOIN_ROOM)
                put("data", buildJsonObject {
                    put("roomName", roomName)
                })
            }
            socket.send(Frame.Text(Json.encodeToString(message)))
        }
        isLeftRoom = false
    }

    fun leaveRoom(roomName: String) {
        Logger.d("离开房间 ： $roomName")
        CoroutineScope(Dispatchers.Default).launch {

            val message = buildJsonObject {
                put("event", EVENT_LEAVE_ROOM)
                put("data", buildJsonObject {
                    put("roomName", roomName)
                })
            }
            socket.send(Frame.Text(Json.encodeToString(message)))
        }
        this.signalingListener?.onDisconnected()
        isLeftRoom = true
    }

    fun sendMessage(message: JsonObject) {
        CoroutineScope(Dispatchers.Default).launch {

            val fullMessage = buildJsonObject {
                put("event", EVENT_MESSAGE)
                put("data", buildJsonObject {
                    put("roomName", roomName)
                    put("peerId", peerId)
                    put("message", message)
                })
            }
            socket.send(Frame.Text(Json.encodeToString(fullMessage)))
        }
    }

    fun sendMessage1(message: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val fullMessage = buildJsonObject {
                put("event", EVENT_MESSAGE)
                put("data", buildJsonObject {
                    put("roomName", roomName)
                    put("peerId", peerId)
                    put("message", message)
                })
            }
            socket.send(Frame.Text(Json.encodeToString(fullMessage)))
        }
    }

    interface SignalingListener {
        fun onRemoteConnected()
        fun onRejoin()
        fun onOfferReceived(offer: kotlinx.serialization.json.JsonObject)
        fun onAnswerReceived(answer: kotlinx.serialization.json.JsonObject)
        fun onIceCandidateReceived(candidate: kotlinx.serialization.json.JsonObject)
        fun onDisconnected()
        fun onError(errorMessage: String)
        fun onConnectionError(errorMessage: String)
    }
}
