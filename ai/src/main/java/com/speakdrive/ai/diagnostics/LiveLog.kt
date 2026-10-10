package com.speakdrive.ai.diagnostics

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lesson diagnostics kept on the phone, so "the AI went silent" or "it would not connect" can be
 * explained afterwards, long after logcat has rolled over. Every line goes to logcat and, once
 * [init] has run, to rolling files in the app's external files folder. Nothing leaves the phone;
 * read them over ADB:
 *
 *     adb pull /sdcard/Android/data/com.speakdrive.ai/files/logs
 */
object LiveLog {
    private const val FILE = "live.log"

    /** Older files: live.1.log (newest) ... live.N.log (oldest). */
    private const val KEPT_OLD_FILES = 2

    /** The current file rolls over at this size, so about 6 MB of history are kept. */
    const val MAX_FILE_BYTES = 2_000_000L

    @Volatile
    private var dir: File? = null

    /** All file work happens here, in order, so the audio threads never wait for the disk. */
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "speakdrive-livelog").apply { isDaemon = true }
    }
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US) // used on the writer thread only

    fun init(directory: File) {
        directory.mkdirs()
        dir = directory
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append('D', tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append('I', tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
        append('W', tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (error != null) Log.e(tag, message, error) else Log.e(tag, message)
        append('E', tag, message, error)
    }

    /** Waits until every line logged so far is on disk. */
    fun flush() {
        runCatching { writer.submit {}.get(3, TimeUnit.SECONDS) }
    }

    private fun append(level: Char, tag: String, message: String, error: Throwable?) {
        val target = dir ?: return
        val time = System.currentTimeMillis()
        val thread = Thread.currentThread().name
        writer.execute { write(target, line(stamp.format(Date(time)), level, tag, thread, message, error)) }
    }

    internal fun line(time: String, level: Char, tag: String, thread: String, message: String, error: Throwable?): String =
        buildString {
            append(time).append(' ').append(level).append('/').append(tag).append(" [").append(thread).append("] ")
            append(message)
            if (error != null) {
                append(" | ").append(error::class.java.name).append(": ").append(error.message)
                error.cause?.let { append(" | cause ").append(it::class.java.name).append(": ").append(it.message) }
            }
            append('\n')
        }

    private fun write(target: File, line: String) {
        runCatching {
            val file = File(target, FILE)
            if (file.length() > MAX_FILE_BYTES) rollOver(target, file)
            FileWriter(file, true).use { it.append(line) }
        }
    }

    private fun rollOver(target: File, current: File) {
        File(target, "live.$KEPT_OLD_FILES.log").delete()
        for (index in KEPT_OLD_FILES - 1 downTo 1) {
            File(target, "live.$index.log").renameTo(File(target, "live.${index + 1}.log"))
        }
        current.renameTo(File(target, "live.1.log"))
    }
}
