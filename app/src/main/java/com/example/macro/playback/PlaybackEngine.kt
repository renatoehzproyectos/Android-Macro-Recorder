package com.example.macro.playback

import com.example.macro.model.Macro
import com.example.macro.model.RepeatMode
import com.example.macro.model.sortedByTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.coroutineContext

enum class PlaybackState { IDLE, STARTING, PLAYING, PAUSED, STOPPING, COMPLETED, ERROR }

data class PlaybackProgress(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentIteration: Int = 0,
    val totalIterations: Int = 1, // -1 == infinite
    val elapsedNsInIteration: Long = 0,
    val totalNsInIteration: Long = 0
)

/**
 * Schedules events against absolute target timestamps (playbackStart + event
 * timestamp) rather than chaining sleeps, so small per-event delays don't
 * accumulate into overall drift (spec ยง40-41).
 */
class PlaybackEngine(
    private val backend: PlaybackBackend
) {
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress

    @Volatile private var pauseRequested = false
    @Volatile private var stopRequested = false
    @Volatile private var pointerCurrentlyDown = false

    suspend fun play(macro: Macro) {
        stopRequested = false
        pauseRequested = false
        val events = macro.events.sortedByTimestamp()
        if (events.isEmpty()) {
            _progress.value = PlaybackProgress(state = PlaybackState.COMPLETED)
            return
        }
        val speed = macro.playback.speedMultiplier.takeIf { it > 0f } ?: 1.0f
        val totalIterations = when (macro.playback.repeatMode) {
            RepeatMode.ONCE -> 1
            RepeatMode.COUNT -> macro.playback.repeatCount.coerceAtLeast(1)
            RepeatMode.INFINITE -> -1
        }
        val durationNs = events.last().timestampNs

        _progress.value = PlaybackProgress(PlaybackState.STARTING, 0, totalIterations, 0, durationNs)

        var iteration = 0
        try {
            while (coroutineContext.isActive && !stopRequested && (totalIterations == -1 || iteration < totalIterations)) {
                iteration++
                _progress.value = _progress.value.copy(state = PlaybackState.PLAYING, currentIteration = iteration)
                runSingleIteration(events, speed, durationNs)
                if (stopRequested) break
                if (macro.playback.delayBetweenLoopsNs > 0) {
                    delay((macro.playback.delayBetweenLoopsNs / speed / 1_000_000).toLong())
                }
            }
        } finally {
            if (pointerCurrentlyDown) {
                backend.releaseAllPointers()
                pointerCurrentlyDown = false
            }
            _progress.value = _progress.value.copy(
                state = if (stopRequested) PlaybackState.STOPPING else PlaybackState.COMPLETED
            )
        }
    }

    private suspend fun runSingleIteration(events: List<com.example.macro.model.MacroEvent>, speed: Float, durationNs: Long) {
        val iterationStartMs = System.nanoTime() / 1_000_000
        for (event in events) {
            if (stopRequested) return
            while (pauseRequested && !stopRequested) delay(30)
            if (stopRequested) return

            val targetMs = iterationStartMs + (event.timestampNs / speed / 1_000_000).toLong()
            val nowMs = System.nanoTime() / 1_000_000
            val waitMs = targetMs - nowMs
            if (waitMs > 0) delay(waitMs)

            val ok = backend.inject(event)
            pointerCurrentlyDown = when (event.type) {
                com.example.macro.model.EventType.TOUCH_DOWN -> true
                com.example.macro.model.EventType.TOUCH_UP, com.example.macro.model.EventType.TOUCH_CANCEL -> false
                else -> pointerCurrentlyDown
            }
            if (!ok) {
                _progress.value = _progress.value.copy(state = PlaybackState.ERROR)
            }
            _progress.value = _progress.value.copy(elapsedNsInIteration = event.timestampNs, totalNsInIteration = durationNs)
        }
    }

    fun pause() { pauseRequested = true; _progress.value = _progress.value.copy(state = PlaybackState.PAUSED) }
    fun resume() { pauseRequested = false; _progress.value = _progress.value.copy(state = PlaybackState.PLAYING) }
    fun stop() { stopRequested = true; pauseRequested = false }
}
