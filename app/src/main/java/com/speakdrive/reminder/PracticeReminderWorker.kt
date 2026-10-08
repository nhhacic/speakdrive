package com.speakdrive.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/** Fires once a day; [PracticeReminder] decides whether a notification is needed. */
class PracticeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun practiceReminder(): PracticeReminder
    }

    override suspend fun doWork(): Result {
        val reminder = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java).practiceReminder()
        return try {
            reminder.remindIfNeeded()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PracticeReminderWorker", "Reminder failed", e)
            Result.retry()
        }
    }
}
