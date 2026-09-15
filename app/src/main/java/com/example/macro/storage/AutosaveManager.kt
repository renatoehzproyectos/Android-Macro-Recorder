package com.example.macro.storage

import com.example.macro.model.Macro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debounces saves so we don't hit disk on every tiny drag (spec ยง67). */
class AutosaveManager(
    private val scope: CoroutineScope,
    private val repository: MacroRepository,
    private val debounceMs: Long = 500
) {
    private var job: Job? = null

    fun scheduleSave(macroProvider: () -> Macro) {
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            repository.save(macroProvider())
        }
    }

    fun saveNow(macro: Macro) {
        job?.cancel()
        repository.save(macro)
    }
}
