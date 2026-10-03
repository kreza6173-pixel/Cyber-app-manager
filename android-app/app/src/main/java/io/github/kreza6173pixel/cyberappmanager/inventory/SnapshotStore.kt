package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable storage in public Downloads/VOID APPS so uninstalling VOID does not erase recovery data. */
class SnapshotStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val legacyFile = File(context.applicationContext.filesDir, "snapshots.json")

    @Synchronized fun list(): List<Snapshot> = read().sortedByDescending { it.createdAtMs }
    @Synchronized fun get(id: String): Snapshot? = read().firstOrNull { it.id == id }
    @Synchronized fun put(snapshot: Snapshot) {
        require(snapshot.id.isNotBlank()) { "snapshot id must not be blank" }
        write((read().filterNot { it.id == snapshot.id } + snapshot).sortedByDescending { it.createdAtMs }.take(MAX_SNAPSHOTS))
    }
    @Synchronized fun delete(id: String): Boolean {
        val current = read(); val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        write(next); return true
    }

    private fun read(): List<Snapshot> = runCatching {
        val texts = mediaUris("snapshots").mapNotNull { uri -> runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
        val all = if (texts.isNotEmpty()) texts else listOfNotNull(legacyFile.takeIf { it.exists() }?.readText())
        val byId = LinkedHashMap<String, Snapshot>()
        all.forEach { text ->
            runCatching {
                val array = JSONArray(text)
                for (i in 0 until array.length()) snapshotFrom(array.getJSONObject(i)).also { byId[it.id] = it }
            }
        }
        byId.values.toList()
    }.getOrElse { emptyList() }

    private fun write(snapshots: List<Snapshot>) {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            array.put(JSONObject().apply {
                put("id", snapshot.id); put("name", snapshot.name); put("createdAtMs", snapshot.createdAtMs)
                put("entries", JSONArray().apply { snapshot.entries.forEach { entry -> put(JSONObject().apply { put("pkg", entry.pkg); put("isSystem", entry.isSystem); put("state", entry.state.name) }) } })
            })
        }
        writeCanonical(FILE_NAME, array.toString(), "snapshots")
    }

    private fun snapshotFrom(json: JSONObject): Snapshot {
        val entries = json.optJSONArray("entries") ?: JSONArray()
        return Snapshot(json.getString("id"), json.optString("name", "Snapshot"), json.optLong("createdAtMs", 0L), buildList(entries.length()) {
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val state = runCatching { AppState.valueOf(item.getString("state")) }.getOrNull() ?: continue
                val pkg = item.optString("pkg")
                if (isValidPackageName(pkg)) add(SnapshotEntry(pkg, item.optBoolean("isSystem"), state))
            }
        }.sortedBy { it.pkg })
    }

    private fun writeCanonical(name: String, text: String, prefix: String) {
        runCatching {
            val files = mediaUris(prefix)
            val uri = files.firstOrNull { displayName(it) == name } ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY)
            }) ?: error("could not create persistent storage")
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("could not open persistent storage")
            files.filter { it != uri }.forEach { runCatching { resolver.delete(it, null, null) } }
        }.onFailure { legacyFile.writeText(text) }
    }

    private fun mediaUris(prefix: String): List<android.net.Uri> = runCatching {
        val base = MediaStore.Files.getContentUri("external")
        resolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(DIRECTORY), null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID); val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME); buildList {
                while (c.moveToNext()) if (c.getString(name).startsWith(prefix) && c.getString(name).endsWith(".json")) add(MediaStore.Files.getContentUri("external", c.getLong(id)))
            }
        } ?: emptyList()
    }.getOrElse { emptyList() }

    private fun displayName(uri: android.net.Uri): String? = runCatching { resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
    private companion object { const val DIRECTORY = "Download/VOID APPS/"; const val FILE_NAME = "snapshots.json"; const val MAX_SNAPSHOTS = 50 }
}

/** Durable user pins in the same uninstall-safe public directory. */
class PinStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val legacyFile = File(context.applicationContext.filesDir, "pins.json")
    @Synchronized fun list(): Set<String> = runCatching {
        val text = mediaUri()?.let { resolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } } ?: legacyFile.takeIf { it.exists() }?.readText() ?: return emptySet()
        val array = JSONArray(text); buildSet { for (i in 0 until array.length()) array.optString(i).takeIf(::isValidPackageName)?.let(::add) }
    }.getOrElse { emptySet() }
    @Synchronized fun set(pkg: String, pinned: Boolean): Set<String> {
        require(isValidPackageName(pkg)) { "invalid package name" }; val next = list().toMutableSet().apply { if (pinned) add(pkg) else remove(pkg) }; val array = JSONArray(); next.sorted().forEach(array::put); write(array.toString()); return next
    }
    private fun write(text: String) { runCatching {
        val uri = mediaUri() ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME); put(MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY) }) ?: error("could not create persistent storage")
        resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("could not open persistent storage")
    }.onFailure { legacyFile.writeText(text) } }
    private fun mediaUri(): android.net.Uri? = runCatching {
        val base = MediaStore.Files.getContentUri("external")
        resolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(DIRECTORY), null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID); val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) if (c.getString(name) == FILE_NAME) return@use MediaStore.Files.getContentUri("external", c.getLong(id)); null
        }
    }.getOrNull()
    private companion object { const val DIRECTORY = "Download/VOID APPS/"; const val FILE_NAME = "pins.json" }
}
