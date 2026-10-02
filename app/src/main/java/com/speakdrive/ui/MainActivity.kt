package com.speakdrive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.speakdrive.ui.navigation.ConversationRoute
import com.speakdrive.ui.navigation.HomeRoute
import com.speakdrive.ui.navigation.PrivacyRoute
import com.speakdrive.ui.navigation.ProgressRoute
import com.speakdrive.ui.navigation.SettingsRoute
import com.speakdrive.ui.navigation.SummaryRoute
import com.speakdrive.ui.navigation.VocabularyRoute
import com.speakdrive.ui.screens.ConversationScreen
import com.speakdrive.ui.screens.HomeScreen
import com.speakdrive.ui.screens.PrivacyPolicyScreen
import com.speakdrive.ui.screens.ProgressScreen
import com.speakdrive.ui.screens.SettingsScreen
import com.speakdrive.ui.screens.SummaryScreen
import com.speakdrive.ui.screens.VocabularyScreen
import com.speakdrive.ui.theme.SpeakDriveTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeakDriveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SpeakDriveNavHost()
                }
            }
        }
    }
}

@Composable
private fun SpeakDriveNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = HomeRoute) {
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
                onOpenPrivacy = { navController.navigate(PrivacyRoute) }
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
    }
}
