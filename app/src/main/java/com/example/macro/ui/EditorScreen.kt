package com.example.macro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.macro.editor.DerivedGesture
import com.example.macro.model.EventType
import com.example.macro.model.MacroEvent
import com.example.macro.model.RepeatMode
import com.example.macro.model.sortedByTimestamp
import com.example.macro.playback.PlaybackState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: MacroViewModel, onBack: () -> Unit) {
    val editor by viewModel.currentEditor.collectAsState()
    val macro = editor?.macro ?: return
    val recordingState by viewModel.recordingState.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val derived by viewModel.derivedGestures.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()

    var zoomPxPerSec by remember { mutableStateOf(120f) }
    var showRepeatSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(macro.name + if (editor?.isDirty == true) " *" else "") },
                navigationIcon = { TextButton(onClick = { onBack() }) { Text("←") } },
                actions = {
                    TextButton(onClick = { viewModel.undo() }) { Text("Undo") }
                    TextButton(onClick = { viewModel.redo() }) { Text("Redo") }
                    TextButton(onClick = { viewModel.saveNow() }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ---- Recording / preview surface ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF101418))
            ) {
                RecordingSurface(viewModel, recordingState)
                if (recordingState == RecordingState.RECORDING) {
                    RecordingOverlay(
                        elapsedMs = recordingElapsedMs,
                        actionCount = viewModel.recorder.capturedCount,
                        onStop = { viewModel.stopRecording() }
                    )
                } else {
                    Row(Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(12.dp)) {
                        Button(onClick = { viewModel.startRecording() }) { Text("● Record") }
                    }
                }
                if (progress.state == PlaybackState.PLAYING || progress.state == PlaybackState.PAUSED) {
                    PlaybackOverlay(progress, onPause = { viewModel.pausePlayback() }, onResume = { viewModel.resumePlayback() }, onStop = { viewModel.stopPlayback() })
                }
            }

            Divider()

            // ---- Timeline ----
            Column(Modifier.height(220.dp).fillMaxWidth().padding(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Timeline", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("Zoom")
                    Slider(
                        value = zoomPxPerSec,
                        onValueChange = { zoomPxPerSec = it },
                        valueRange = 20f..800f,
                        modifier = Modifier.width(140.dp)
                    )
                }
                Timeline(
                    events = macro.events.sortedByTimestamp(),
                    gestures = derived,
                    selectedIds = selectedIds,
                    pxPerSecond = zoomPxPerSec,
                    onSelect = { id -> viewModel.toggleSelect(id, exclusive = true) }
                )
            }

            Divider()

            // ---- Inspector / bulk actions ----
            val selectedEvent = macro.events.find { it.id == selectedIds.firstOrNull() }
            if (selectedIds.size == 1 && selectedEvent != null) {
                ActionInspector(
                    event = selectedEvent,
                    onSetTimestamp = { ns, ripple -> viewModel.setTimestamp(selectedEvent.id, ns, ripple) },
                    onSetCoordinate = { x, y -> viewModel.setCoordinate(selectedEvent.id, x, y) },
                    onClone = { viewModel.cloneEvent(selectedEvent.id, 250_000_000) },
                    onDelete = { viewModel.deleteSelection() }
                )
            } else if (selectedIds.size > 1) {
                BulkInspector(
                    count = selectedIds.size,
                    onClone = { viewModel.cloneSelection(macro.durationNs + 500_000_000) },
                    onDelete = { viewModel.deleteSelection() },
                    onOffset = { deltaMs -> viewModel.offsetTimestamps(deltaMs * 1_000_000) },
                    onScale = { factor -> viewModel.scaleTiming(factor) }
                )
            } else {
                Text("Select an action on the timeline to edit it.", Modifier.padding(16.dp))
            }

            Divider()

            // ---- Playback bar ----
            Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Button(onClick = { viewModel.play() }, enabled = macro.events.isNotEmpty()) { Text("▶ Play") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showRepeatSheet = true }) {
                    Text(
                        when (macro.playback.repeatMode) {
                            RepeatMode.ONCE -> "Repeat: Once"
                            RepeatMode.COUNT -> "Repeat: ${macro.playback.repeatCount}x"
                            RepeatMode.INFINITE -> "Repeat: ∞"
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("System-wide")
                    Switch(checked = viewModel.useSystemWidePlayback, onCheckedChange = { viewModel.useSystemWidePlayback = it })
                }
            }
        }
    }

    if (showRepeatSheet) {
        RepeatSettingsDialog(
            current = macro.playback,
            onDismiss = { showRepeatSheet = false },
            onConfirm = { mode, count ->
                viewModel.setRepeat(mode, count)
                showRepeatSheet = false
            }
        )
    }
}

