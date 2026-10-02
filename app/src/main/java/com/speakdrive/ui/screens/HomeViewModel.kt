package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Topic(val id: String, val title: String, val subtitle: String, val progress: Float)

data class HomeUiState(
    val streakDays: Int = 0,
    val totalMinutesToday: Int = 0,
    val wordsLearned: Int = 0,
    val isAndroidAutoConnected: Boolean = false,
    val topics: List<Topic> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Simulate loading
            val dummyTopics = listOf(
                Topic("1", "Chào hỏi cơ bản", "Basic Greetings", 0.8f),
                Topic("2", "Tại nhà hàng", "At the Restaurant", 0.5f),
                Topic("3", "Mua sắm", "Shopping", 0.2f),
                Topic("4", "Chỉ đường", "Directions", 0.0f),
                Topic("5", "Sở thích", "Hobbies", 0.0f),
                Topic("6", "Du lịch", "Travel", 0.0f),
                Topic("7", "Công việc", "Work", 0.0f),
                Topic("8", "Tin tức", "News", 0.0f)
            )
            _uiState.value = HomeUiState(
                streakDays = 5,
                totalMinutesToday = 15,
                wordsLearned = 42,
                isAndroidAutoConnected = false,
                topics = dummyTopics,
                isLoading = false
            )
        }
    }
}
