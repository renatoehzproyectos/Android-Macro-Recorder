package com.example.macro.playback

import com.example.macro.model.EventType
import com.example.macro.model.MacroEvent
import com.example.macro.service.MacroAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Buffers each pointer's DOWN..MOVE*..UP run and dispatches it as a single
 * GestureDescription stroke through the Accessibility Service, which is the
 * only public API for injecting gestures into other apps. Individual MOVE
 * events therefore don't inject anything on their own -- the whole stroke
 * fires when the matching UP arrives, using the recorded relative timing to
 * build the path.
 */
class AccessibilityPlaybackBackend : PlaybackBackend {

    private val buffers = mutableMapOf<Int, MutableList<Triple<Float, Float, Long>>>()
    private var anyPointerDown = false

    override suspend fun inject(event: MacroEvent): Boolean {
        val service = MacroAccessibilityService.instance
            ?: return false // Accessibility Service not enabled by the user.
        val pointer = event.pointers.firstOrNull() ?: return true
        val pid = pointer.pointerId

        return when (event.type) {
            EventType.TOUCH_DOWN -> {
                buffers[pid] = mutableListOf(Triple(pointer.x, pointer.y, event.timestampNs))
                anyPointerDown = true
                true
            }
            EventType.TOUCH_MOVE -> {
                buffers[pid]?.add(Triple(pointer.x, pointer.y, event.timestampNs))
                true
            }
            EventType.TOUCH_UP, EventType.TOUCH_CANCEL -> {
                val points = buffers.remove(pid) ?: return true
                points.add(Triple(pointer.x, pointer.y, event.timestampNs))
                anyPointerDown = buffers.isNotEmpty()
                suspendCancellableCoroutine { cont ->
                    service.dispatchStroke(points) { ok -> if (cont.isActive) cont.resume(ok) }
                }
            }
            else -> true
        }
    }

    override suspend fun releaseAllPointers() {
        // GestureDescription strokes are atomic and short-lived; there is no
        // "held pointer" to leak on the Accessibility API, but clear buffers.
        buffers.clear()
        anyPointerDown = false
    }
}
