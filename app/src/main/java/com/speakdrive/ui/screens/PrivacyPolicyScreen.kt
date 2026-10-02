package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.speakdrive.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(private val sessionRepository: SessionRepository) : ViewModel() {
    private val _deleted = MutableStateFlow(false)
    val deleted = _deleted.asStateFlow()

    fun deleteAll() {
        viewModelScope.launch {
            sessionRepository.deleteAllData()
            _deleted.value = true
        }
    }
}

/** In-app summary of docs/PRIVACY_POLICY.md, plus a way to wipe local data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, viewModel: PrivacyViewModel = hiltViewModel()) {
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chính sách bảo mật") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section(
                "Âm thanh giọng nói",
                "Chỉ khi bạn đang học, âm thanh từ micro được truyền trực tiếp tới dịch vụ Gemini của Google (qua Firebase AI Logic) " +
                    "để AI nghe và trả lời. SpeakDrive không ghi âm hay lưu file âm thanh nào."
            )
            Section(
                "Nội dung hội thoại",
                "Bản ghi chữ (transcript), điểm số, lỗi sai và từ mới được lưu ngay trên điện thoại của bạn. Khi buổi học kết thúc, " +
                    "transcript được gửi tới Gemini một lần để tạo nhận xét. Ứng dụng không có máy chủ riêng và không bán dữ liệu."
            )
            Section(
                "Dịch vụ bên thứ ba",
                "Firebase (Google) xử lý yêu cầu AI và dùng App Check để chống lạm dụng. Xem chính sách của Google tại policies.google.com/privacy."
            )
            Section(
                "Quyền của bạn",
                "Bạn có thể xoá toàn bộ dữ liệu học tập bất cứ lúc nào bằng nút bên dưới, hoặc gỡ cài đặt ứng dụng."
            )
            Section(
                "An toàn khi lái xe",
                "SpeakDrive được thiết kế để dùng hoàn toàn bằng giọng nói. Luôn tập trung lái xe và tuân thủ luật giao thông; " +
                    "đừng thao tác trên điện thoại khi xe đang chạy."
            )

            if (deleted) {
                Text("Đã xoá toàn bộ dữ liệu học tập.", color = MaterialTheme.colorScheme.tertiary)
            } else {
                OutlinedButton(
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Xoá toàn bộ dữ liệu học tập") }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Xoá dữ liệu?") },
            text = { Text("Lịch sử, transcript và sổ từ vựng sẽ bị xoá vĩnh viễn khỏi điện thoại này.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.deleteAll()
                }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Huỷ") } }
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
