package com.example.macro.playback

import com.example.macro.model.MacroEvent

/** Replays events into an in-app preview surface (does not touch other apps). */
class InAppPlaybackBackend(
    private val onEvent: (MacroEvent) -> Unit
) : PlaybackBackend {
    override suspend fun inject(event: MacroEvent): Boolean {
        onEvent(event)
        return true
    }

    override suspend fun releaseAllPointers() {
        // Nothing to release for a pure visual preview.
    }
}
