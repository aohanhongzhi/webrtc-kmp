import SignalingClient.Companion.EVENT_GOT_USER_MEDIA
import SignalingClient.Companion.MESSAGE_TYPE_CANDIDATE
import SignalingClient.Companion.ROOM_NAME
import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.AudioStreamTrack
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.IceConnectionState
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.MediaStreamTrackKind
import com.shepeliev.webrtckmp.OfferAnswerOptions
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.VideoStreamTrack
import com.shepeliev.webrtckmp.onIceCandidate
import com.shepeliev.webrtckmp.onIceConnectionStateChange
import com.shepeliev.webrtckmp.onTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

suspend fun makeCall(
    peerConnection: PeerConnection,
    signalingClient: SignalingClient,       // 新增信令客户端
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoStreamTrack) -> Unit,
    onRemoteAudioTrack: (AudioStreamTrack) -> Unit = {},
): Nothing = coroutineScope {

    localStream.tracks.forEach { peerConnection.addTrack(it) }

    peerConnection.onIceConnectionStateChange
        .onEach { state ->
            if (state == IceConnectionState.Failed) {
                logger.e { "---------- ICE Connection State: $state" }
            } else if (state == IceConnectionState.Connected) {
                logger.i { "---------- ICE 连接成功 ICE Connection State: $state" }
                // 监听远端视频轨道
                peerConnection.onTrack
                    .filterNotNull()
                    .map { it.receiver.track as? VideoStreamTrack }
                    .filterNotNull()
            } else if (state == IceConnectionState.Checking) {
                logger.i { "----------检测中 ICE Connection State: $state" }
            } else if (state == IceConnectionState.Closed) {
                logger.w { "----------关闭啦 ICE Connection State: $state" }
            } else {
                logger.e { "----------未知情况 ICE Connection State: $state" }

            }
        }
        .launchIn(this)

    // 绑定信令监听
    signalingClient.setSignalingListener(object : SignalingClient.SignalingListener {
        // 实现所有回调方法（见上文）
        override fun onRemoteConnected() {
            Logger.d("4. onRemoteConnected 媒体就绪， 通知信令服务器，让摄像头call 我")
            signalingClient.sendMessage(EVENT_GOT_USER_MEDIA) // 通知本地媒体就绪
        }

        override fun onRejoin() {
            TODO("onRejoin Not yet implemented")
        }

        override fun onOfferReceived(offer: JsonObject) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sdpInfo = offer["sdp"]?.jsonPrimitive?.content
                    Logger.d("8. onOfferReceived 处理远端 Offer，设置远程 SDP 并创建 Answer ")
                    if (sdpInfo != null) {
                        val sdp = SessionDescription(SessionDescriptionType.Offer, sdpInfo)
                        peerConnection.setRemoteDescription(sdp)

                        Logger.d("9. 对方call我成功, 发起应答。")
                        // 可选：创建 Answer 并发送
                        val answer = peerConnection.createAnswer(OfferAnswerOptions(offerToReceiveVideo = true, offerToReceiveAudio = true))
                        peerConnection.setLocalDescription(answer)

                        val answerBack = JsonObject(
                            mapOf(
                                "type" to JsonPrimitive(answer.type.name.lowercase()),
                                "sdp" to JsonPrimitive(answer.sdp)
                            )
                        )
                        signalingClient.sendMessage(answerBack)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("处理 Offer 失败: ${e.message}")
                    }
                }
            }
        }

        override fun onAnswerReceived(answer: JsonObject) {
            TODO("onAnswerReceived Not yet implemented")
        }

        override fun onIceCandidateReceived(candidate: JsonObject) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sdpMid = candidate["id"]?.jsonPrimitive?.content
                    val label = candidate["label"].toString().toIntOrNull()
                    val candidate1 = candidate["candidate"]?.jsonPrimitive?.content

                    if (sdpMid != null && candidate1 != null) {
                        val iceCandidate = IceCandidate(sdpMid, label ?: 0, candidate1)
//                        Logger.d("onIceCandidateReceived:" + iceCandidate)
                        logger.i("onIceCandidateReceived 准备添加远端 ICE 候选 $iceCandidate")

                        if (peerConnection.remoteDescription == null) {
                            logger.i { "onIceCandidateReceived 添加远端 ICE 候选失败，远程 SDP 为空" }
                        } else {
                            val addIceCandidate = peerConnection.addIceCandidate(iceCandidate)
                            if (!addIceCandidate) {
                                logger.e { "onIceCandidateReceived 添加远端 ICE 候选失败" }
                            } else {
                                logger.i { "onIceCandidateReceived 添加远端 ICE 候选成功" }
                            }
                        }
                    } else {
                        logger.e { "onIceCandidateReceived 添加远端 ICE 候选失败，参数为空" }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("处理 Offer 失败: ${e.message}")
                        onError("Error processing ice candidate: ${e.message}")
                    }
                }
            }
        }

        override fun onDisconnected() {
            releaseResources()
        }

        override fun onError(errorMessage: String) {
            logger.e { "信令服务器报错信息 $errorMessage" }
        }

        override fun onConnectionError(errorMessage: String) {
            logger.e { "信令服务器连接过程中报错信息 $errorMessage" }
        }

        /**
         * 释放 PeerConnection 和音频资源，避免内存泄漏。
         */
        fun releaseResources() {
            Logger.e("releaseResources")
            peerConnection.close()
//        PeerConnectionFactory.stopInternalTracingCapture()
//        PeerConnectionFactory.shutdownInternalTracer()
        }

    })

    // 收集并转发 ICE Candidate
    peerConnection.onIceCandidate.onEach { candidate ->
        run {
            logger.i { "WebRTC的 peerConnection onIceCandidate  => $candidate" }

            val candidateInfo = JsonObject(
                mapOf(
                    "type" to JsonPrimitive(MESSAGE_TYPE_CANDIDATE),
                    "candidate" to JsonPrimitive(candidate.candidate),
                    "label" to JsonPrimitive(candidate.sdpMLineIndex),
                    "id" to JsonPrimitive(candidate.sdpMid)
                )
            )

            signalingClient.sendMessage(candidateInfo)
        }
    }.launchIn(this)


    // 处理远端轨道
    peerConnection.onTrack
        .map { it.track }
        .filterNotNull()
        .onEach { track ->
            logger.i { "Received track: ${track.kind}, ${track.id}" }
            if (track.kind == MediaStreamTrackKind.Video) {
                logger.i { "Received video track: ${track.id}" }
                onRemoteVideoTrack(track as VideoStreamTrack)
            } else if (track.kind == MediaStreamTrackKind.Audio) {
                logger.i { "Received audio track: ${track.id}" }
                onRemoteAudioTrack(track as AudioStreamTrack)
            } else {
                logger.e { "未知轨道类型 ${track.kind}" }
            }
        }
        .launchIn(this)

    // 加入房间触发连接
    signalingClient.joinRoom(ROOM_NAME)

    if (false) {
        // 断开时清理
        peerConnection.close()
        signalingClient.disconnect()
    }

    awaitCancellation()
}
