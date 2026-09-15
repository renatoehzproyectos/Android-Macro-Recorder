package com.example.macro.capture

import android.view.MotionEvent
import com.example.macro.model.EventType
import com.example.macro.model.MacroEvent
import com.example.macro.model.PointerState
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Converts a live stream of Android MotionEvents into the app's normalized
 * MacroEvent model. Uses System.nanoTime() (monotonic) for all timing, never
 * wall-clock. The hot path only copies primitive values into a lock-free
 * queue -- no JSON serialization happens on the input callback.
 */
class InAppRecorder : CaptureBackend {

    override val fidelityDescription = "In-App: Raw MotionEvent (full fidelity)"

    private var listener: CaptureListener? = null
    private var startNanos: Long = 0L
    private var recording = false
    private val queue = ConcurrentLinkedQueue<MacroEvent>()

    var droppedCount: Int = 0
        private set
    var capturedCount: Int = 0
        private set

    override fun start(listener: CaptureListener) {
        this.listener = listener
        startNanos = System.nanoTime()
        recording = true
        droppedCount = 0
        capturedCount = 0
    }

    override fun stop() {
        recording = false
        listener = null
    }

    /** Call from the view's onTouchEvent / pointerInteropFilter. */
    fun onMotionEvent(ev: MotionEvent) {
        if (!recording) return
        val nowNs = System.nanoTime() - startNanos
        val type = when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> EventType.TOUCH_DOWN
            MotionEvent.ACTION_MOVE -> EventType.TOUCH_MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> EventType.TOUCH_UP
            MotionEvent.ACTION_CANCEL -> EventType.TOUCH_CANCEL
            else -> return
        }

        // For POINTER_DOWN/UP, only the affected pointer is reported as a
        // discrete event; for MOVE, report every active pointer at once so
        // multi-touch temporal relationships are preserved (see spec ยง13).
        val pointers: List<PointerState> = if (type == EventType.TOUCH_MOVE) {
            (0 until ev.pointerCount).map { idx -> pointerStateAt(ev, idx) }
        } else {
            val idx = ev.actionIndex
            listOf(pointerStateAt(ev, idx))
        }

        try {
            val event = MacroEvent(
                id = UUID.randomUUID().toString(),
                timestampNs = nowNs,
                type = type,
                pointers = pointers
            )
            queue.add(event)
            capturedCount++
            listener?.onEvent(event)
        } catch (t: Throwable) {
            droppedCount++
        }
    }

    private fun pointerStateAt(ev: MotionEvent, idx: Int): PointerState {
        val id = ev.getPointerId(idx)
        return PointerState(
            pointerId = id,
            x = ev.getX(idx),
            y = ev.getY(idx),
            pressure = runCatching { ev.getPressure(idx) }.getOrNull(),
            size = runCatching { ev.getSize(idx) }.getOrNull(),
            toolType = runCatching { ev.getToolType(idx) }.getOrNull()
        )
    }

    fun drainAll(): List<MacroEvent> {
        val list = mutableListOf<MacroEvent>()
        while (true) list.add(queue.poll() ?: break)
        return list
    }
}
