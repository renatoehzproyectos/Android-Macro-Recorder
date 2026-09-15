package com.example.macro.playback

import com.example.macro.model.MacroEvent

interface PlaybackBackend {
    /** Inject a single event "now". Backend decides how (dispatchGesture, direct dispatch, etc). */
    suspend fun inject(event: MacroEvent): Boolean

    /** Called if playback stops while a pointer is logically down, so the backend can release it safely. */
    suspend fun releaseAllPointers()
}
