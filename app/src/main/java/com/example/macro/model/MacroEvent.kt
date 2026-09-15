package com.example.macro.model

import kotlinx.serialization.Serializable

/** Low-level event types. Touch is primary; others are stubs for future backends. */
@Serializable
enum class EventType {
    TOUCH_DOWN, TOUCH_MOVE, TOUCH_UP, TOUCH_CANCEL,
    KEY_DOWN, KEY_UP, BACK, HOME, RECENT_APPS,
    SCREEN_ROTATION, WAIT, CUSTOM
}

@Serializable
data class PointerState(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float? = null,
    val size: Float? = null,
    val toolType: Int? = null
)

/**
 * A single recorded/edited event. `id` is a stable identity independent of
 * timestamp so re-timing never breaks references (undo, selection, clone).
 */
@Serializable
data class MacroEvent(
    val id: String,
    val timestampNs: Long,
    val type: EventType,
    val pointers: List<PointerState> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

fun List<MacroEvent>.sortedByTimestamp(): List<MacroEvent> =
    sortedWith(compareBy({ it.timestampNs }, { it.id }))
