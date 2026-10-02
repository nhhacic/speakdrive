package com.speakdrive.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/**
 * @param mediaId what to start (see com.speakdrive.auto.MediaIds); null joins the lesson
 * that is already running, e.g. one started from Android Auto.
 */
@Serializable
data class ConversationRoute(val mediaId: String? = null)

@Serializable
data class SummaryRoute(val sessionId: String)

@Serializable
data object SettingsRoute

@Serializable
data object ProgressRoute

@Serializable
data object VocabularyRoute

@Serializable
data object PrivacyRoute
