package com.faunary.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.faunary.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sigpipe.jbsdiff.Patch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

// ---- Manifest published by tools/release.py as app-releases/latest.json ----

@Serializable
data class ReleaseFile(val path: String, val size: Long, val sha256: String)

@Serializable
data class ReleasePatch(val fromSha256: String, val path: String, val size: Long, val sha256: String)

@Serializable
data class AbiRelease(val apk: ReleaseFile, val patches: List<ReleasePatch> = emptyList())

@Serializable
data class ReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val notes: String = "",
    val abis: Map<String, AbiRelease>,
)

// ---- What the UI shows ----

data class UpdateState(
    val availableVersion: String? = null,
    val availableCode: Int = 0,
    val notes: String = "",
    /** Bytes the update will download (patch when possible, otherwise the full APK). */
    val downloadSize: Long = 0,
    val isPatch: Boolean = false,
    val downloadedBytes: Long = 0,
    val downloading: Boolean = false,
    val ready: Boolean = false,
    val waitingForWifi: Boolean = false,
    val error: String? = null,
    val lastChecked: Long = 0,
    val allowMobileData: Boolean = false,
) {
    val hasUpdate: Boolean get() = availableCode > BuildConfig.VERSION_CODE
}

/**
 * Self-update for sideloaded installs: fetches the manifest, downloads a bsdiff patch against the
 * installed APK when one exists (a few MB) or the full split APK otherwise, verifies SHA-256, and
 * keeps the result ready for [UpdateInstaller].
 */
