package com.example.macro.editor

import com.example.macro.model.EventType
import com.example.macro.model.MacroEvent
import com.example.macro.model.sortedByTimestamp
import kotlin.math.hypot

sealed class DerivedGesture {
    abstract val rawEventIds: List<String>
    abstract val startNs: Long
    abstract val endNs: Long

    data class Tap(override val rawEventIds: List<String>, override val startNs: Long, override val endNs: Long, val x: Float, val y: Float) : DerivedGesture()
    data class LongPress(override val rawEventIds: List<String>, override val startNs: Long, override val endNs: Long, val x: Float, val y: Float) : DerivedGesture()
    data class Swipe(override val rawEventIds: List<String>, override val startNs: Long, override val endNs: Long, val startX: Float, val startY: Float, val endX: Float, val endY: Float) : DerivedGesture()
}

/**
 * Groups raw per-pointer DOWN..UP runs into a friendlier label. This is
 * metadata only — the underlying MacroEvent list is never modified.
 */
object GestureAnalyzer {

    private const val TAP_MOVE_THRESHOLD_PX = 18f
    private const val LONG_PRESS_NS = 500_000_000L // 500ms

    fun analyze(events: List<MacroEvent>): List<DerivedGesture> {
        val sorted = events.sortedByTimestamp()
        val byPointer = sorted.groupBy { it.pointers.firstOrNull()?.pointerId ?: 0 }
        val result = mutableListOf<DerivedGesture>()

        for ((_, pointerEvents) in byPointer) {
            var runStart: MacroEvent? = null
            val run = mutableListOf<MacroEvent>()

            fun flush() {
                val down = runStart ?: return
                if (run.isEmpty()) return
                val up = run.lastOrNull { it.type == EventType.TOUCH_UP } ?: run.last()
                val ids = run.map { it.id }
                val duration = up.timestampNs - down.timestampNs
                val startP = down.pointers.firstOrNull()
                val endP = up.pointers.firstOrNull() ?: startP
                if (startP != null && endP != null) {
                    val dist = hypot((endP.x - startP.x).toDouble(), (endP.y - startP.y).toDouble())
                    result += when {
                        dist > TAP_MOVE_THRESHOLD_PX ->
                            DerivedGesture.Swipe(ids, down.timestampNs, up.timestampNs, startP.x, startP.y, endP.x, endP.y)
                        duration > LONG_PRESS_NS ->
                            DerivedGesture.LongPress(ids, down.timestampNs, up.timestampNs, startP.x, startP.y)
                        else ->
                            DerivedGesture.Tap(ids, down.timestampNs, up.timestampNs, startP.x, startP.y)
                    }
                }
                runStart = null
                run.clear()
            }

            for (e in pointerEvents) {
                when (e.type) {
                    EventType.TOUCH_DOWN -> { flush(); runStart = e; run += e }
                    EventType.TOUCH_MOVE -> if (runStart != null) run += e
                    EventType.TOUCH_UP, EventType.TOUCH_CANCEL -> { if (runStart != null) { run += e; flush() } }
                    else -> {}
                }
            }
            flush()
        }
        return result.sortedBy { it.startNs }
    }
}
