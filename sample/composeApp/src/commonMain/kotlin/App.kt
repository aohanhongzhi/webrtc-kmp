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
import com.shepeliev.webrtckmp.MediaDevices
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.VideoStreamTrack
import com.shepeliev.webrtckmp.videoTracks
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

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
            mutableStateOf<Pair<PeerConnection, PeerConnection>?>(null)
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
                    StartButton(onClick = {
                        scope.launch {
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

                // 判断是否有连接
                if (peerConnections == null) {
                    // 拨打，建立连接
                    CallButton(
                        onClick = { setPeerConnections(Pair(PeerConnection(), PeerConnection())) },
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
