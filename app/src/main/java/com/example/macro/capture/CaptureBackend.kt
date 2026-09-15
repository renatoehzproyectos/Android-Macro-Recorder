package com.example.macro.capture

import com.example.macro.model.MacroEvent

interface CaptureListener {
    fun onEvent(event: MacroEvent)
}

/**
 * Abstraction over how raw input is captured. Mode A (in-app) can see real
 * MotionEvents. Mode B (system-wide) is fundamentally limited by Android:
 * third-party apps cannot read raw touch events from other apps without
 * root. We never claim otherwise (see AccessibilityCaptureBackend).
 */
interface CaptureBackend {
    val fidelityDescription: String
    fun start(listener: CaptureListener)
    fun stop()
}
