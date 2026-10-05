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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.speakdrive.R
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
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section(
                stringResource(R.string.privacy_voice_title),
                stringResource(R.string.privacy_voice_desc)
            )
            Section(
                stringResource(R.string.privacy_data_title),
                stringResource(R.string.privacy_data_desc)
            )
            Section(
                stringResource(R.string.privacy_third_party_title),
                stringResource(R.string.privacy_third_party_desc)
            )
            Section(
                stringResource(R.string.privacy_rights_title),
                stringResource(R.string.privacy_rights_desc)
            )
            Section(
                stringResource(R.string.privacy_driving_title),
                stringResource(R.string.privacy_driving_desc)
            )

            if (deleted) {
                Text(stringResource(R.string.privacy_deleted_success), color = MaterialTheme.colorScheme.tertiary)
            } else {
                OutlinedButton(
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.privacy_btn_delete_all)) }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.privacy_confirm_dialog_title)) },
            text = { Text(stringResource(R.string.privacy_confirm_dialog_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.deleteAll()
                }) { Text(stringResource(R.string.privacy_dialog_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.privacy_dialog_cancel)) } }
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
