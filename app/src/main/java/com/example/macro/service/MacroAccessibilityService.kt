package com.example.macro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.example.macro.model.EventType
import com.example.macro.model.MacroEvent

/**
 * Provides system-wide PLAYBACK only, via the public GestureDescription /
 * dispatchGesture API. This is the legitimate, documented way to inject
 * gestures into other apps on stock Android; it is not a raw input stream
 * and is intentionally not used to claim raw system-wide *recording*.
 */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MacroAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /**
     * Dispatches a single-point down/move/up as its own short gesture. For
     * real fluid swipes, callers should batch DOWN..MOVE*..UP into one
     * GestureDescription (see dispatchStroke) for accurate path + duration.
     */
    fun dispatchStroke(points: List<Triple<Float, Float, Long>>, onDone: (Boolean) -> Unit) {
        if (points.size < 2) {
            onDone(false)
            return
        }
        val path = Path()
        path.moveTo(points.first().first, points.first().second)
        for (p in points.drop(1)) path.lineTo(p.first, p.second)

        val startTime = points.first().third
        val duration = (points.last().third - startTime).coerceAtLeast(1L)

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { onDone(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { onDone(false) }
        }, null)
    }

    fun tapAt(x: Float, y: Float, durationMs: Long, onDone: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { onDone(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { onDone(false) }
        }, null)
    }
}
