package com.speakdrive.ai.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.speakdrive.ai.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts a copy of the lesson log where a tester can pick it up without ADB: the phone's Download
 * folder (Download/SpeakDrive). Nothing is sent anywhere; the tester decides what to do with the file.
 */
object DiagnosticsLogExport {
    private const val TAG = "DiagnosticsLogExport"
    private const val FOLDER = "SpeakDrive"

    /** Returns where the report was saved, as the learner would look for it, or null on failure. */
    fun saveToDownloads(context: Context): String? = runCatching {
        val name = "SpeakDrive-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val header = header(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(context, name, header) else saveInAppFolder(context, name, header)
    }.onFailure { LiveLog.w(TAG, "Could not save the diagnostics log", it) }.getOrNull()
        ?.also { LiveLog.i(TAG, "Diagnostics log saved to $it") }

    private fun saveWithMediaStore(context: Context, name: String, header: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { LiveLog.writeReport(it, header) } == true
        }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name"
    }

    /** Android 8–9: file managers can still open the app's own folder, so the report goes there. */
    private fun saveInAppFolder(context: Context, name: String, header: String): String? {
        val folder = File(context.getExternalFilesDir(null) ?: return null, "logs")
        val report = File(folder, name)
        val written = report.bufferedWriter().use { LiveLog.writeReport(it, header) }
        if (!written) {
            report.delete()
            return null
        }
        return "Android/data/${context.packageName}/files/logs/$name"
    }

    private fun header(context: Context): String {
        @Suppress("DEPRECATION")
        val app = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = app?.let { "${it.versionName} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()})" }
        return buildString {
            appendLine("SpeakDrive diagnostics log")
            appendLine("App: ${context.packageName} $version")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Live models: conversation ${BuildConfig.LIVE_MODEL_CONVERSATION}, drill ${BuildConfig.LIVE_MODEL}")
            append("Saved: ${Date()}")
        }
    }
}

/** Saves the diagnostics log for the developer; returns where it went, or null. */
fun interface DiagnosticsLogSaver {
    suspend fun save(): String?
}

@Singleton
class AndroidDiagnosticsLogSaver @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DiagnosticsLogSaver {
    override suspend fun save(): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { DiagnosticsLogExport.saveToDownloads(context) }
}
