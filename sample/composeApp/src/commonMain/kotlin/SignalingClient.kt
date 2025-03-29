import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 摄像头作为服务端：
 *
 * 摄像头启动后加入房间 roomName，等待客户端连接。
 * 客户端加入 roomName，信令服务器通知双方彼此存在。
 * 客户端通过 peerId 发送Offer，摄像头回复Answer，建立WebRTC连接。
 *
 *
 * 为什么不是直接拨号？
 *      直接拨号需要预先知道对方的唯一标识（如ID、IP），且信令服务器需维护复杂的会话状态。
 *      房间模型通过逻辑分组解耦设备发现和信令交换，更适合动态或未知对等端的场景（如物联网设备）。
 *
 */
class SignalingClient(socketUrl: String) {
    private var socket: BoschSocket
    var isLeftRoom: Boolean = false

    // 保存当前连接的peerId,当前应该为摄像头的peerId。 通过这个peerId来给摄像头发送offer，摄像头收到answer，建立连接。
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

        const val SOCKET_URL = "https://47.99.135.85:8186"
        const val ROOM_NAME = "A01001JOY00000002"
    }


    init {
        socket = BoschSocket(socketUrl)
        setupSocketEvents()
    }

    private fun setupSocketEvents() {
        socket.on("connect") {
            Logger.d("Socket connected，连接信令服务器成功 ${socket.id()}")
        }

        socket.on(EVENT_MESSAGE) { args ->
            Logger.d("5. 接收到信令服务器的回调消息 EVENT_MESSAGE:${args[0]},${args[2]}")
            if (args[2] as String == EVENT_GOT_USER_MEDIA) {
                Logger.d("Socket on message ${args[0] as String}")
            } else if (args[2] as String == "bye") {
                Logger.d("Bye Socket on message ${args[0] as String}")
            } else {
                val message = Json.parseToJsonElement(args[2] as String).jsonObject
                Logger.d("6. Socket on message ${message["type"]}")
                // 关键修改：使用 jsonPrimitive.content 获取原始字符串
                val messageType = message["type"]?.jsonPrimitive?.content
                when (messageType) {

                    // 下面是信令服务器发送的信令， 接收者收到offer、answer、candidate的回调
                    MESSAGE_TYPE_OFFER -> {
                        Logger.d("7. 对方call我，我这边answer。【信令服务器先主动把摄像头信息给我了】。Socket on message offer:${message["type"]?.toString()}")
                        signalingListener?.onOfferReceived(message)
                    }

                    MESSAGE_TYPE_ANSWER -> {
                        Logger.e("！！！这里不应该发生，回复对方。这里是answer，我收到answer，应该要回复offer，但是这里没有做任何处理")
                        signalingListener?.onAnswerReceived(message)
                    }

                    MESSAGE_TYPE_CANDIDATE -> {
                        Logger.d("收到远端 onIceCandidateReceived candidate:${message["type"]?.toString()} , ${message["candidate"]?.toString()}")
                        signalingListener?.onIceCandidateReceived(message)
                    }

                    else -> {
                        Logger.e("没处理的回调 Socket on message ${message["type"]?.toString()}")
                    }
                }
            }
        }

        socket.on(EVENT_ROOM_JOINED) { args ->
            logger.i { "joined消息回调了 $args" }
            // 第一个参数是roomName
            roomName = args[0] as String
            // 第二个参数是socketId
            val socketId = args[1] as String
            // 其他用户id
            val otherIds = args[2] as ArrayList<Any>
            val myId = socket.id()
            //自己加入房间
            if (socketId == myId) {
                //检查摄像头是否在线
                if (!otherIds.isEmpty()) {
                    // 这里其实可以指定好摄像头的id，但是id可能不固定。
                    val otherIds1 = otherIds[0] as ArrayList<Any>
                    if (!otherIds1.isEmpty()) {
                        if (otherIds1[1] == "1") {
                            peerId = otherIds1[0] as String
                            Logger.d("3. Room joined 1 接下来让摄像头call我，建立视频会话。:roomName:$roomName socketId：$socketId myId:$myId otherIds:$otherIds peerId:$peerId")
                            signalingListener?.onRemoteConnected()
                        } else {
                            logger.i { "这个不是摄像头，是手机 ${otherIds[1]}" }
                        }
                    } else {
                        Logger.e("3. 1房间 $roomName 摄像头不在线 otherIds $otherIds")
                    }
                } else {
                    Logger.d("3. 2房间 $roomName 摄像头不在线 otherIds $otherIds")
                }
            } else {
                //摄像头端上线
                peerId = socketId
                //可以请求offer
                Logger.d("Room joined 2:roomName:$roomName socketId：$socketId myId:$myId otherIds:$otherIds")
                Logger.d("其他信息通知 $args")
                signalingListener?.onRemoteConnected()
            }


        }

        socket.on(EVENT_LEAVE_ROOM) { args ->
            val roomName = args[0] as String
            val id = args[1] as String
            Logger.d("Room left:roomName:$roomName id:$id")
        }

        socket.on("disconnect") {
            signalingListener?.onDisconnected()
        }

        socket.on("connect_error") { args ->
            val error = args[0] as? Exception
            signalingListener?.onConnectionError(error?.message ?: "Connection error")
        }
    }

    fun sendOffer(offer: JsonObject) {
        sendMessage(offer)
    }


    /**
     * 信令服务器监听成功的回调
     */
    fun setSignalingListener(listener: SignalingListener) {
        this.signalingListener = listener
    }

    fun disconnect() {
        roomName?.let { leaveRoom(it) }
        socket.disconnect()
        Logger.d("断开连接")
    }

    fun joinRoom(roomName: String) {
        Logger.d("2. 开始加入房间 ： $roomName")
        if (isLeftRoom) {
            this.signalingListener?.onRejoin()
        }
        // 向信令服务器发送加入房间请求，等待信令服务器返回房间信息。参考  socket.on(EVENT_MESSAGE) 和具体调用到 fun setSignalingListener(listener: SignalingListener)
        socket.emit(EVENT_JOIN_ROOM, roomName)
        isLeftRoom = false
    }

    fun leaveRoom(roomName: String) {
        Logger.d("离开房间 ： $roomName")
        socket.emit(EVENT_LEAVE_ROOM, roomName)
        this.signalingListener?.onDisconnected()
        isLeftRoom = true
    }

    fun sendMessage(message: JsonObject) {
        Logger.d("sendMessage:peerId:$peerId message: $message")
        socket.emit(EVENT_MESSAGE, roomName, peerId, message)
    }

    fun sendMessage(message: String) {
        Logger.d("sendMessage:peerId:$peerId message: $message")
        socket.emit(EVENT_MESSAGE, roomName, peerId, message)
    }


    interface SignalingListener {
        fun onRemoteConnected()
        fun onRejoin()
        fun onOfferReceived(offer: JsonObject)
        fun onAnswerReceived(answer: JsonObject)
        fun onIceCandidateReceived(candidate: JsonObject)
        fun onDisconnected()
        fun onError(errorMessage: String)
        fun onConnectionError(errorMessage: String)
    }
}
