package com.speakdrive.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakdrive.R
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.CustomScenario
import com.speakdrive.ai.model.Topic
import com.speakdrive.auto.MediaIds

// Bottom sheets opened from the home screen and the topic list.

@Composable
internal fun TopicSheet(
    topic: Topic,
    isVi: Boolean,
    onPick: (mediaId: String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(topic.emoji, fontSize = 28.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (isVi) topic.titleVi else topic.titleEn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (isVi) topic.titleEn else topic.titleVi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { onPick(MediaIds.topic(topic.id)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_topic_sheet_free_talk), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onPick(MediaIds.pronunciation(topic.id)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_topic_sheet_pronunciation))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onPick(MediaIds.story(topic.id, "${TopicManager.DYNAMIC_PREFIX}story_recommended_${topic.id}")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_topic_sheet_story))
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.home_topic_sheet_roleplay_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        // Dynamic Scenario Discovery
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    onPick(MediaIds.scenario("${TopicManager.DYNAMIC_PREFIX}explore_${topic.id}"))
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_topic_sheet_ai_scenario_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.home_topic_sheet_ai_scenario_desc, if (isVi) topic.titleVi else topic.titleEn)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        // Static Scenarios
        topic.scenarios.forEach { scenario ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                .clickable { onPick(MediaIds.scenario(scenario.id)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(if (isVi) scenario.titleVi else scenario.titleEn, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.home_topic_sheet_role_ai, scenario.aiRole))
                            scenario.missionObjective?.let { mission ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🎯 $mission",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
internal fun StorySheet(
    storyTopics: List<Topic>,
    recentStories: List<CompletedSession>,
    unfinishedStory: CompletedSession? = null,
    isVi: Boolean,
    onPick: (mediaId: String) -> Unit
) {
    var customTopicInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("🎧", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.home_story_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_story_sheet_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (unfinishedStory != null) {
            Card(
                onClick = { onPick(MediaIds.STORY_RESUME) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    headlineContent = { Text(stringResource(R.string.home_story_sheet_resume_title), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(stringResource(R.string.home_story_sheet_resume_desc)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        // Quick Pick
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPick(MediaIds.STORY_RECOMMENDED) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_story_sheet_ai_pick), style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = { onPick(MediaIds.STORY_RANDOM) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.home_story_sheet_random), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Custom Topic Input
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_story_sheet_custom_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customTopicInput,
                        onValueChange = { customTopicInput = it },
                        placeholder = { Text(stringResource(R.string.home_story_sheet_custom_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val slug = TopicManager.slugify(customTopicInput.trim())
                            if (slug.isNotBlank()) {
                                onPick(MediaIds.story("story_famous", "${TopicManager.DYNAMIC_PREFIX}story_custom_$slug"))
                            }
                        },
                        enabled = customTopicInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.home_story_sheet_custom_btn))
                    }
                }
            }
        }

        // Recent Stories Section
        if (recentStories.isNotEmpty()) {
            Text(stringResource(R.string.home_story_sheet_recent_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            recentStories.forEach { session ->
                val topic = storyTopics.find { it.id == session.topicId }
                val topicTitle = if (isVi) (topic?.titleVi ?: session.topicId) else (topic?.titleEn ?: session.topicId)
                val scenarioTitle = session.scenarioId?.let { sId ->
                    topic?.scenarios?.firstOrNull { it.id == sId }?.let { if (isVi) it.titleVi else it.titleEn }
                } ?: topicTitle
                val statusText = if (session.isCompleted) stringResource(R.string.home_story_sheet_completed) else stringResource(R.string.home_story_sheet_in_progress)
                val targetScenarioId = session.scenarioId ?: "${TopicManager.DYNAMIC_PREFIX}story_recommended_${session.topicId}"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(MediaIds.story(session.topicId, targetScenarioId)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    ListItem(
                        headlineContent = { Text(scenarioTitle, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("$topicTitle • $statusText") },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        // Story Topics & Scenarios
        Text(stringResource(R.string.home_story_sheet_explore_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        storyTopics.forEach { topic ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(topic.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(if (isVi) topic.titleVi else topic.titleEn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (isVi) topic.titleEn else topic.titleVi, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Dynamic topic recommendation
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(MediaIds.story(topic.id, "${TopicManager.DYNAMIC_PREFIX}story_recommended_${topic.id}"))
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        val topicTitle = if (isVi) topic.titleVi else topic.titleEn
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.home_story_sheet_generate_new, topicTitle), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    topic.scenarios.forEach { scenario ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(MediaIds.story(topic.id, scenario.id)) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            ListItem(
                                headlineContent = { Text(if (isVi) scenario.titleVi else scenario.titleEn, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall) },
                                supportingContent = { Text(if (isVi) scenario.titleEn else scenario.titleVi, style = MaterialTheme.typography.labelSmall) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomScenarioCard(
    scenario: CustomScenario,
    isVi: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎯", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isVi) scenario.titleVi else scenario.titleEn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "AI: ${scenario.aiRole} • You: ${scenario.learnerRole}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.memory_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** "Nhập vai": an AI-made scenario on the last topic, the learner's own scenarios, or a way into the topic list. */
@Composable
internal fun RoleplaySheet(
    lastTopic: Topic?,
    customScenarios: List<CustomScenario>,
    isVi: Boolean,
    onPick: (mediaId: String) -> Unit,
    onDeleteCustomScenario: (String) -> Unit,
    onOpenAllTopics: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("🎭", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.home_topic_sheet_roleplay_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_roleplay_sheet_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val topicId = lastTopic?.id ?: "daily"
        val topicTitle = lastTopic?.let { if (isVi) it.titleVi else it.titleEn }
        Card(
            onClick = { onPick(MediaIds.scenario("${TopicManager.DYNAMIC_PREFIX}explore_$topicId")) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_topic_sheet_ai_scenario_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = topicTitle?.let { title ->
                    { Text(stringResource(R.string.home_topic_sheet_ai_scenario_desc, title)) }
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        if (customScenarios.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_custom_scenarios_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            customScenarios.forEach { scenario ->
                CustomScenarioCard(
                    scenario = scenario,
                    isVi = isVi,
                    onPlay = { onPick(MediaIds.scenario(scenario.id)) },
                    onDelete = { onDeleteCustomScenario(scenario.id) }
                )
            }
        }

        OutlinedButton(
            onClick = onOpenAllTopics,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_roleplay_sheet_by_topic))
        }
    }
}