@Composable
private fun RecordingSurface(viewModel: MacroViewModel, recordingState: RecordingState) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInteropFilter { ev ->
                if (recordingState == RecordingState.RECORDING) {
                    viewModel.onMotionEvent(ev)
                }
                true
            }
    ) {
        Text(
            "Recording / Preview Surface\n(perform gestures here while recording)",
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(24.dp)
        )
    }
}

@Composable
private fun RecordingOverlay(elapsedMs: Long, actionCount: Int, onStop: () -> Unit) {
    Row(
        Modifier
            .align(androidx.compose.ui.Alignment.TopStart)
            .padding(12.dp)
            .background(Color(0xCC000000), MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        val s = elapsedMs / 1000
        val ms = elapsedMs % 1000
        Text("● RECORDING  %02d:%02d.%03d".format(s / 60, s % 60, ms), color = Color.Red)
        Spacer(Modifier.width(12.dp))
        Text("Actions: $actionCount", color = Color.White)
        Spacer(Modifier.width(12.dp))
        Button(onClick = onStop) { Text("STOP") }
    }
}

@Composable
private fun PlaybackOverlay(progress: com.example.macro.playback.PlaybackProgress, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier
            .align(androidx.compose.ui.Alignment.TopEnd)
            .padding(12.dp)
            .background(Color(0xCC000000), MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        val label = if (progress.totalIterations == -1) "∞ Iteration ${progress.currentIteration}" else "Iteration ${progress.currentIteration}/${progress.totalIterations}"
        Text(label, color = Color.White)
        Spacer(Modifier.width(8.dp))
        if (progress.state == PlaybackState.PLAYING) {
            TextButton(onClick = onPause) { Text("Pause", color = Color.White) }
        } else {
            TextButton(onClick = onResume) { Text("Resume", color = Color.White) }
        }
        TextButton(onClick = onStop) { Text("STOP", color = Color.Red) }
    }
}

@Composable
private fun Timeline(
    events: List<MacroEvent>,
    gestures: List<DerivedGesture>,
    selectedIds: Set<String>,
    pxPerSecond: Float,
    onSelect: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val totalDurationSec = ((events.maxOfOrNull { it.timestampNs } ?: 0L) / 1_000_000_000.0).coerceAtLeast(1.0)
    val widthPx = (totalDurationSec * pxPerSecond).toFloat() + 200f

    Box(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Canvas(
            modifier = Modifier
                .width(widthPx.dp)
                .fillMaxHeight()
                .pointerInput(events) {
                    detectDragGestures { change, _ ->
                        val tSec = change.position.x / pxPerSecond
                        val tNs = (tSec * 1_000_000_000).toLong()
                        val nearest = events.minByOrNull { kotlin.math.abs(it.timestampNs - tNs) }
                        nearest?.let { onSelect(it.id) }
                    }
                }
        ) {
            // Ruler ticks
            var t = 0f
            while (t <= totalDurationSec.toFloat() + 1f) {
                val x = t * pxPerSecond
                drawLine(Color.DarkGray, Offset(x, 0f), Offset(x, size.height), 1f)
                t += 1f
            }

            // Gesture bands
            gestures.forEach { g ->
                val x1 = (g.startNs / 1_000_000_000.0 * pxPerSecond).toFloat()
                val x2 = (g.endNs / 1_000_000_000.0 * pxPerSecond).toFloat()
                drawRect(
                    color = Color(0xFF2D6A4F),
                    topLeft = Offset(x1, size.height - 24f),
                    size = androidx.compose.ui.geometry.Size((x2 - x1).coerceAtLeast(4f), 20f)
                )
            }

            // Event markers
            events.forEach { e ->
                val x = (e.timestampNs / 1_000_000_000.0 * pxPerSecond).toFloat()
                val color = when (e.type) {
                    EventType.TOUCH_DOWN -> Color(0xFF52B788)
                    EventType.TOUCH_MOVE -> Color(0xFF74C0FC)
                    EventType.TOUCH_UP -> Color(0xFFE76F51)
                    else -> Color.Gray
                }
                val selected = e.id in selectedIds
                drawCircle(
                    color = if (selected) Color.Yellow else color,
                    radius = if (selected) 8f else 6f,
                    center = Offset(x, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun ActionInspector(
    event: MacroEvent,
    onSetTimestamp: (Long, Boolean) -> Unit,
    onSetCoordinate: (Float, Float) -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit
) {
    val pointer = event.pointers.firstOrNull()
    var tsText by remember(event.id) { mutableStateOf(formatTimestamp(event.timestampNs)) }
    var xText by remember(event.id) { mutableStateOf(pointer?.x?.toInt()?.toString() ?: "0") }
    var yText by remember(event.id) { mutableStateOf(pointer?.y?.toInt()?.toString() ?: "0") }
    var ripple by remember { mutableStateOf(false) }

    Column(Modifier.padding(12.dp)) {
        Text(event.type.name, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(value = tsText, onValueChange = { tsText = it }, label = { Text("Timestamp mm:ss.mmm") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("Ripple")
            Switch(checked = ripple, onCheckedChange = { ripple = it })
        }
        Row {
            OutlinedTextField(value = xText, onValueChange = { xText = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = yText, onValueChange = { yText = it }, label = { Text("Y") }, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                parseTimestamp(tsText)?.let { onSetTimestamp(it, ripple) }
                xText.toFloatOrNull()?.let { x -> yText.toFloatOrNull()?.let { y -> onSetCoordinate(x, y) } }
            }) { Text("Apply") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClone) { Text("Clone") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun BulkInspector(count: Int, onClone: () -> Unit, onDelete: () -> Unit, onOffset: (Long) -> Unit, onScale: (Double) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Text("Selected: $count actions", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = onClone) { Text("Clone Selection") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { onOffset(100) }) { Text("+100ms") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onOffset(-100) }) { Text("-100ms") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onScale(0.5) }) { Text("Scale 0.5x") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onScale(2.0) }) { Text("Scale 2x") }
        }
    }
}

@Composable
private fun RepeatSettingsDialog(current: com.example.macro.model.PlaybackSettings, onDismiss: () -> Unit, onConfirm: (RepeatMode, Int) -> Unit) {
    var mode by remember { mutableStateOf(current.repeatMode) }
    var countText by remember { mutableStateOf(current.repeatCount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = mode == RepeatMode.ONCE, onClick = { mode = RepeatMode.ONCE })
                    Text("Once")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = mode == RepeatMode.COUNT, onClick = { mode = RepeatMode.COUNT })
                    Text("Custom count:")
                    OutlinedTextField(value = countText, onValueChange = { countText = it }, modifier = Modifier.width(90.dp))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = mode == RepeatMode.INFINITE, onClick = { mode = RepeatMode.INFINITE })
                    Text("Infinite (∞)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode, countText.toIntOrNull() ?: 1) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTimestamp(ns: Long): String {
    val totalMs = ns / 1_000_000
    val m = totalMs / 60000
    val s = (totalMs / 1000) % 60
    val ms = totalMs % 1000
    return "%02d:%02d.%03d".format(m, s, ms)
}

private fun parseTimestamp(s: String): Long? {
    return try {
        val parts = s.split(":")
        val minutes = parts[0].toLong()
        val secMs = parts[1].split(".")
        val seconds = secMs[0].toLong()
        val millis = if (secMs.size > 1) secMs[1].padEnd(3, '0').take(3).toLong() else 0L
        (minutes * 60_000 + seconds * 1000 + millis) * 1_000_000
    } catch (e: Exception) {
        null
    }
}
