package com.example.macro.editor

import com.example.macro.model.Macro
import com.example.macro.model.MacroEvent
import com.example.macro.model.sortedByTimestamp
import java.util.UUID

/**
 * All mutation goes through here so every op is undoable and the timeline
 * always sees a consistent, sorted event list. This class holds no UI state.
 */
class MacroEditor(initial: Macro) {

    private val undoStack = ArrayDeque<List<MacroEvent>>()
    private val redoStack = ArrayDeque<List<MacroEvent>>()

    var macro: Macro = initial
        private set

    val isDirty: Boolean get() = undoStack.isNotEmpty()

    private fun commit(newEvents: List<MacroEvent>) {
        undoStack.addLast(macro.events)
        redoStack.clear()
        macro = macro.copy(events = newEvents.sortedByTimestamp(), modifiedAt = System.currentTimeMillis())
    }

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(macro.events)
        macro = macro.copy(events = prev)
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(macro.events)
        macro = macro.copy(events = next)
        return true
    }

    // ---- Timestamp editing -------------------------------------------------

    /** Move/re-time a single event. If [ripple] is true, shift every later event by the same delta. */
    fun setTimestamp(eventId: String, newTimestampNs: Long, ripple: Boolean = false) {
        val target = macro.events.find { it.id == eventId } ?: return
        val delta = newTimestampNs - target.timestampNs
        val updated = macro.events.map { e ->
            when {
                e.id == eventId -> e.copy(timestampNs = newTimestampNs.coerceAtLeast(0))
                ripple && e.timestampNs > target.timestampNs -> e.copy(timestampNs = (e.timestampNs + delta).coerceAtLeast(0))
                else -> e
            }
        }
        commit(updated)
    }

    fun setDelayAfterPrevious(eventId: String, delayNs: Long) {
        val sorted = macro.events.sortedByTimestamp()
        val idx = sorted.indexOfFirst { it.id == eventId }
        if (idx <= 0) return
        val prevTs = sorted[idx - 1].timestampNs
        setTimestamp(eventId, prevTs + delayNs)
    }

    // ---- Coordinate / property editing -------------------------------------

    fun setCoordinate(eventId: String, x: Float, y: Float, pointerId: Int = 0) {
        val updated = macro.events.map { e ->
            if (e.id == eventId) {
                e.copy(pointers = e.pointers.map { p -> if (p.pointerId == pointerId) p.copy(x = x, y = y) else p })
            } else e
        }
        commit(updated)
    }

    /** Edit a DOWN/UP touch pair's hold duration by moving the UP event. */
    fun setHoldDuration(downEventId: String, upEventId: String, durationNs: Long) {
        val down = macro.events.find { it.id == downEventId } ?: return
        val updated = macro.events.map { e ->
            if (e.id == upEventId) e.copy(timestampNs = (down.timestampNs + durationNs).coerceAtLeast(down.timestampNs)) else e
        }
        commit(updated)
    }

    // ---- Clone --------------------------------------------------------------

    fun cloneEvent(eventId: String, offsetNs: Long): String? {
        val src = macro.events.find { it.id == eventId } ?: return null
        val newId = UUID.randomUUID().toString()
        val clone = src.copy(id = newId, timestampNs = (src.timestampNs + offsetNs).coerceAtLeast(0))
        commit(macro.events + clone)
        return newId
    }

    /** Clone a whole selection, preserving the relative timing between them. */
    fun cloneSelection(eventIds: Set<String>, startAtNs: Long): List<String> {
        val selected = macro.events.filter { it.id in eventIds }.sortedByTimestamp()
        if (selected.isEmpty()) return emptyList()
        val baseTs = selected.first().timestampNs
        val newIds = mutableListOf<String>()
        val clones = selected.map { e ->
            val newId = UUID.randomUUID().toString()
            newIds += newId
            e.copy(id = newId, timestampNs = startAtNs + (e.timestampNs - baseTs))
        }
        commit(macro.events + clones)
        return newIds
    }

    // ---- Delete ---------------------------------------------------------------

    fun deleteEvents(eventIds: Set<String>) {
        commit(macro.events.filterNot { it.id in eventIds })
    }

    // ---- Bulk operations --------------------------------------------------

    fun offsetTimestamps(eventIds: Set<String>, deltaNs: Long) {
        val updated = macro.events.map { e ->
            if (e.id in eventIds) e.copy(timestampNs = (e.timestampNs + deltaNs).coerceAtLeast(0)) else e
        }
        commit(updated)
    }

    fun scaleTiming(eventIds: Set<String>, factor: Double) {
        val selected = macro.events.filter { it.id in eventIds }
        if (selected.isEmpty()) return
        val anchor = selected.minOf { it.timestampNs }
        val updated = macro.events.map { e ->
            if (e.id in eventIds) {
                val newTs = anchor + ((e.timestampNs - anchor) * factor).toLong()
                e.copy(timestampNs = newTs.coerceAtLeast(0))
            } else e
        }
        commit(updated)
    }

    fun offsetCoordinates(eventIds: Set<String>, dx: Float, dy: Float) {
        val updated = macro.events.map { e ->
            if (e.id in eventIds) e.copy(pointers = e.pointers.map { it.copy(x = it.x + dx, y = it.y + dy) }) else e
        }
        commit(updated)
    }

    fun scaleCoordinates(eventIds: Set<String>, scaleX: Float, scaleY: Float, originX: Float, originY: Float) {
        val updated = macro.events.map { e ->
            if (e.id in eventIds) {
                e.copy(pointers = e.pointers.map { p ->
                    p.copy(
                        x = originX + (p.x - originX) * scaleX,
                        y = originY + (p.y - originY) * scaleY
                    )
                })
            } else e
        }
        commit(updated)
    }

    // ---- Manual insertion ---------------------------------------------------

    fun addEvent(event: MacroEvent) {
        commit(macro.events + event)
    }

    fun replaceMacro(newMacro: Macro) {
        commit(newMacro.events)
        macro = macro.copy(name = newMacro.name, playback = newMacro.playback)
    }

    // ---- Validation -----------------------------------------------------------

    data class ValidationIssue(val message: String, val eventId: String? = null)

    fun validate(): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val downPointers = mutableMapOf<Int, MacroEvent>()
        for (e in macro.events.sortedByTimestamp()) {
            when (e.type) {
                com.example.macro.model.EventType.TOUCH_DOWN -> {
                    val pid = e.pointers.firstOrNull()?.pointerId ?: 0
                    downPointers[pid] = e
                }
                com.example.macro.model.EventType.TOUCH_UP -> {
                    val pid = e.pointers.firstOrNull()?.pointerId ?: 0
                    if (downPointers.remove(pid) == null) {
                        issues += ValidationIssue("Pointer $pid has UP without DOWN", e.id)
                    }
                }
                else -> {}
            }
        }
        downPointers.values.forEach {
            issues += ValidationIssue("Pointer left DOWN with no matching UP", it.id)
        }
        return issues
    }
}
