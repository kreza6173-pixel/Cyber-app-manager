package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * Pure parsers for package-manager output. No Android imports; unit-tested with real lines
 * from the reference phone (Xiaomi, Android 16, Shizuku uid 2000).
 */

private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")

/** Android package names: letters, digits, underscore, dot-separated. `android` has no dot. */
fun isValidPackageName(name: String): Boolean = name.length <= 255 && PACKAGE_NAME.matches(name)

/**
 * Parses `pm list packages` output. Accepts both raw lines (`package:com.x uid:1000`) and
 * lines already stripped by `sed 's/^package://'`. Anything that is not a valid package name
 * is ignored, so stray warnings never become fake packages.
 */
fun parsePackageList(stdout: String): Set<String> {
    val out = LinkedHashSet<String>()
    for (raw in stdout.lineSequence()) {
        var line = raw.trim()
        if (line.startsWith("package:")) line = line.removePrefix("package:")
        val name = line.substringBefore(' ').trim()
        if (name.isNotEmpty() && isValidPackageName(name)) out.add(name)
    }
    return out
}

/**
 * The shell inherits the device locale, so on a Persian phone dumpsys prints dates with
 * Extended Arabic-Indic digits. Convert those (and Arabic-Indic) to ASCII before parsing.
 */
fun normalizeDigits(s: String): String {
    if (s.none { it in '\u06F0'..'\u06F9' || it in '\u0660'..'\u0669' }) return s
    val sb = StringBuilder(s.length)
    for (c in s) {
        sb.append(
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            },
        )
    }
    return sb.toString()
}

data class PackageDetails(
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val installer: String?,
    val firstInstall: String?,
    val lastUpdate: String?,
    /** key=value pairs of the first `User 0:` line, e.g. installed, enabled, stopped. */
    val userFlags: Map<String, String>,
)

private val VERSION_CODE = Regex("\\bversionCode=(\\d+)")
private val MIN_SDK = Regex("\\bminSdk=(\\d+)")
private val TARGET_SDK = Regex("\\btargetSdk=(\\d+)")
private val INSTALLER = Regex("\\binstallerPackageName=(\\S+)")
private val KEY_VALUE = Regex("(\\w+)=(\\S+)")

/**
 * Parses the grep-filtered `dumpsys package <pkg>` lines. The first occurrence of each field
 * wins, because later sections of the dump can repeat keys for other contexts.
 */
fun parsePackageDetails(text: String): PackageDetails {
    var versionName: String? = null
    var versionCode: Long? = null
    var minSdk: Int? = null
    var targetSdk: Int? = null
    var installer: String? = null
    var firstInstall: String? = null
    var lastUpdate: String? = null
    val userFlags = LinkedHashMap<String, String>()

    for (raw in text.lineSequence()) {
        val line = normalizeDigits(raw).trim()
        if (line.isEmpty()) continue
        if (versionName == null && line.startsWith("versionName=")) {
            versionName = line.removePrefix("versionName=").trim()
        }
        if (versionCode == null) {
            val m = VERSION_CODE.find(line)
            if (m != null) {
                versionCode = m.groupValues[1].toLongOrNull()
                minSdk = MIN_SDK.find(line)?.groupValues?.get(1)?.toIntOrNull()
                targetSdk = TARGET_SDK.find(line)?.groupValues?.get(1)?.toIntOrNull()
            }
        }
        if (installer == null) {
            val m = INSTALLER.find(line)
            if (m != null) installer = m.groupValues[1].takeIf { it != "null" }
        }
        if (firstInstall == null && line.startsWith("firstInstallTime=")) {
            firstInstall = line.removePrefix("firstInstallTime=").trim()
        }
        if (lastUpdate == null && line.startsWith("lastUpdateTime=")) {
            lastUpdate = line.removePrefix("lastUpdateTime=").trim()
        }
        if (userFlags.isEmpty() && line.startsWith("User 0:") && line.contains("installed=")) {
            for (kv in KEY_VALUE.findAll(line.removePrefix("User 0:"))) {
                userFlags[kv.groupValues[1]] = kv.groupValues[2]
            }
        }
    }
    return PackageDetails(
        versionName = versionName,
        versionCode = versionCode,
        minSdk = minSdk,
        targetSdk = targetSdk,
        installer = installer,
        firstInstall = firstInstall,
        lastUpdate = lastUpdate,
        userFlags = userFlags,
    )
}
