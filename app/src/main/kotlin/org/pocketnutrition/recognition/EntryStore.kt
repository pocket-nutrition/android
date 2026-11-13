// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private val fileJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

/**
 * What a load found. Entries that decoded are returned; raw JSON of those that did not (an enum value
 * from a newer version, say) is kept in [preserved] and written back verbatim by [saveEntriesFile], so a
 * later save never silently drops them. [unreadable] means the whole file could not be parsed, in which
 * case nothing may be written over it. A load that is not [intact] never triggers deletions.
 */
class LoadedEntries(
    val entries: List<Entry>,
    val preserved: List<JsonElement> = emptyList(),
    val unreadable: Boolean = false,
) {
    val intact: Boolean get() = !unreadable && preserved.isEmpty()
}

/** An absent file is an intact, empty load; an unreadable one is not. */
fun loadEntriesFile(file: File): LoadedEntries {
    if (!file.exists()) return LoadedEntries(emptyList())
    return try {
        val root = fileJson.parseToJsonElement(file.readText()).jsonObject
        val entries = mutableListOf<Entry>()
        val preserved = mutableListOf<JsonElement>()
        for (element in root.getValue("entries").jsonArray) {
            try {
                entries += fileJson.decodeFromJsonElement<Entry>(element)
            } catch (e: SerializationException) {
                preserved += element
            } catch (e: IllegalArgumentException) {
                preserved += element
            }
        }
        LoadedEntries(entries, preserved)
    } catch (e: Exception) {
        // IOException, malformed JSON, missing "entries", wrong shape.
        LoadedEntries(emptyList(), unreadable = true)
    }
}

/**
 * Written to a sibling and moved into place, so an interrupted write cannot truncate the file.
 * [preserved] elements are appended verbatim. Returns `false` (never throws) when the disk refuses.
 */
fun saveEntriesFile(file: File, entries: List<Entry>, preserved: List<JsonElement> = emptyList()): Boolean {
    val temporary = File(file.parentFile, "${file.name}.tmp")
    return try {
        val array = JsonArray(entries.map { fileJson.encodeToJsonElement(Entry.serializer(), it) } + preserved)
        val root = JsonObject(mapOf("version" to JsonPrimitive(1), "entries" to array))
        temporary.writeText(fileJson.encodeToString(JsonObject.serializer(), root))
        if (!temporary.renameTo(file)) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (e: IOException) {
        temporary.delete()
        false
    }
}

/**
 * The observable list of entries behind the capture screen, plus the private audio folder.
 * Both the entries file and the audio folder are excluded from backup.
 *
 * Retention runs once on construction. It is skipped entirely when the file was not intact (a `.corrupt`
 * copy is kept), and an audio folder that cannot be listed counts as "unknown", never "empty". A failed
 * write degrades to in-memory state; an audio file is deleted only after the entries that stop
 * referencing it have been persisted.
 */
class EntryStore(
    private val file: File,
    val audioDir: File,
    nowEpochMs: Long = System.currentTimeMillis(),
) {
    private val _entries = mutableStateListOf<Entry>()

    /** Newest first. */
    val entries: List<Entry> get() = _entries.sortedByDescending { it.createdAtEpochMs }

    private var preserved: List<JsonElement> = emptyList()
    private var unreadable = false
    private val deletedWhileUnreadable = mutableSetOf<String>()

    init {
        File(file.parentFile, "${file.name}.tmp").delete() // a write interrupted by a kill
        audioDir.mkdirs()
        val loaded = loadEntriesFile(file)
        preserved = loaded.preserved
        unreadable = loaded.unreadable
        if (loaded.unreadable) {
            runCatching { file.copyTo(File(file.parentFile, "${file.name}.corrupt"), overwrite = true) }
        } else {
            if (loaded.preserved.isNotEmpty()) {
                runCatching { file.copyTo(File(file.parentFile, "${file.name}.corrupt"), overwrite = true) }
            }
            val files = audioDir.listFiles() // null = could not list = unknown
            val retention = applyRetention(
                loaded.entries,
                nowEpochMs,
                existingAudio = files?.map { it.name }?.toSet(),
                audioModifiedMs = files?.associate { it.name to it.lastModified() } ?: emptyMap(),
            )
            _entries.addAll(retention.entries)
            val saved = retention.entries == loaded.entries || persist()
            // With undecodable entries preserved, only audio that a DECODED entry referenced may go:
            // any other file might belong to an entry this version cannot read, so no orphan sweep.
            val deletable = if (loaded.preserved.isEmpty()) {
                retention.audioToDelete
            } else {
                val referenced = loaded.entries.mapNotNull { it.audioFile }.toSet()
                retention.audioToDelete.filter { it in referenced }
            }
            if (saved) deletable.forEach { File(audioDir, it).delete() }
        }
    }

    fun find(localId: String): Entry? = _entries.firstOrNull { it.localId == localId }

    fun audioFor(entry: Entry): File? = entry.audioFile?.let { File(audioDir, it) }

    /**
     * Adds or replaces. Returns whether the change reached the disk; the audio file the new version no
     * longer refers to is deleted only after that. Callers gate server-side deletions on the result.
     */
    fun upsert(entry: Entry): Boolean {
        val index = _entries.indexOfFirst { it.localId == entry.localId }
        val previousAudio = if (index >= 0) _entries[index].audioFile else null
        if (index < 0) _entries.add(entry) else _entries[index] = entry
        val saved = persist()
        if (saved && previousAudio != null && previousAudio != entry.audioFile) File(audioDir, previousAudio).delete()
        return saved
    }

    /** Removes the entry and, once that is persisted, its audio. `true` when the removal reached the disk. */
    fun remove(localId: String): Boolean {
        val entry = find(localId) ?: return true
        _entries.removeAll { it.localId == localId }
        // While the file is unreadable a later merge must not bring this entry back from disk.
        if (unreadable) deletedWhileUnreadable += localId
        val saved = persist()
        if (saved) entry.audioFile?.let { File(audioDir, it).delete() }
        return saved
    }

    /**
     * While the file could not be parsed at all, nothing is written over it (it may be a transient read
     * failure); each attempt first retries the load and, if it now parses, merges what is on disk with
     * what changed in memory. Undecodable entries are written back verbatim.
     */
    private fun persist(): Boolean {
        if (unreadable) {
            val again = loadEntriesFile(file)
            if (again.unreadable) return false
            unreadable = false
            preserved = again.preserved
            val known = _entries.map { it.localId }.toSet() + deletedWhileUnreadable
            _entries.addAll(again.entries.filter { it.localId !in known })
            deletedWhileUnreadable.clear()
        }
        return saveEntriesFile(file, _entries.toList(), preserved)
    }
}
