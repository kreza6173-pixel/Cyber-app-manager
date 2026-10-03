package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable storage for reversible package state only. No APKs or private app data are stored. */
class SnapshotStore(context: Context) {
    private val file = File(context.filesDir, "snapshots.json")
    private val tmp = File(context.filesDir, "snapshots.json.tmp")

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
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList(array.length()) {
            for (i in 0 until array.length()) add(snapshotFrom(array.getJSONObject(i)))
        }
    }.getOrElse { emptyList() }

    private fun write(snapshots: List<Snapshot>) {
        val array = JSONArray()
        snapshots.forEach { array.put(snapshotTo(it)) }
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not commit snapshot store" }
        }
    }

    private fun snapshotTo(snapshot: Snapshot) = JSONObject().apply {
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

    private companion object { const val MAX_SNAPSHOTS = 50 }
}

/** Durable user pins. Pins contain package names only and are always revalidated by the guard. */
class PinStore(context: Context) {
    private val file = File(context.filesDir, "pins.json")

    @Synchronized
    fun list(): Set<String> = runCatching {
        if (!file.exists()) return emptySet()
        val array = JSONArray(file.readText())
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
        val tmp = File(file.parentFile, "pins.json.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not commit pin store" }
        }
        return next
    }
}