@Singleton
class UpdateRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("faunary_update", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val readyApk: File? get() = readyFile().takeIf { it.exists() && _state.value.ready && _state.value.hasUpdate }

    fun setAllowMobileData(allow: Boolean) {
        prefs.edit { putBoolean(KEY_ALLOW_METERED, allow) }
        _state.value = _state.value.copy(allowMobileData = allow)
    }

    fun isMetered(): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    /**
     * Checks for an update and, when [download] is true, fetches it.
     * @return false only for transient failures worth retrying.
     */
    suspend fun checkAndDownload(download: Boolean): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val manifest = runCatching { fetchManifest() }.getOrElse {
                Log.w(TAG, "manifest fetch failed", it)
                update { copy(error = null, lastChecked = System.currentTimeMillis()) }
                return@withContext false
            }
            if (manifest == null || manifest.versionCode <= BuildConfig.VERSION_CODE) {
                clearDownloads()
                update { UpdateState(lastChecked = System.currentTimeMillis(), allowMobileData = allowMobileData) }
                persist()
                return@withContext true
            }
            val abi = Build.SUPPORTED_ABIS.firstOrNull { it in manifest.abis } ?: run {
                update { copy(error = "Pembaruan belum tersedia untuk perangkat ini.") }
                return@withContext true
            }
            val entry = manifest.abis.getValue(abi)

            // Already downloaded and verified for this version?
            if (prefs.getInt(KEY_READY_CODE, 0) == manifest.versionCode && readyFile().exists()) {
                update { copy(ready = true, downloading = false, waitingForWifi = false) }
                return@withContext true
            }

            val baseSha = runCatching { installedApkSha256() }.getOrNull()
            val patch = entry.patches.firstOrNull { it.fromSha256.equals(baseSha, ignoreCase = true) }
            update {
                copy(
                    availableVersion = manifest.versionName, availableCode = manifest.versionCode, notes = manifest.notes,
                    downloadSize = patch?.size ?: entry.apk.size, isPatch = patch != null,
                    downloadedBytes = 0, ready = false, error = null, lastChecked = System.currentTimeMillis(),
                    waitingForWifi = !download,
                )
            }
            persist()
            if (!download) return@withContext true

            update { copy(downloading = true, waitingForWifi = false) }
            val ok = (patch != null && tryPatch(patch, entry.apk)) || tryFull(entry.apk)
            if (ok) {
                prefs.edit { putInt(KEY_READY_CODE, manifest.versionCode) }
                update { copy(downloading = false, ready = true, downloadedBytes = downloadSize) }
            } else {
                update { copy(downloading = false, error = "Unduhan terputus, akan dicoba lagi otomatis.") }
            }
            persist()
            ok
        }
    }

    private fun tryPatch(patch: ReleasePatch, target: ReleaseFile): Boolean = runCatching {
        val patchFile = File(dir, "update.patch")
        download(patch.path, patchFile, patch.size)
        check(sha256(patchFile).equals(patch.sha256, ignoreCase = true)) { "patch checksum mismatch" }
        val out = File(dir, "update.apk.tmp")
        out.outputStream().buffered().use { stream ->
            Patch.patch(File(installedApkPath()).readBytes(), patchFile.readBytes(), stream)
        }
        check(sha256(out).equals(target.sha256, ignoreCase = true)) { "patched apk checksum mismatch" }
        patchFile.delete()
        out.renameTo(readyFile().also { it.delete() })
    }.onFailure { Log.w(TAG, "patch path failed, falling back to full apk", it) }.getOrDefault(false)

    private fun tryFull(apk: ReleaseFile): Boolean = runCatching {
        val part = File(dir, "update.apk.part")
        download(apk.path, part, apk.size)
        if (!sha256(part).equals(apk.sha256, ignoreCase = true)) {
            part.delete() // corrupted: start over next time
            error("apk checksum mismatch")
        }
        part.renameTo(readyFile().also { it.delete() })
    }.onFailure { Log.w(TAG, "full apk download failed", it) }.getOrDefault(false)

    /** Resumable download: continues a partial file with an HTTP Range request. */
    private fun download(path: String, target: File, expectedSize: Long) {
        val existing = if (target.exists()) target.length() else 0L
        if (existing == expectedSize) return
        val conn = (URL("$BASE_URL/$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (existing in 1 until expectedSize) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val append = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append && conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            var done = if (append) existing else 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(target, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 256 * 1024) {
                            lastReport = done
                            update { copy(downloadedBytes = done) }
                        }
                    }
                }
            }
            update { copy(downloadedBytes = done) }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchManifest(): ReleaseManifest? {
        // Cache-buster: the storage CDN may otherwise serve a stale manifest for a while.
        val conn = (URL("$BASE_URL/latest.json?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return try {
            when (conn.responseCode) {
                in 200..299 -> json.decodeFromString<ReleaseManifest>(conn.inputStream.bufferedReader().use { it.readText() })
                400, 404 -> null // nothing published yet
                else -> error("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun installedApkPath(): String = context.applicationInfo.sourceDir

    /** SHA-256 of the installed APK, cached per install so it's only hashed once per version. */
    private fun installedApkSha256(): String {
        @Suppress("DEPRECATION")
        val installedAt = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA).lastUpdateTime
        if (prefs.getLong(KEY_BASE_SHA_TIME, 0) == installedAt) prefs.getString(KEY_BASE_SHA, null)?.let { return it }
        return sha256(File(installedApkPath())).also {
            prefs.edit { putString(KEY_BASE_SHA, it); putLong(KEY_BASE_SHA_TIME, installedAt) }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readyFile() = File(dir, "update.apk")

    private fun clearDownloads() {
        dir.listFiles()?.forEach { it.delete() }
        prefs.edit { remove(KEY_READY_CODE) }
    }

    private inline fun update(block: UpdateState.() -> UpdateState) {
        _state.value = _state.value.block()
    }

    private fun persist() {
        val s = _state.value
        prefs.edit {
            putString(KEY_VERSION, s.availableVersion)
            putInt(KEY_CODE, s.availableCode)
            putString(KEY_NOTES, s.notes)
            putLong(KEY_SIZE, s.downloadSize)
            putBoolean(KEY_IS_PATCH, s.isPatch)
            putLong(KEY_CHECKED, s.lastChecked)
        }
    }

    private fun loadState(): UpdateState {
        val code = prefs.getInt(KEY_CODE, 0)
        val ready = code > BuildConfig.VERSION_CODE && prefs.getInt(KEY_READY_CODE, 0) == code && readyFile().exists()
        return UpdateState(
            availableVersion = prefs.getString(KEY_VERSION, null),
            availableCode = code,
            notes = prefs.getString(KEY_NOTES, "") ?: "",
            downloadSize = prefs.getLong(KEY_SIZE, 0),
            isPatch = prefs.getBoolean(KEY_IS_PATCH, false),
            ready = ready,
            lastChecked = prefs.getLong(KEY_CHECKED, 0),
            allowMobileData = prefs.getBoolean(KEY_ALLOW_METERED, false),
        )
    }

    companion object {
        private const val TAG = "FaunaryUpdate"
        private val BASE_URL = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/app-releases"
        private const val KEY_VERSION = "version"
        private const val KEY_CODE = "code"
        private const val KEY_NOTES = "notes"
        private const val KEY_SIZE = "size"
        private const val KEY_IS_PATCH = "is_patch"
        private const val KEY_CHECKED = "checked"
        private const val KEY_READY_CODE = "ready_code"
        private const val KEY_BASE_SHA = "base_sha"
        private const val KEY_BASE_SHA_TIME = "base_sha_time"
        private const val KEY_ALLOW_METERED = "allow_metered"
    }
}
