package com.zachvlat.footballscores.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zachvlat.footballscores.data.model.LiveScoresResponse
import com.zachvlat.footballscores.data.repository.LiveScoresRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class HockeyViewModel(private val repository: LiveScoresRepository) : ViewModel() {
    
    private val _uiState = MutableStateFlow<HockeyUiState>(HockeyUiState.Loading)
    val uiState: StateFlow<HockeyUiState> = _uiState.asStateFlow()
    
    private val _currentDate = MutableStateFlow(getTodayDateString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        loadTodayScores()
        startAutoRefresh()
    }
    
    fun loadTodayScores() {
        viewModelScope.launch {
            _uiState.value = HockeyUiState.Loading
            
            repository.getTodayHockeyScores().fold(
                onSuccess = { response ->
                    _uiState.value = HockeyUiState.Success(response)
                },
                onFailure = { error ->
                    _uiState.value = HockeyUiState.Error(
                        message = error.message ?: "Unknown error occurred"
                    )
                }
            )
        }
    }
    
    fun loadScoresForDate(dateString: String) {
        _currentDate.value = dateString
        loadScoresForDateInternal(dateString, showLoading = true)
    }
    
    fun refresh() {
        loadScoresForDateInternal(_currentDate.value, showLoading = false)
    }
    
    private fun loadScoresForDateInternal(dateString: String, showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = HockeyUiState.Loading
            } else {
                _isRefreshing.value = true
            }
            
            repository.getHockeyScoresForDate(dateString).fold(
                onSuccess = { response ->
                    _uiState.value = HockeyUiState.Success(response)
                    _isRefreshing.value = false
                },
                onFailure = { error ->
                    if (showLoading) {
                        _uiState.value = HockeyUiState.Error(
                            message = error.message ?: "Failed to load hockey scores for selected date"
                        )
                    }
                    _isRefreshing.value = false
                }
            )
        }
    }
    
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(60000)
                if (_currentDate.value == getTodayDateString()) {
                    refresh()
                }
            }
        }
    }
    
    fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return dateFormat.format(Date())
    }
    
    fun getYesterdayDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return dateFormat.format(calendar.time)
    }
    
    fun getTomorrowDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return dateFormat.format(calendar.time)
    }
}

sealed class HockeyUiState {
    object Loading : HockeyUiState()
    data class Success(val response: LiveScoresResponse) : HockeyUiState()
    data class Error(val message: String) : HockeyUiState()
}

class HockeyViewModelFactory(private val repository: LiveScoresRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HockeyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HockeyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
