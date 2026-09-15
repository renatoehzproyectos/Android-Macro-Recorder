package com.example.macro.ui

import android.app.Application
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.macro.capture.InAppRecorder
import com.example.macro.editor.DerivedGesture
import com.example.macro.editor.GestureAnalyzer
import com.example.macro.editor.MacroEditor
import com.example.macro.model.Macro
import com.example.macro.model.PlaybackSettings
import com.example.macro.model.RepeatMode
import com.example.macro.playback.AccessibilityPlaybackBackend
import com.example.macro.playback.InAppPlaybackBackend
import com.example.macro.playback.PlaybackEngine
import com.example.macro.playback.PlaybackProgress
import com.example.macro.playback.PlaybackState
import com.example.macro.storage.AutosaveManager
import com.example.macro.storage.MacroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordingState { IDLE, RECORDING }
enum class CapturePreview { NONE }

class MacroViewModel(app: Application) : AndroidViewModel(app) {

    val repository = MacroRepository(app)
    private val autosave = AutosaveManager(viewModelScope, repository)
    val recorder = InAppRecorder()

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _currentEditor = MutableStateFlow<MacroEditor?>(null)
    val currentEditor: StateFlow<MacroEditor?> = _currentEditor.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _derivedGestures = MutableStateFlow<List<DerivedGesture>>(emptyList())
    val derivedGestures: StateFlow<List<DerivedGesture>> = _derivedGestures.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val _previewEvents = MutableStateFlow<List<com.example.macro.model.MacroEvent>>(emptyList())
    val previewEvents: StateFlow<List<com.example.macro.model.MacroEvent>> = _previewEvents.asStateFlow()

    var useSystemWidePlayback = false

    fun refreshMacros() {
        _macros.value = repository.listAll()
    }

    fun createMacro(name: String): Macro {
        val m = repository.createNew(name.ifBlank { "Macro ${( _macros.value.size + 1)}" })
        refreshMacros()
        return m
    }

    fun openMacro(id: String) {
        val macro = repository.load(id) ?: return
        _currentEditor.value = MacroEditor(macro)
        recomputeGestures()
        _selectedIds.value = emptySet()
    }

    fun closeMacro() {
        _currentEditor.value?.macro?.let { repository.save(it) }
        _currentEditor.value = null
    }

    private fun recomputeGestures() {
        _derivedGestures.value = _currentEditor.value?.macro?.events?.let { GestureAnalyzer.analyze(it) } ?: emptyList()
    }

    private fun editOp(block: (MacroEditor) -> Unit) {
        val editor = _currentEditor.value ?: return
        block(editor)
        recomputeGestures()
        autosave.scheduleSave { editor.macro }
        // Force StateFlow emission since MacroEditor mutates internally.
        _currentEditor.value = editor
    }

    // ---- Recording --------------------------------------------------------

    fun startRecording() {
        _recordingState.value = RecordingState.RECORDING
        recorder.start(object : com.example.macro.capture.CaptureListener {
            override fun onEvent(event: com.example.macro.model.MacroEvent) {
                _recordingElapsedMs.value = event.timestampNs / 1_000_000
            }
        })
    }

    fun onMotionEvent(ev: MotionEvent) = recorder.onMotionEvent(ev)

    fun stopRecording() {
        recorder.stop()
        _recordingState.value = RecordingState.IDLE
        val captured = recorder.drainAll()
        val editor = _currentEditor.value ?: return
        editOp { it.addEvent0(captured) }
    }

    // helper to add multiple events as a single undo step
    private fun MacroEditor.addEvent0(events: List<com.example.macro.model.MacroEvent>) {
        if (events.isEmpty()) return
        val merged = macro.events + events
        replaceMacro(macro.copy(events = merged))
    }

    // ---- Editing passthrough ------------------------------------------------

    fun toggleSelect(id: String, exclusive: Boolean = false) {
        _selectedIds.value = if (exclusive) setOf(id) else {
            val s = _selectedIds.value.toMutableSet()
            if (!s.add(id)) s.remove(id)
            s
        }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun setTimestamp(id: String, ns: Long, ripple: Boolean) = editOp { it.setTimestamp(id, ns, ripple) }
    fun setCoordinate(id: String, x: Float, y: Float) = editOp { it.setCoordinate(id, x, y) }
    fun setHold(downId: String, upId: String, durationNs: Long) = editOp { it.setHoldDuration(downId, upId, durationNs) }
    fun cloneEvent(id: String, offsetNs: Long) = editOp { it.cloneEvent(id, offsetNs) }
    fun cloneSelection(startAtNs: Long) = editOp { it.cloneSelection(_selectedIds.value, startAtNs) }
    fun deleteSelection() = editOp { it.deleteEvents(_selectedIds.value) }.also { _selectedIds.value = emptySet() }
    fun undo() = editOp { it.undo() }
    fun redo() = editOp { it.redo() }
    fun offsetTimestamps(deltaNs: Long) = editOp { it.offsetTimestamps(_selectedIds.value, deltaNs) }
    fun scaleTiming(factor: Double) = editOp { it.scaleTiming(_selectedIds.value, factor) }

    fun setRepeat(mode: RepeatMode, count: Int) = editOp {
        it.replaceMacro(it.macro.copy(playback = it.macro.playback.copy(repeatMode = mode, repeatCount = count)))
    }

    fun setSpeed(speed: Float) = editOp {
        it.replaceMacro(it.macro.copy(playback = it.macro.playback.copy(speedMultiplier = speed)))
    }

    fun rename(newName: String) = editOp { it.replaceMacro(it.macro.copy(name = newName)) }

    fun saveNow() {
        _currentEditor.value?.macro?.let { autosave.saveNow(it) }
    }

    // ---- Playback ------------------------------------------------------------

    private var engine: PlaybackEngine? = null

    fun play() {
        val macro = _currentEditor.value?.macro ?: return
        val backend = if (useSystemWidePlayback) AccessibilityPlaybackBackend()
        else InAppPlaybackBackend { event -> _previewEvents.value = _previewEvents.value + event }
        val eng = PlaybackEngine(backend)
        engine = eng
        viewModelScope.launch {
            eng.progress.collect { _playbackProgress.value = it }
        }
        viewModelScope.launch {
            _previewEvents.value = emptyList()
            eng.play(macro)
        }
    }

    fun pausePlayback() = engine?.pause()
    fun resumePlayback() = engine?.resume()
    fun stopPlayback() = engine?.stop()
}
