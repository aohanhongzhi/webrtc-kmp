@file:OptIn(ExperimentalForeignApi::class)

package com.shepeliev.webrtckmp

import WebRTC.RTCDataBuffer
import WebRTC.RTCDataChannel
import WebRTC.RTCDataChannelDelegateProtocol
import WebRTC.RTCDataChannelState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import platform.darwin.NSObject
import platform.posix.uint64_t

public actual class DataChannel(
    public val ios: RTCDataChannel,
) {
    public actual val label: String get() = ios.label()
    public actual val id: Int get() = ios.channelId

    public actual val readyState: DataChannelState get() =
        rtcDataChannelStateAsCommon(ios.readyState())

    public actual val bufferedAmount: Long get() = ios.bufferedAmount.toLong()

    private val events =
        MutableSharedFlow<DataChannelEvent>(extraBufferCapacity = Channel.UNLIMITED)

    private val delegate = Delegate()

    init {
        ios.delegate = delegate
    }

    public actual val onOpen: Flow<Unit> =
        events
            .filter {
                it is DataChannelEvent.StateChanged &&
                    it.state == RTCDataChannelState.RTCDataChannelStateOpen
            }.map { }

    public actual val onClosing: Flow<Unit> =
        events
            .filter {
                it is DataChannelEvent.StateChanged &&
                    it.state == RTCDataChannelState.RTCDataChannelStateClosing
            }.map { }

    public actual val onClose: Flow<Unit> =
        events
            .filter {
                it is DataChannelEvent.StateChanged &&
                    it.state == RTCDataChannelState.RTCDataChannelStateClosed
            }.map { }

    public actual val onError: Flow<String> = emptyFlow()

    public actual val onMessage: Flow<ByteArray> =
        events
            .map { it as? DataChannelEvent.MessageReceived }
            .filterNotNull()
            .map { it.buffer.data.toByteArray() }

    @BetaInteropApi
    public actual fun send(data: ByteArray): Boolean {
        val buffer = RTCDataBuffer(data.toNSData(), true)
        return ios.sendData(buffer)
    }

    public actual fun close() {
        ios.close()
    }

    private sealed interface DataChannelEvent {
        data class StateChanged(
            val state: RTCDataChannelState,
        ) : DataChannelEvent

        data class MessageReceived(
            val buffer: RTCDataBuffer,
        ) : DataChannelEvent
    }

    private fun rtcDataChannelStateAsCommon(state: RTCDataChannelState): DataChannelState =
        when (state) {
            RTCDataChannelState.RTCDataChannelStateConnecting -> DataChannelState.Connecting
            RTCDataChannelState.RTCDataChannelStateOpen -> DataChannelState.Open
            RTCDataChannelState.RTCDataChannelStateClosing -> DataChannelState.Closing
            RTCDataChannelState.RTCDataChannelStateClosed -> DataChannelState.Closed
            else -> error("Unknown RTCDataChannelState: $state")
        }

    private inner class Delegate :
        NSObject(),
        RTCDataChannelDelegateProtocol {
        override fun dataChannel(
            dataChannel: RTCDataChannel,
            didChangeBufferedAmount: uint64_t,
        ) {
            // not implemented
        }

        override fun dataChannel(
            dataChannel: RTCDataChannel,
            didReceiveMessageWithBuffer: RTCDataBuffer,
        ) {
            check(events.tryEmit(DataChannelEvent.MessageReceived(didReceiveMessageWithBuffer))) {
                // as we use SharedFlow with unlimited buffer, this should never happen
                "Failed to emit MessageReceived event"
            }
        }

        override fun dataChannelDidChangeState(dataChannel: RTCDataChannel) {
            val state = dataChannel.readyState
            check(events.tryEmit(DataChannelEvent.StateChanged(state))) {
                // as we use SharedFlow with unlimited buffer, this should never happen
                "Failed to emit StateChanged event."
            }
            if (state == RTCDataChannelState.RTCDataChannelStateClosed) {
                ios.delegate = null
            }
        }
    }
}
