package com.shepeliev.webrtckmp

data class SessionDescription(val type: SessionDescriptionType, val sdp: String)

// TODO 不知道这里大小写是否影响处理
enum class SessionDescriptionType { Offer, Pranswer, Answer, Rollback }

fun SessionDescriptionType.toCanonicalString(): String = name.lowercase()
