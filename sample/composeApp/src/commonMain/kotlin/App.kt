import SignalingClient.Companion.SOCKET_URL
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.shepeliev.webrtckmp.AudioStreamTrack
import com.shepeliev.webrtckmp.MediaDevices
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.IceServer
import com.shepeliev.webrtckmp.VideoStreamTrack
import com.shepeliev.webrtckmp.videoTracks
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview


var logger = Logger.withTag("WebRTCApp")

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
        // 声明可空状态变量
        var peerConnection: PeerConnection? by remember { mutableStateOf(null) }

        LaunchedEffect(localStream, peerConnection) {
            logger.i { "尝试建立webrtc连接？ peerConnections=$peerConnection , localStream=$localStream" }
            val signalingClient = SignalingClient(SOCKET_URL)
            if (peerConnection == null || localStream == null) return@LaunchedEffect
            logger.d("开始建立webrtc连接")

            makeCall(peerConnection!!, signalingClient, localStream, setRemoteVideoTrack, setRemoteAudioTrack)
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

                logger.i { "Rendering remote video track: ${it.id}" }

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
                            hangup(peerConnection)
                            localStream.release()
                            setLocalStream(null)
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
                if (peerConnection == null) {
                    // 拨打，建立连接
                    CallButton(
                        onClick = {
                            // 设置ICE服务器
                            val config = RtcConfiguration(
                                iceServers = listOf(
//                                    IceServer(urls = listOf("stun:47.99.135.85:3478"))
                                    IceServer(urls = listOf("stun:163.228.157.109:5349")),
                                    IceServer(urls = listOf("turn:163.228.157.109:5349"), username = "peer", password = "1Qaz2wSx")
//                                    IceServer(urls = listOf("stun:stun.l.google.com:19302"))
                                )
                            )
                            logger.d("1.  WebRTC 创建连接，这里与信令服务器无关，与STUN服务器有关系")
                            peerConnection = PeerConnection(config)
                        },
                    )
                } else {
                    //挂断，断开连接
                    HangupButton(onClick = {
                        hangup(peerConnection)
                        setRemoteVideoTrack(null)
                        setRemoteAudioTrack(null)
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
