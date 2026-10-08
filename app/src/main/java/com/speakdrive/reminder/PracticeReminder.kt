package com.speakdrive.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.speakdrive.R
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.data.local.dao.MemoryDao
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.domain.ReminderPlanner
import com.speakdrive.domain.StreakCalculator
import com.speakdrive.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily practice reminder: one notification a day, only when the learner has not practised yet,
 * at the time they chose or shortly before the time they usually practise.
 */
@Singleton
class PracticeReminder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val sessionDao: SessionDao,
    private val wordDao: WordDao,
    private val memoryDao: MemoryDao
) {
    /** Replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis
    internal var zone: () -> ZoneId = ZoneId::systemDefault

    private val state by lazy { context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE) }

    /** Keeps the schedule in line with the settings for as long as the app process lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            preferences.preferences
                .map { it.learner.practiceReminderEnabled to it.learner.practiceReminderMinute }
                .distinctUntilChanged()
                .collect { runCatching { reschedule() }.onFailure { Log.w(TAG, "Could not schedule the reminder", it) } }
        }
    }

    /** Schedules the daily reminder, or cancels it when turned off. Does nothing when already set for that time. */
    suspend fun reschedule() {
        val workManager = WorkManager.getInstance(context)
        val learner = preferences.preferences.first().learner
        if (!learner.practiceReminderEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            state.edit().remove(KEY_MINUTE).apply()
            return
        }
        val minute = reminderMinute(learner)
        val scheduled = workManager.getWorkInfosForUniqueWork(WORK_NAME).await()
            .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        if (scheduled && state.getInt(KEY_MINUTE, -1) == minute) return

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock()), zone())
        val delay = Duration.between(now, ReminderPlanner.nextTrigger(now, minute))
        val request = PeriodicWorkRequestBuilder<PracticeReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        state.edit().putInt(KEY_MINUTE, minute).apply()
        Log.i(TAG, "Practice reminder scheduled at minute $minute of the day")
    }

    /** The minute of the day the reminder fires at for these settings. */
    suspend fun reminderMinute(learner: LearnerSettings): Int {
        val now = clock()
        val recent = if (learner.practiceReminderMinute == LearnerSettings.REMINDER_AUTO) {
            sessionDao.sessionStartTimesSince(now - TimeUnit.DAYS.toMillis(HISTORY_DAYS))
        } else {
            emptyList()
        }
        return ReminderPlanner.reminderMinute(learner.practiceReminderMinute, recent, now, zone())
    }

    /** Called by the worker: posts the reminder unless the learner already practised today. */
    suspend fun remindIfNeeded(): Boolean {
        val prefs = preferences.preferences.first()
        if (!prefs.learner.practiceReminderEnabled) return false
        val zone = zone()
        val now = clock()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startTimes = sessionDao.sessionStartTimesSince(now - TimeUnit.DAYS.toMillis(STREAK_HISTORY_DAYS))
        val practiceDays = StreakCalculator.toLocalDates(startTimes, zone)
        if (today in practiceDays) return false
        if (!canNotify()) return false

        val streak = StreakCalculator.streakWithFreezes(practiceDays, today, prefs.learner.streakFreezeEnabled)
        val content = ReminderPlanner.content(
            streakDays = streak.days,
            freezesLeft = streak.freezesLeft,
            dueWords = wordDao.observeDueCount(now).first(),
            dueMistakes = memoryDao.observeDueMistakeCount(now).first(),
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            vietnamese = isVietnamese(prefs)
        )
        post(content)
        // The learner's habit may have moved since the reminder was scheduled.
        runCatching { reschedule() }
        return true
    }

    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun post(content: ReminderPlanner.Content) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminder_channel_description) }
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked", e)
        }
    }

    private fun isVietnamese(prefs: UserPreferences): Boolean = when (prefs.appLanguage) {
        AppLanguage.VIETNAMESE -> true
        AppLanguage.SYSTEM -> context.resources.configuration.locales[0]?.language == "vi"
        else -> false
    }

    internal companion object {
        const val WORK_NAME = "practice_reminder"
        const val CHANNEL_ID = "practice_reminder"
        const val NOTIFICATION_ID = 4201
        private const val TAG = "PracticeReminder"
        private const val STATE_FILE = "practice_reminder_state"
        private const val KEY_MINUTE = "scheduled_minute"
        private const val HISTORY_DAYS = 30L
        private const val STREAK_HISTORY_DAYS = 400L
    }
}
