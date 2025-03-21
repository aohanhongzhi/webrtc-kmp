import com.shepeliev.webrtckmp.PeerConnection

fun hangup(peerConnections:PeerConnection?) {
    val pc1 = peerConnections ?: return
    pc1.getTransceivers().forEach { pc1.removeTrack(it.sender) }
    pc1.close()
}
