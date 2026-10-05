package com.speakdrive.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.ui.res.stringResource
import com.speakdrive.R
import com.speakdrive.ui.navigation.HomeRoute
import com.speakdrive.ui.navigation.ProgressRoute
import com.speakdrive.ui.navigation.SettingsRoute
import com.speakdrive.ui.navigation.VocabularyRoute
import kotlin.reflect.KClass

sealed class BottomNavItem(
    val titleRes: Int,
    val icon: ImageVector,
    val routeClass: KClass<*>,
    val routeObject: Any
) {
    data object Home : BottomNavItem(R.string.nav_home, Icons.Filled.Home, HomeRoute::class, HomeRoute)
    data object Vocabulary : BottomNavItem(R.string.nav_vocabulary, Icons.AutoMirrored.Filled.MenuBook, VocabularyRoute::class, VocabularyRoute)
    data object Progress : BottomNavItem(R.string.nav_progress, Icons.Filled.Insights, ProgressRoute::class, ProgressRoute)
    data object Settings : BottomNavItem(R.string.nav_settings, Icons.Filled.Settings, SettingsRoute::class, SettingsRoute)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Vocabulary,
    BottomNavItem.Progress,
    BottomNavItem.Settings
)

@Composable
fun SpeakDriveBottomBar(
    navController: NavController,
    currentDestination: androidx.navigation.NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                val labelText = stringResource(item.titleRes)
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(item.routeObject) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = labelText
                        )
                    },
                    label = {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
