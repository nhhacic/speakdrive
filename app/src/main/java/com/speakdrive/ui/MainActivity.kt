package com.speakdrive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.ui.components.SpeakDriveBottomBar
import com.speakdrive.ui.components.bottomNavItems
import com.speakdrive.ui.navigation.AboutRoute
import com.speakdrive.ui.navigation.ConversationRoute
import com.speakdrive.ui.navigation.HomeRoute
import com.speakdrive.ui.navigation.LearnerMemoryRoute
import com.speakdrive.ui.navigation.PrivacyRoute
import com.speakdrive.ui.navigation.ProgressRoute
import com.speakdrive.ui.navigation.SettingsRoute
import com.speakdrive.ui.navigation.SummaryRoute
import com.speakdrive.ui.navigation.VocabularyRoute
import com.speakdrive.ui.screens.AboutScreen
import com.speakdrive.ui.screens.ConversationScreen
import com.speakdrive.ui.screens.HomeScreen
import com.speakdrive.ui.screens.LearnerMemoryScreen
import com.speakdrive.ui.screens.PrivacyPolicyScreen
import com.speakdrive.ui.screens.ProgressScreen
import com.speakdrive.ui.screens.SettingsScreen
import com.speakdrive.ui.screens.SummaryScreen
import com.speakdrive.ui.screens.VocabularyScreen
import com.speakdrive.ui.theme.SpeakDriveTheme
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.playback.PlaybackConnection
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.os.Build
import android.util.Log
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var engine: ConversationEngine

    @Inject
    lateinit var playbackConnection: PlaybackConnection

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> engine.setScreenOn(false)
                Intent.ACTION_SCREEN_ON -> engine.setScreenOn(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyInitialLocale()
        enableEdgeToEdge()

        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            engine.setScreenOn(powerManager?.isInteractive ?: true)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(screenReceiver, filter)
            }
        }.onFailure { Log.w("MainActivity", "Failed to register screenReceiver", it) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(engine.state, engine.isCarConnected) { state, isCar ->
                    volumeControlStream = if (state.isInLesson && !isCar) {
                        AudioManager.STREAM_VOICE_CALL
                    } else {
                        AudioManager.STREAM_MUSIC
                    }
                }.collect()
            }
        }
        setContent {
            val prefs by userPreferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
            val appLang = prefs?.appLanguage ?: AppLanguage.SYSTEM

            val baseContext = LocalContext.current
            val localizedConfiguration = remember(appLang, baseContext) {
                val locale = if (appLang.code.isNotBlank()) {
                    Locale.forLanguageTag(appLang.code)
                } else {
                    Locale.getDefault()
                }
                Locale.setDefault(locale)
                val config = android.content.res.Configuration(baseContext.resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                @Suppress("DEPRECATION")
                baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
                @Suppress("DEPRECATION")
                baseContext.applicationContext.resources.updateConfiguration(config, baseContext.applicationContext.resources.displayMetrics)
                config
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration
            ) {
                SpeakDriveTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SpeakDriveNavHost()
                    }
                }
            }
        }
    }

    private fun applyInitialLocale() {
        try {
            val cached = getSharedPreferences("speakdrive_locale", Context.MODE_PRIVATE)
                .getString("cached_language", null)
            if (!cached.isNullOrBlank()) {
                val locale = Locale.forLanguageTag(cached)
                Locale.setDefault(locale)
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
                @Suppress("DEPRECATION")
                applicationContext.resources.updateConfiguration(config, applicationContext.resources.displayMetrics)
            }
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        if (!isChangingConfigurations) {
            runCatching { engine.setAppFocused(true) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isChangingConfigurations) {
            runCatching { engine.setAppFocused(true) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            runCatching { engine.setAppFocused(false) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        if (!engine.state.value.isInLesson) {
            runCatching { playbackConnection.release() }
        }
    }
}

@Composable
private fun SpeakDriveNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show bottom bar only on top-level tabs
    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
    }

    val back: () -> Unit = { navController.popBackStack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                SpeakDriveBottomBar(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onStartLesson = { mediaId -> navController.navigate(ConversationRoute(mediaId)) },
                    onOpenCurrentLesson = { navController.navigate(ConversationRoute(null)) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenProgress = { navController.navigate(ProgressRoute) }
                )
            }
            composable<ConversationRoute> { entry ->
                ConversationScreen(
                    mediaId = entry.toRoute<ConversationRoute>().mediaId,
                    onBack = back,
                    onLessonSaved = { sessionId ->
                        navController.navigate(SummaryRoute(sessionId)) { popUpTo(HomeRoute) }
                    },
                    onLessonDiscarded = { navController.popBackStack(HomeRoute, inclusive = false) }
                )
            }
            composable<SummaryRoute> {
                SummaryScreen(
                    onPracticeAgain = { mediaId ->
                        navController.navigate(ConversationRoute(mediaId)) { popUpTo(HomeRoute) }
                    },
                    onHome = { navController.popBackStack(HomeRoute, inclusive = false) },
                    onBack = back
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = back,
                    onOpenProgress = { navController.navigate(ProgressRoute) },
                    onOpenVocabulary = { navController.navigate(VocabularyRoute) },
                    onOpenPrivacy = { navController.navigate(PrivacyRoute) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                    onOpenMemory = { navController.navigate(LearnerMemoryRoute) }
                )
            }
            composable<LearnerMemoryRoute> {
                LearnerMemoryScreen(
                    onBack = back,
                    onStartReview = { mediaId -> navController.navigate(ConversationRoute(mediaId)) }
                )
            }
            composable<ProgressRoute> {
                ProgressScreen(
                    onBack = back,
                    onOpenSession = { navController.navigate(SummaryRoute(it)) },
                    onOpenVocabulary = { navController.navigate(VocabularyRoute) }
                )
            }
            composable<VocabularyRoute> {
                VocabularyScreen(
                    onBack = back,
                    onStartReview = { mediaId -> navController.navigate(ConversationRoute(mediaId)) }
                )
            }
            composable<PrivacyRoute> { PrivacyPolicyScreen(onBack = back) }
            composable<AboutRoute> {
                AboutScreen(
                    onBack = back,
                    onOpenPrivacy = { navController.navigate(PrivacyRoute) }
                )
            }
        }
    }
}

