package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable storage in public Downloads/VOID APPS so uninstalling VOID does not erase recovery data. */
class SnapshotStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val legacyFile = File(appContext.filesDir, "snapshots.json")

    @Synchronized
    fun list(): List<Snapshot> = read().sortedByDescending { it.createdAtMs }

    @Synchronized
    fun get(id: String): Snapshot? = read().firstOrNull { it.id == id }

    @Synchronized
    fun put(snapshot: Snapshot) {
        require(snapshot.id.isNotBlank()) { "snapshot id must not be blank" }
        val next = read().filterNot { it.id == snapshot.id } + snapshot
        write(next.sortedByDescending { it.createdAtMs }.take(MAX_SNAPSHOTS))
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = read()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        write(next)
        return true
    }

    private fun read(): List<Snapshot> = runCatching {
        val text = readText(FILE_NAME) ?: legacyFile.takeIf { it.exists() }?.readText() ?: return emptyList()
        val array = JSONArray(text)
        buildList(array.length()) {
            for (i in 0 until array.length()) add(snapshotFrom(array.getJSONObject(i)))
        }
    }.getOrElse { emptyList() }

    private fun write(snapshots: List<Snapshot>) {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            array.put(JSONObject().apply {
                put("id", snapshot.id)
                put("name", snapshot.name)
                put("createdAtMs", snapshot.createdAtMs)
                put("entries", JSONArray().apply {
                    snapshot.entries.forEach { entry ->
                        put(JSONObject().apply {
                            put("pkg", entry.pkg)
                            put("isSystem", entry.isSystem)
                            put("state", entry.state.name)
                        })
                    }
                })
            })
        }
        writeText(FILE_NAME, array.toString())
    }

    private fun snapshotFrom(json: JSONObject): Snapshot {
        val entries = json.optJSONArray("entries") ?: JSONArray()
        return Snapshot(
            id = json.getString("id"),
            name = json.optString("name", "Snapshot"),
            createdAtMs = json.optLong("createdAtMs", 0L),
            entries = buildList(entries.length()) {
                for (i in 0 until entries.length()) {
                    val item = entries.getJSONObject(i)
                    val state = runCatching { AppState.valueOf(item.getString("state")) }.getOrNull() ?: continue
                    val pkg = item.optString("pkg")
                    if (isValidPackageName(pkg)) add(SnapshotEntry(pkg, item.optBoolean("isSystem"), state))
                }
            }.sortedBy { it.pkg },
        )
    }

    private fun readText(name: String): String? = runCatching {
        find(name)?.let { resolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } }
    }.getOrNull()

    private fun writeText(name: String, text: String) {
        runCatching {
            val uri = find(name) ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY)
                },
            ) ?: error("could not create persistent storage")
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                ?: error("could not open persistent storage")
        }.onFailure {
            // Keep the feature usable on unusual vendor storage implementations. The public
            // file remains the primary source and is what survives uninstall.
            legacyFile.writeText(text)
        }
    }

    private fun find(name: String) = resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
        arrayOf(name, DIRECTORY),
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) MediaStore.Downloads.getContentUri("external", cursor.getLong(0)) else null }

    private companion object {
        const val DIRECTORY = "Download/VOID APPS/"
        const val FILE_NAME = "snapshots.json"
        const val MAX_SNAPSHOTS = 50
    }
}

/** Durable user pins in the same uninstall-safe public directory. */
class PinStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val legacyFile = File(appContext.filesDir, "pins.json")

    @Synchronized
    fun list(): Set<String> = runCatching {
        val text = readText() ?: legacyFile.takeIf { it.exists() }?.readText() ?: return emptySet()
        val array = JSONArray(text)
        buildSet {
            for (i in 0 until array.length()) {
                val pkg = array.optString(i)
                if (isValidPackageName(pkg)) add(pkg)
            }
        }
    }.getOrElse { emptySet() }

    @Synchronized
    fun set(pkg: String, pinned: Boolean): Set<String> {
        require(isValidPackageName(pkg)) { "invalid package name" }
        val next = list().toMutableSet().apply { if (pinned) add(pkg) else remove(pkg) }
        val array = JSONArray()
        next.sorted().forEach(array::put)
        writeText(array.toString())
        return next
    }

    private fun readText(): String? = runCatching {
        find()?.let { resolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } }
    }.getOrNull()

    private fun writeText(text: String) {
        runCatching {
            val uri = find() ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY)
                },
            ) ?: error("could not create persistent storage")
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                ?: error("could not open persistent storage")
        }.onFailure { legacyFile.writeText(text) }
    }

    private fun find() = resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
        arrayOf(FILE_NAME, DIRECTORY),
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) MediaStore.Downloads.getContentUri("external", cursor.getLong(0)) else null }

    private companion object {
        const val DIRECTORY = "Download/VOID APPS/"
        const val FILE_NAME = "pins.json"
    }
}
