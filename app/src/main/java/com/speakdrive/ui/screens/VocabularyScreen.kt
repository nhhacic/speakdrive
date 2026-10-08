package com.speakdrive.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.speakdrive.R
import com.speakdrive.ai.evaluation.SentenceEvaluationResult
import com.speakdrive.ai.evaluation.SentenceEvaluator
import com.speakdrive.ai.evaluation.SentenceStatus
import com.speakdrive.audio.VoiceAnnouncer
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class VocabFilter { ALL, DUE, LEARNING, MASTERED }
enum class VocabViewMode { LIST, FLASHCARD }

data class VocabularyItem(val word: LearnedWordEntity, val days: Long, val isDue: Boolean)

@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val voiceAnnouncer: VoiceAnnouncer,
    private val sentenceEvaluator: SentenceEvaluator
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(VocabFilter.ALL)
    val selectedFilter: StateFlow<VocabFilter> = _selectedFilter.asStateFlow()

    private val _viewMode = MutableStateFlow(VocabViewMode.LIST)
    val viewMode: StateFlow<VocabViewMode> = _viewMode.asStateFlow()

    // Sentence challenge
    private val _activeSentenceWordId = MutableStateFlow<Long?>(null)
    val activeSentenceWordId: StateFlow<Long?> = _activeSentenceWordId.asStateFlow()

    private val _sentenceInput = MutableStateFlow("")
    val sentenceInput: StateFlow<String> = _sentenceInput.asStateFlow()

    private val _sentenceEvaluation = MutableStateFlow<SentenceEvaluationResult?>(null)
    val sentenceEvaluation: StateFlow<SentenceEvaluationResult?> = _sentenceEvaluation.asStateFlow()

    // Flashcard state
    private val _flashcardIndex = MutableStateFlow(0)
    val flashcardIndex: StateFlow<Int> = _flashcardIndex.asStateFlow()

    private val _isFlashcardFlipped = MutableStateFlow(false)
    val isFlashcardFlipped: StateFlow<Boolean> = _isFlashcardFlipped.asStateFlow()

    // Dialog state
    private val _showAddWordDialog = MutableStateFlow(false)
    val showAddWordDialog: StateFlow<Boolean> = _showAddWordDialog.asStateFlow()

    private val _showPracticeModeDialog = MutableStateFlow(false)
    val showPracticeModeDialog: StateFlow<Boolean> = _showPracticeModeDialog.asStateFlow()

    internal var clock: () -> Long = System::currentTimeMillis

    // One Room observer shared by the list and the counters, mapped off the main thread.
    private val rawWords: Flow<List<VocabularyItem>> = sessionRepository.observeAllWords()
        .map { words ->
            val currentTime = clock()
            words.map { word ->
                val days = TimeUnit.MILLISECONDS.toDays(word.nextReviewAt - currentTime)
                val isDue = word.nextReviewAt <= currentTime
                VocabularyItem(
                    word = word,
                    days = days,
                    isDue = isDue
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val words: StateFlow<List<VocabularyItem>?> = combine(
        rawWords,
        _searchQuery,
        _selectedFilter
    ) { allItems, query, filter ->
        var list = allItems
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.word.word.lowercase().contains(q) ||
                    it.word.meaning.lowercase().contains(q) ||
                    it.word.exampleSentence.lowercase().contains(q)
            }
        }
        when (filter) {
            VocabFilter.ALL -> list
            VocabFilter.DUE -> list.filter { it.isDue }
            VocabFilter.LEARNING -> list.filter { !it.isDue && it.word.reviewCount < 5 }
            VocabFilter.MASTERED -> list.filter { it.word.reviewCount >= 5 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allWordsList: StateFlow<List<VocabularyItem>> = rawWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCount: StateFlow<Int> = rawWords
        .map { it.count { item -> item.isDue } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val learningCount: StateFlow<Int> = rawWords
        .map { it.count { item -> !item.isDue && item.word.reviewCount < 5 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val masteredCount: StateFlow<Int> = rawWords
        .map { it.count { item -> item.word.reviewCount >= 5 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: VocabFilter) {
        _selectedFilter.value = filter
    }

    fun setViewMode(mode: VocabViewMode) {
        _viewMode.value = mode
        _isFlashcardFlipped.value = false
    }

    fun playAudio(text: String) {
        if (text.isNotBlank()) {
            voiceAnnouncer.announce(text)
        }
    }

    fun toggleSentencePractice(wordId: Long) {
        if (_activeSentenceWordId.value == wordId) {
            _activeSentenceWordId.value = null
            _sentenceInput.value = ""
            _sentenceEvaluation.value = null
        } else {
            _activeSentenceWordId.value = wordId
            _sentenceInput.value = ""
            _sentenceEvaluation.value = null
        }
    }

    fun updateSentenceInput(text: String) {
        _sentenceInput.value = text
    }

    fun evaluateSentence(targetWord: String) {
        val result = sentenceEvaluator.evaluate(targetWord, _sentenceInput.value)
        _sentenceEvaluation.value = result
    }

    fun saveSentenceAsExample(wordId: Long) {
        val sentence = _sentenceInput.value.trim()
        if (sentence.isNotBlank()) {
            viewModelScope.launch {
                sessionRepository.updateWordExample(wordId, sentence)
            }
        }
    }

    fun addCustomWord(word: String, meaning: String, example: String) {
        if (word.isNotBlank() && meaning.isNotBlank()) {
            viewModelScope.launch {
                sessionRepository.addCustomWord(word, meaning, example)
                _showAddWordDialog.value = false
            }
        }
    }

    fun deleteWord(wordId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteWord(wordId)
        }
    }

    fun markMastered(wordId: Long) {
        viewModelScope.launch {
            sessionRepository.markWordMastered(wordId)
        }
    }

    fun resetReview(wordId: Long) {
        viewModelScope.launch {
            sessionRepository.resetWordSchedule(wordId)
        }
    }

    fun setShowAddWordDialog(show: Boolean) {
        _showAddWordDialog.value = show
    }

    fun setShowPracticeModeDialog(show: Boolean) {
        _showPracticeModeDialog.value = show
    }

    fun nextFlashcard(size: Int) {
        if (size > 0) {
            _flashcardIndex.value = (_flashcardIndex.value + 1) % size
            _isFlashcardFlipped.value = false
            _activeSentenceWordId.value = null
        }
    }

    fun prevFlashcard(size: Int) {
        if (size > 0) {
            _flashcardIndex.value = if (_flashcardIndex.value > 0) _flashcardIndex.value - 1 else size - 1
            _isFlashcardFlipped.value = false
            _activeSentenceWordId.value = null
        }
    }

    fun flipFlashcard() {
        _isFlashcardFlipped.value = !_isFlashcardFlipped.value
    }

    /**
     * "Again" / "Mastered" on a flashcard. When the answer moves the card out of the current filter
     * (e.g. "Mastered" while showing due words), the next card slides into the same position, so the
     * index must not advance as well or one card would be skipped.
     */
    fun answerFlashcard(item: VocabularyItem, mastered: Boolean, listSize: Int) {
        if (mastered) markMastered(item.word.id) else resetReview(item.word.id)
        val staysInList = when (_selectedFilter.value) {
            VocabFilter.ALL -> true
            VocabFilter.DUE -> !mastered
            VocabFilter.LEARNING -> false
            VocabFilter.MASTERED -> mastered
        }
        if (staysInList) {
            nextFlashcard(listSize)
        } else {
            _isFlashcardFlipped.value = false
            _activeSentenceWordId.value = null
            if (_flashcardIndex.value >= listSize - 1) _flashcardIndex.value = 0
        }
    }

    override fun onCleared() {
        // A long example sentence must not keep playing after leaving the screen (or over a lesson).
        voiceAnnouncer.stop()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onBack: () -> Unit,
    onStartReview: (mediaId: String) -> Unit,
    viewModel: VocabularyViewModel = hiltViewModel()
) {
    val words by viewModel.words.collectAsStateWithLifecycle()
    val allWords by viewModel.allWordsList.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val learningCount by viewModel.learningCount.collectAsStateWithLifecycle()
    val masteredCount by viewModel.masteredCount.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    val activeSentenceWordId by viewModel.activeSentenceWordId.collectAsStateWithLifecycle()
    val sentenceInput by viewModel.sentenceInput.collectAsStateWithLifecycle()
    val sentenceEvaluation by viewModel.sentenceEvaluation.collectAsStateWithLifecycle()

    val flashcardIndex by viewModel.flashcardIndex.collectAsStateWithLifecycle()
    val isFlashcardFlipped by viewModel.isFlashcardFlipped.collectAsStateWithLifecycle()

    val showAddDialog by viewModel.showAddWordDialog.collectAsStateWithLifecycle()
    val showPracticeModeDialog by viewModel.showPracticeModeDialog.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.vocab_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setShowAddWordDialog(true) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vocab_btn_add_word), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        val list = words
        if (allWords.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.vocab_empty_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    Button(
                        onClick = { viewModel.setShowAddWordDialog(true) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.vocab_btn_add_word))
                    }
                }
            }
            if (showAddDialog) {
                AddWordDialog(
                    onDismiss = { viewModel.setShowAddWordDialog(false) },
                    onConfirm = { word, meaning, example ->
                        viewModel.addCustomWord(word, meaning, example)
                    }
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // SRS Stage Summary Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SrsSummaryCard(stringResource(R.string.vocab_tab_due), dueCount, AppColors.StreakOrange, Modifier.weight(1f))
                SrsSummaryCard(stringResource(R.string.vocab_tab_learning), learningCount, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SrsSummaryCard(stringResource(R.string.vocab_tab_mastered), masteredCount, AppColors.CorrectGreen, Modifier.weight(1f))
            }

            // Hero Action: Voice Practice with AI
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Button(
                    onClick = { viewModel.setShowPracticeModeDialog(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.vocab_btn_voice_coach),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // View Mode Tabs (List vs Flashcard)
            PrimaryTabRow(
                selectedTabIndex = if (viewMode == VocabViewMode.LIST) 0 else 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Tab(
                    selected = viewMode == VocabViewMode.LIST,
                    onClick = { viewModel.setViewMode(VocabViewMode.LIST) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.vocab_view_list), fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = viewMode == VocabViewMode.FLASHCARD,
                    onClick = { viewModel.setViewMode(VocabViewMode.FLASHCARD) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ViewCarousel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.vocab_view_flashcard), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            if (viewMode == VocabViewMode.LIST) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text(stringResource(R.string.vocab_search_hint), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == VocabFilter.ALL,
                        onClick = { viewModel.setFilter(VocabFilter.ALL) },
                        label = { Text(stringResource(R.string.vocab_filter_all, allWords.size), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == VocabFilter.DUE,
                        onClick = { viewModel.setFilter(VocabFilter.DUE) },
                        label = { Text(stringResource(R.string.vocab_filter_due, dueCount), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == VocabFilter.MASTERED,
                        onClick = { viewModel.setFilter(VocabFilter.MASTERED) },
                        label = { Text(stringResource(R.string.vocab_filter_mastered, masteredCount), fontSize = 12.sp) }
                    )
                }

                // Vocabulary List
                val displayedList = list.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayedList, key = { it.word.id }) { item ->
                        VocabularyCard(
                            item = item,
                            isActiveSentence = activeSentenceWordId == item.word.id,
                            sentenceInput = sentenceInput,
                            sentenceEvaluation = sentenceEvaluation,
                            onPlayWord = { viewModel.playAudio(item.word.word) },
                            onPlaySentence = { viewModel.playAudio(item.word.exampleSentence) },
                            onPracticePronounce = { onStartReview(MediaIds.vocabWord(item.word.word)) },
                            onToggleSentencePractice = { viewModel.toggleSentencePractice(item.word.id) },
                            onSentenceInputChange = viewModel::updateSentenceInput,
                            onEvaluateSentence = { viewModel.evaluateSentence(item.word.word) },
                            onSaveSentenceAsExample = {
                                viewModel.saveSentenceAsExample(item.word.id)
                                Toast.makeText(context, context.getString(R.string.vocab_sentence_saved_toast), Toast.LENGTH_SHORT).show()
                            },
                            onPlayCustomSentence = { viewModel.playAudio(sentenceInput) },
                            onMarkMastered = { viewModel.markMastered(item.word.id) },
                            onResetReview = { viewModel.resetReview(item.word.id) },
                            onDelete = { viewModel.deleteWord(item.word.id) }
                        )
                    }
                }
            } else {
                // Flashcard Interactive Study Mode
                val flashcardList = list.orEmpty()
                if (flashcardList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.vocab_empty_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val safeIndex = flashcardIndex.coerceIn(0, flashcardList.lastIndex)
                    val currentItem = flashcardList[safeIndex]

                    FlashcardStudyView(
                        item = currentItem,
                        currentIndex = safeIndex,
                        totalSize = flashcardList.size,
                        isFlipped = isFlashcardFlipped,
                        onFlip = viewModel::flipFlashcard,
                        onPlayWord = { viewModel.playAudio(currentItem.word.word) },
                        onPlaySentence = { viewModel.playAudio(currentItem.word.exampleSentence) },
                        onPracticeWithAi = { onStartReview(MediaIds.vocabWord(currentItem.word.word)) },
                        onNext = { viewModel.nextFlashcard(flashcardList.size) },
                        onPrev = { viewModel.prevFlashcard(flashcardList.size) },
                        onMarkAgain = { viewModel.answerFlashcard(currentItem, mastered = false, listSize = flashcardList.size) },
                        onMarkMastered = { viewModel.answerFlashcard(currentItem, mastered = true, listSize = flashcardList.size) }
                    )
                }
            }
        }
    }

    // Add Word Dialog
    if (showAddDialog) {
        AddWordDialog(
            onDismiss = { viewModel.setShowAddWordDialog(false) },
            onConfirm = { word, meaning, example ->
                viewModel.addCustomWord(word, meaning, example)
            }
        )
    }

    // Choose AI Practice Mode Dialog
    if (showPracticeModeDialog) {
        PracticeModeDialog(
            onDismiss = { viewModel.setShowPracticeModeDialog(false) },
            onSelectMode = { modeMediaId ->
                viewModel.setShowPracticeModeDialog(false)
                onStartReview(modeMediaId)
            }
        )
    }
}

@Composable
fun VocabularyCard(
    item: VocabularyItem,
    isActiveSentence: Boolean,
    sentenceInput: String,
    sentenceEvaluation: SentenceEvaluationResult?,
    onPlayWord: () -> Unit,
    onPlaySentence: () -> Unit,
    onPracticePronounce: () -> Unit,
    onToggleSentencePractice: () -> Unit,
    onSentenceInputChange: (String) -> Unit,
    onEvaluateSentence: () -> Unit,
    onSaveSentenceAsExample: () -> Unit,
    onPlayCustomSentence: () -> Unit,
    onMarkMastered: () -> Unit,
    onResetReview: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Word, Audio Icon, Due Badge, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.word.word,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onPlayWord,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.vocab_pronounce_tooltip),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val chipBg = if (item.isDue) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val chipTextColor = if (item.isDue) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val reviewLabel = when {
                        item.isDue -> stringResource(R.string.vocab_status_due)
                        item.days < 1 -> stringResource(R.string.vocab_status_today)
                        else -> stringResource(R.string.vocab_status_days, item.days)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = reviewLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = chipTextColor
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vocab_card_menu_mastered)) },
                                onClick = {
                                    menuExpanded = false
                                    onMarkMastered()
                                },
                                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vocab_card_menu_reset)) },
                                onClick = {
                                    menuExpanded = false
                                    onResetReview()
                                },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vocab_card_menu_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            // Meaning
            if (item.word.meaning.isNotBlank()) {
                Text(
                    text = item.word.meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Example Sentence
            if (item.word.exampleSentence.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "“${item.word.exampleSentence}”",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onPlaySentence, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Action Buttons: Pronounce & Make Sentence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPracticePronounce,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.vocab_practice_pronounce), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onToggleSentencePractice,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActiveSentence) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isActiveSentence) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.vocab_practice_sentence), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Expandable Sentence Practice Box
            AnimatedVisibility(visible = isActiveSentence) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.vocab_sentence_title, item.word.word),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = sentenceInput,
                            onValueChange = onSentenceInputChange,
                            placeholder = { Text(stringResource(R.string.vocab_sentence_hint, item.word.word), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onEvaluateSentence,
                                shape = RoundedCornerShape(10.dp),
                                enabled = sentenceInput.isNotBlank()
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.vocab_sentence_check_btn), fontSize = 12.sp)
                            }
                        }

                        // Evaluation result feedback
                        if (sentenceEvaluation != null) {
                            val eval = sentenceEvaluation
                            val isSuccess = eval.status == SentenceStatus.EXCELLENT || eval.status == SentenceStatus.GOOD
                            val boxBg = if (isSuccess) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(boxBg)
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = if (isSuccess) AppColors.CorrectGreen else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = eval.feedbackVi,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    val upgrade = eval.nativeUpgrade
                                    if (!upgrade.isNullOrBlank()) {
                                        Text(
                                            text = upgrade,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (isSuccess) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = onPlayCustomSentence,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.vocab_listen_sentence), fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = onSaveSentenceAsExample,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.vocab_sentence_save_example), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SRS 5-Dot Retention Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.vocab_reviewed_count, item.word.reviewCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 5 dots representing SRS mastery levels
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { stageIndex ->
                        val isFilled = item.word.reviewCount > stageIndex
                        val dotColor = if (isFilled) {
                            if (item.word.reviewCount >= 5) AppColors.CorrectGreen else MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardStudyView(
    item: VocabularyItem,
    currentIndex: Int,
    totalSize: Int,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onPlayWord: () -> Unit,
    onPlaySentence: () -> Unit,
    onPracticeWithAi: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onMarkAgain: () -> Unit,
    onMarkMastered: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.vocab_card_count_format, currentIndex + 1, totalSize),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.vocab_flashcard_flip_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / totalSize.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Main Flashcard (Clickable to Flip)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { onFlip() }
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isFlipped) {
                    // FRONT OF CARD
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = item.word.word,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            onClick = onPlayWord,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.vocab_flashcard_front_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // BACK OF CARD
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = item.word.word,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = onPlayWord, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Text(
                            text = item.word.meaning,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        if (item.word.exampleSentence.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "“${item.word.exampleSentence}”",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = onPlaySentence, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onPracticeWithAi,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.vocab_practice_word_with_ai))
                        }
                    }
                }
            }
        }

        // Assessment Buttons: Again vs Mastered
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onMarkAgain,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.vocab_flashcard_again), fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onMarkMastered,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.CorrectGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.vocab_flashcard_mastered), fontWeight = FontWeight.Bold)
            }
        }

        // Navigation Arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            IconButton(onClick = onFlip) {
                Icon(Icons.Filled.FlipCameraAndroid, contentDescription = null)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun AddWordDialog(
    onDismiss: () -> Unit,
    onConfirm: (word: String, meaning: String, example: String) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.vocab_add_dialog_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text(stringResource(R.string.vocab_add_word_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text(stringResource(R.string.vocab_add_meaning_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text(stringResource(R.string.vocab_add_example_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(word, meaning, example) },
                enabled = word.isNotBlank() && meaning.isNotBlank()
            ) {
                Text(stringResource(R.string.vocab_add_save_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun PracticeModeDialog(
    onDismiss: () -> Unit,
    onSelectMode: (mediaId: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.vocab_mode_dialog_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode(MediaIds.REVIEW) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.vocab_mode_dialog_comprehensive),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.vocab_mode_dialog_comprehensive_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode(MediaIds.vocabMode("pronunciation")) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.vocab_mode_dialog_pronounce),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.vocab_mode_dialog_pronounce_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode(MediaIds.vocabMode("sentence")) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.vocab_mode_dialog_sentence),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.vocab_mode_dialog_sentence_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SrsSummaryCard(
    label: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
