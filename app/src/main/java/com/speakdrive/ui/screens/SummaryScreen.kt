package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    sessionId: String,
    onContinue: () -> Unit,
    onChangeTopic: () -> Unit,
    onEnd: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kết quả luyện tập") }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chủ đề: Chào hỏi cơ bản", style = MaterialTheme.typography.titleMedium)
                    Text("Thời lượng: 10:25", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Đánh giá", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreCard("Trôi chảy", "85")
                ScoreCard("Ngữ pháp", "70")
                ScoreCard("Từ vựng", "90")
            }
            
            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onChangeTopic, modifier = Modifier.weight(1f)) {
                    Text("Đổi chủ đề")
                }
                Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                    Text("Tiếp tục")
                }
            }
            TextButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                Text("Về trang chủ")
            }
        }
    }
}

@Composable
fun ScoreCard(label: String, score: String) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(score, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
