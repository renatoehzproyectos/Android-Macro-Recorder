package com.example.macro.storage

import android.content.Context
import com.example.macro.model.Macro
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class MacroRepository(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir: File = File(context.filesDir, "macros").apply { mkdirs() }

    private fun fileFor(id: String) = File(dir, "$id.json")

    fun save(macro: Macro) {
        val updated = macro.copy(modifiedAt = System.currentTimeMillis())
        fileFor(updated.id).writeText(json.encodeToString(Macro.serializer(), updated))
    }

    fun load(id: String): Macro? {
        val f = fileFor(id)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Macro.serializer(), f.readText()) }.getOrNull()
    }

    fun listAll(): List<Macro> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString(Macro.serializer(), it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()

    fun delete(id: String) {
        fileFor(id).delete()
    }

    fun rename(id: String, newName: String) {
        val m = load(id) ?: return
        save(m.copy(name = newName))
    }

    fun createNew(name: String): Macro {
        val macro = Macro(id = UUID.randomUUID().toString(), name = name)
        save(macro)
        return macro
    }

    fun exportTo(macro: Macro, target: File) {
        target.writeText(json.encodeToString(Macro.serializer(), macro))
    }

    fun importFrom(source: File): Macro {
        val imported = json.decodeFromString(Macro.serializer(), source.readText())
        val withNewId = imported.copy(id = UUID.randomUUID().toString())
        save(withNewId)
        return withNewId
    }
}
