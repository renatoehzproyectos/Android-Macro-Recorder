package com.example.macro.model

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatMode { ONCE, COUNT, INFINITE }

@Serializable
data class PlaybackSettings(
    val repeatMode: RepeatMode = RepeatMode.ONCE,
    val repeatCount: Int = 1,
    val delayBetweenLoopsNs: Long = 0,
    val speedMultiplier: Float = 1.0f
)

@Serializable
data class DeviceInfo(
    val width: Int = 0,
    val height: Int = 0,
    val density: Float = 1.0f,
    val orientation: String = "portrait"
)

@Serializable
data class Macro(
    val id: String,
    var name: String,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    var modifiedAt: Long = System.currentTimeMillis(),
    val device: DeviceInfo = DeviceInfo(),
    var events: List<MacroEvent> = emptyList(),
    var playback: PlaybackSettings = PlaybackSettings()
) {
    val durationNs: Long
        get() = events.maxOfOrNull { it.timestampNs } ?: 0L
}
