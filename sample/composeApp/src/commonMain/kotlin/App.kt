import SignalingClient.Companion.EVENT_GOT_USER_MEDIA
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.shepeliev.webrtckmp.AudioStreamTrack
import com.shepeliev.webrtckmp.IceServer
import com.shepeliev.webrtckmp.MediaDevices
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.VideoStreamTrack
import com.shepeliev.webrtckmp.videoTracks
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

const val SOCKET_URL = "https://47.99.135.85:8186"

@Composable
@Preview
fun App() {
    LaunchedEffect(Unit) {
        Logger.setLogWriters(platformLogWriter())
    }

    MaterialTheme {
        val scope = rememberCoroutineScope()
//        在 Kotlin 中，解构声明（Destructuring Declaration）允许你将一个对象的属性分解为多个变量。这在处理包含多个属性的对象时非常有用。
        val (localStream, setLocalStream) = remember { mutableStateOf<MediaStream?>(null) }
        val (remoteVideoTrack, setRemoteVideoTrack) = remember {
            mutableStateOf<VideoStreamTrack?>(
                null
            )
        }
        val (remoteAudioTrack, setRemoteAudioTrack) = remember {
            mutableStateOf<AudioStreamTrack?>(
                null
            )
        }
        val (peerConnections, setPeerConnections) = remember {
            mutableStateOf<Pair<PeerConnection, PeerConnection>?>(
                null
            )
        }

        // 创建信令客户端实例
        val signalingClient = remember { SignalingClient(SOCKET_URL) }
        val (roomName, setRoomName) = remember { mutableStateOf<String?>(null) }

        // 设置信令客户端监听器
        LaunchedEffect(signalingClient) {

//            private fun createPeerConnection() {
//                val rtcConfig = RtcConfiguration(
//                    listOf(
//                        PeerConnection.IceServer.builder("stun:47.99.135.85:3478").createIceServer(),
//                    )
//                )
//                peerConnection =
//                    peerConnectionFactory.createPeerConnection(rtcConfig, mediaConstraints, this)
//                        ?: throw IllegalStateException("Failed to create peer connection")
//            }

            signalingClient.setSignalingListener(object : SignalingClient.SignalingListener {
                override fun onRemoteConnected() {
                    // 处理远程连接
                    Logger.i { "处理远程连接" }
                    signalingClient.sendMessage1(EVENT_GOT_USER_MEDIA)
                }

                override fun onRejoin() {
                    // 处理重新加入房间
                    Logger.i { "处理重新加入房间" }
//                    createPeerConnection()
//                    createAudioTrack()
                }

                override fun onOfferReceived(offer: kotlinx.serialization.json.JsonObject) {

                    Logger.i { "onOfferReceived" }

                    scope.launch {
//                        val remotePeerConnection = peerConnections?.second
//                        remotePeerConnection?.setRemoteDescription(offer)
//                        val answer = remotePeerConnection?.createAnswer()
//                        remotePeerConnection?.setLocalDescription(answer)
//                        signalingClient.sendOffer(answer!!)
                    }
                }

                override fun onAnswerReceived(answer: kotlinx.serialization.json.JsonObject) {

                    Logger.i { "onAnswerReceived" }

                    scope.launch {
//                        val localPeerConnection = peerConnections?.first
//                        localPeerConnection?.setRemoteDescription(answer)
                    }
                }

                override fun onIceCandidateReceived(candidate: kotlinx.serialization.json.JsonObject) {

                    Logger.i { "onIceCandidateReceived" }

                    scope.launch {
//                        val sdpMid = candidate.getString("sdpMid")
//                        val sdpMLineIndex = candidate.getInt("sdpMLineIndex")
//                        val iceCandidate = org.webrtc.IceCandidate(sdpMid, sdpMLineIndex, candidate.getString("candidate"))
//                        peerConnections?.first?.addIceCandidate(iceCandidate)
//                        peerConnections?.second?.addIceCandidate(iceCandidate)
                    }
                }

                override fun onDisconnected() {
                    // 处理断开连接
                    Logger.i { "处理断开连接" }
                }

                override fun onError(errorMessage: String) {
                    // 处理错误
                    Logger.i { "处理错误" }
                }

                override fun onConnectionError(errorMessage: String) {
                    // 处理连接错误
                    Logger.i { "处理连接错误" }
                }
            })
        }

        LaunchedEffect(localStream, peerConnections) {
            if (peerConnections == null || localStream == null) return@LaunchedEffect
            makeCall(peerConnections, localStream, setRemoteVideoTrack, setRemoteAudioTrack)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val localVideoTrack = localStream?.videoTracks?.firstOrNull()

            localVideoTrack?.let {
                Video(
                    videoTrack = it,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } ?: Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Local video")
            }

            remoteVideoTrack?.let {
                Video(
                    videoTrack = it, // 这里的it就是指的不为空的remoteVideoTrack
                    audioTrack = remoteAudioTrack,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } ?: Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Remote video")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (localStream == null) {
                    // Start 开始
                    StartButton(onClick = {
                        scope.launch {
//                            准备媒体信息，获取硬件信息，摄像头 麦克风，做好准备
                            val stream = MediaDevices.getUserMedia(audio = true, video = true)
                            setLocalStream(stream)
                        }
                    })
                } else {
                    StopButton(
                        onClick = {
                            hangup(peerConnections)
                            localStream.release()
                            setLocalStream(null)
                            setPeerConnections(null)
                            setRemoteVideoTrack(null)
                            setRemoteAudioTrack(null)
                        }
                    )

                    SwitchCameraButton(
                        onClick = {
                            scope.launch { localStream.videoTracks.firstOrNull()?.switchCamera() }
                        }
                    )
                }

                if (roomName == null) {
                    CallButton(
                        onClick = {
                            val config = RtcConfiguration(
                                iceServers = listOf(
                                    IceServer(urls = listOf("stun:stun.l.google.com:19302"))
                                )
                            )
                            setPeerConnections(Pair(PeerConnection(config), PeerConnection(config)))
                            setRoomName("room1") // 设置房间名称
                            signalingClient.joinRoom("room1")
                        },
                    )
                } else {
                    HangupButton(onClick = {
                        hangup(peerConnections)
                        setPeerConnections(null)
                        setRemoteVideoTrack(null)
                        setRemoteAudioTrack(null)
                        signalingClient.leaveRoom(roomName!!)
                    })
                }
            }
        }
    }
}

@Composable
private fun CallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier = modifier) {
        Text("Call")
    }
}

@Composable
private fun HangupButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier = modifier) {
        Text("Hangup")
    }
}

@Composable
private fun SwitchCameraButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Switch Camera")
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Stop")
    }
}
