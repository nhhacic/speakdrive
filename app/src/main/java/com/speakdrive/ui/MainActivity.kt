package com.speakdrive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.speakdrive.ui.screens.HomeScreen
import com.speakdrive.ui.screens.ConversationScreen
import com.speakdrive.ui.screens.SettingsScreen
import com.speakdrive.ui.screens.SummaryScreen
import com.speakdrive.ui.theme.SpeakDriveTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeakDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onNavigateToConversation = { topicId -> 
                                    navController.navigate("conversation/$topicId")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("conversation/{topicId}") { backStackEntry ->
                            val topicId = backStackEntry.arguments?.getString("topicId") ?: ""
                            ConversationScreen(
                                topicId = topicId,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSummary = { sessionId ->
                                    navController.navigate("summary/$sessionId") {
                                        popUpTo("home")
                                    }
                                }
                            )
                        }
                        composable("summary/{sessionId}") { backStackEntry ->
                            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                            SummaryScreen(
                                sessionId = sessionId,
                                onContinue = { navController.popBackStack("home", false) },
                                onChangeTopic = { navController.popBackStack("home", false) },
                                onEnd = { navController.popBackStack("home", false) }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
