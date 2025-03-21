import SignalingClient.Companion.EVENT_GOT_USER_MEDIA
import SignalingClient.Companion.ROOM_NAME
import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.AudioStreamTrack
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.MediaStreamTrackKind
import com.shepeliev.webrtckmp.OfferAnswerOptions
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.VideoStreamTrack
import com.shepeliev.webrtckmp.onIceCandidate
import com.shepeliev.webrtckmp.onTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

suspend fun makeCall(
    peerConnection: PeerConnection,
    signalingClient: SignalingClient,       // 新增信令客户端
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoStreamTrack) -> Unit,
    onRemoteAudioTrack: (AudioStreamTrack) -> Unit = {},
): Nothing = coroutineScope {

    localStream.tracks.forEach { peerConnection.addTrack(it) }

    // 绑定信令监听
    signalingClient.setSignalingListener(object : SignalingClient.SignalingListener {
        // 实现所有回调方法（见上文）
        override fun onRemoteConnected() {
            Logger.d("4. onRemoteConnected 媒体就绪， 通知信令服务器，让摄像头call 我")
            signalingClient.sendMessage(EVENT_GOT_USER_MEDIA) // 通知本地媒体就绪
        }

        override fun onRejoin() {
            TODO("Not yet implemented")
        }

        override fun onOfferReceived(offer: JsonObject) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sdpInfo = offer["sdp"].toString()
                    Logger.d("处理远端 Offer: $sdpInfo")

                    val sdp = SessionDescription(SessionDescriptionType.Offer, sdpInfo)
                    peerConnection.setRemoteDescription(sdp)

                    // 可选：创建 Answer 并发送
                    val answer = peerConnection.createAnswer(OfferAnswerOptions(offerToReceiveVideo = true, offerToReceiveAudio = true))
                    peerConnection.setLocalDescription(answer)
                    signalingClient.sendMessage(answer.toString())

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("处理 Offer 失败: ${e.message}")
                    }
                }
            }
        }

        override fun onAnswerReceived(answer: JsonObject) {
            TODO("Not yet implemented")
        }

        override fun onIceCandidateReceived(candidate: JsonObject) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val iceCandidate = IceCandidate("1", 1, "1")
                    peerConnection.addIceCandidate(iceCandidate)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("处理 Offer 失败: ${e.message}")
                    }
                }
            }
        }

        override fun onDisconnected() {
            TODO("Not yet implemented")
        }

        override fun onError(errorMessage: String) {
            TODO("Not yet implemented")
        }

        override fun onConnectionError(errorMessage: String) {
            TODO("Not yet implemented")
        }
    })

    // 收集并转发 ICE Candidate
    peerConnection.onIceCandidate
        .onEach { candidate -> signalingClient.sendMessage(candidate.toString()) } // 瞎写的
        .launchIn(this)


    // 处理远端轨道
    peerConnection.onTrack
        .map { it.track }
        .filterNotNull()
        .onEach { track ->
            if (track.kind == MediaStreamTrackKind.Video) {
                onRemoteVideoTrack(track as VideoStreamTrack)
            }
        }
        .launchIn(this)

    // 加入房间触发连接
    signalingClient.joinRoom(ROOM_NAME)

    if (false){
        // 断开时清理
        peerConnection.close()
        signalingClient.disconnect()
    }

    awaitCancellation()
}
