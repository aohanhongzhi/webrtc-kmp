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
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.IceServer
import com.shepeliev.webrtckmp.MediaDevices
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.VideoStreamTrack
import com.shepeliev.webrtckmp.onIceCandidate
import com.shepeliev.webrtckmp.videoTracks
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview


const val SOCKET_URL = "wss://47.99.135.85:8186"
const val DEVICE_ID = "device_${(0..1000).random()}"

@Composable
@Preview
fun App() {
    val httpClient = HttpClient {
        install(WebSockets)
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    LaunchedEffect(Unit) {
        Logger.setLogWriters(platformLogWriter())
        
        // 连接信令服务器
        httpClient.webSocket(SOCKET_URL) {
            send("REGISTER|$DEVICE_ID")
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    handleSignalingMessage(
                        message = message,
                        localPeer = peerConnections?.get("local"),
                        remotePeer = peerConnections?.get("remote")
                    )
                }
            }
        }
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
            mutableStateOf<MutableMap<String, PeerConnection>?>(null)
        }
        val (localDeviceId, _) = remember { mutableStateOf("device1") }
        val (remoteDeviceId, _) = remember { mutableStateOf("device2") }

        LaunchedEffect(localStream, peerConnections) {
            if (peerConnections == null || localStream == null) return@LaunchedEffect
            // 初始化双向连接
            val localPeer = peerConnections["local"]!!
            val remotePeer = peerConnections["remote"]!!

            // 添加本地媒体流到本地PeerConnection
            localPeer.addStream(localStream)

            // 本地创建offer
            localPeer.createOffer().then { offer ->
                localPeer.setLocalDescription(offer)
                // 通过网络传输offer到远程设备

                // 远程设备收到offer后设置并创建answer
                remotePeer.setRemoteDescription(offer)
                remotePeer.createAnswer().then { answer ->
                    remotePeer.setLocalDescription(answer)
                    // 通过网络传输answer到本地设备

                    // 本地设备设置远程answer
                    localPeer.setRemoteDescription(answer)
                }
            }

            // 处理远程媒体流
            remotePeer.onAddStream = { stream ->
                setRemoteVideoTrack(stream.videoTracks.firstOrNull())
                setRemoteAudioTrack(stream.audioTracks.firstOrNull())
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            // 本地视频显示区域
            Column(Modifier.weight(1f)) {
                localStream?.videoTracks?.firstOrNull()?.let { track ->
                    Video(
                        videoTrack = track,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Local Camera")
                }
            }

            // 远程视频显示区域
            Column(Modifier.weight(1f)) {
                remoteVideoTrack?.let { track ->
                    Video(
                        videoTrack = track,
                        audioTrack = remoteAudioTrack,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Remote Camera")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (localStream == null) {
                StartButton(onClick = {
                    scope.launch {
                        val stream = MediaDevices.getUserMedia(audio = true, video = true)
                        setLocalStream(stream)
                    }
                })
            } else {
                StopButton(
                    onClick = {
                        peerConnections?.values?.forEach { it.close() }
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

            // 判断是否有连接
            if (peerConnections == null) {
                // 拨打，建立连接
                CallButton(
                    onClick = {
                        // 设置ICE服务器
                        val config = RtcConfiguration(
                            iceServers = listOf(
                                IceServer(urls = listOf("stun:stun.l.google.com:19302"))
                            )
                        )
                        val localPeer = PeerConnection(config).apply {
                            onIceCandidate = { candidate ->
                                httpClient.webSocket(SOCKET_URL) {
                                    send("CANDIDATE|${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}")
                                }
                            }
                            onAddStream = { stream ->
                                setRemoteVideoTrack(stream.videoTracks.firstOrNull())
                                setRemoteAudioTrack(stream.audioTracks.firstOrNull())
                            }
                        }

                        val remotePeer = PeerConnection(config).apply {
                            onIceCandidate = { candidate ->
                                httpClient.webSocket(SOCKET_URL) {
                                    send("CANDIDATE|${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}")
                                }
                            }
                        }

                        setPeerConnections(
                            mutableMapOf(
                                "local" to localPeer,
                                "remote" to remotePeer
                            )
                        )
                    },
                )
            } else {
                //挂断，断开连接
                HangupButton(onClick = {
                    hangup(peerConnections)
                    setPeerConnections(null)
                    setRemoteVideoTrack(null)
                    setRemoteAudioTrack(null)
                })
            }
        }
    }
}


private fun handleSignalingMessage(
    message: String,
    localPeer: PeerConnection?,
    remotePeer: PeerConnection?
) {
    val parts = message.split("|")
    when (parts[0]) {
        "OFFER" -> {
            val sdp = SessionDescription(SessionDescriptionType.Offer, parts[1])
            remotePeer?.setRemoteDescription(sdp)
            remotePeer?.createAnswer()?.then { answer ->
                remotePeer.setLocalDescription(answer)
                // 发送answer回对方设备
            }
        }

        "ANSWER" -> {
            val sdp = SessionDescription(SessionDescriptionType.Answer, parts[1])
            localPeer?.setRemoteDescription(sdp)
        }

        "CANDIDATE" -> {
            val candidate = IceCandidate(
                sdpMid = parts[1],
                sdpMLineIndex = parts[2].toInt(),
                sdp = parts[3]
            )
            localPeer?.addIceCandidate(candidate)
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
