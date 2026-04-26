package com.project.ava

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.ava.data.CategoryWithQuestions
import com.project.ava.data.QuestionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(val data: CategoryWithQuestions) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel(private val repository: QuestionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun loadData(qrCode: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            // Simular carga para mostrar la animación
            delay(2000)
            
            try {
                val result = repository.getCategoryWithQuestions(qrCode)
                if (result != null) {
                    _uiState.value = ChatUiState.Success(result)
                } else {
                    _uiState.value = ChatUiState.Error("Código QR no reconocido.")
                }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Error al cargar datos: ${e.message}")
            }
        }
    }
}
