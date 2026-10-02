package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Độ khó", style = MaterialTheme.typography.titleMedium)
            var selectedDifficulty by remember { mutableStateOf("Beginner") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Beginner", "Intermediate", "Advanced").forEach { diff ->
                    FilterChip(
                        selected = diff == selectedDifficulty,
                        onClick = { selectedDifficulty = diff },
                        label = { Text(diff) }
                    )
                }
            }
            
            Divider()
            
            Text("Giọng AI", style = MaterialTheme.typography.titleMedium)
            var selectedVoice by remember { mutableStateOf("Nam") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Nam", "Nữ").forEach { voice ->
                    FilterChip(
                        selected = voice == selectedVoice,
                        onClick = { selectedVoice = voice },
                        label = { Text(voice) }
                    )
                }
            }

            Divider()

            ListItem(
                headlineContent = { Text("Lịch sử học tập") },
                modifier = Modifier.fillMaxWidth()
            )
            ListItem(
                headlineContent = { Text("Thông tin ứng dụng") },
                modifier = Modifier.fillMaxWidth()
            )
            ListItem(
                headlineContent = { Text("Chính sách bảo mật") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
