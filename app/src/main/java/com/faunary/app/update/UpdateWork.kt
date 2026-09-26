package com.faunary.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.faunary.app.BuildConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two kinds of runs: a "check" (tiny JSON, any network) that downloads right away when data is
 * cheap, and a "download" constrained to Wi-Fi unless the user allowed mobile data.
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateRepository,
    private val scheduler: UpdateScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val forceDownload = inputData.getBoolean(KEY_DOWNLOAD, false)
        val allowNow = forceDownload || updates.state.value.allowMobileData || !updates.isMetered()
        val ok = updates.checkAndDownload(download = allowNow)
        val s = updates.state.value
        if (ok && s.hasUpdate && !s.ready && !allowNow) scheduler.downloadWhenOnWifi()
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_DOWNLOAD = "download"
    }
}

@Singleton
class UpdateScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    private val enabled get() = BuildConfig.SUPABASE_URL.isNotBlank() && !BuildConfig.DEBUG

    /** On app start and every 12 hours: check, and download if on Wi-Fi (or mobile data is allowed). */
    fun start() {
        if (!enabled) return
        checkNow()
        val periodic = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork("faunary-update-periodic", ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun checkNow() {
        if (!enabled) return
        enqueue(download = false, network = NetworkType.CONNECTED, name = "faunary-update-check")
    }

    /** User tapped "download now": explicit consent to use whatever network is available. */
    fun downloadNow() {
        if (!enabled) return
        enqueue(download = true, network = NetworkType.CONNECTED, name = "faunary-update-download")
    }

    fun downloadWhenOnWifi() {
        enqueue(download = true, network = NetworkType.UNMETERED, name = "faunary-update-download")
    }

    private fun enqueue(download: Boolean, network: NetworkType, name: String) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(workDataOf(UpdateWorker.KEY_DOWNLOAD to download))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Hands a verified APK to the system installer (the user confirms with one tap). */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updates: UpdateRepository,
) {
    /** Android 8+: the user must allow Faunary to install apps once. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        val apk = updates.readyApk ?: return@withContext false
        updates.clearError()
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                // Android 12+: once Faunary is the installer of record, later updates may skip the prompt.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("faunary.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 256 * 1024) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(context, sessionId, Intent(context, InstallResultReceiver::class.java), flags)
                session.commit(callback.intentSender)
            }
            true
        }.onFailure { Log.w("FaunaryUpdate", "install failed", it) }.getOrDefault(false)
    }
}

/** Receives the installer status; shows the system confirmation dialog when it's needed. */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject lateinit var updates: UpdateRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts on the new version
            else -> {
                Log.w("FaunaryUpdate", "install status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                updates.reportInstallFailed()
            }
        }
    }
}
