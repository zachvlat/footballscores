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

class CricketViewModel(private val repository: LiveScoresRepository) : ViewModel() {
    
    private val _uiState = MutableStateFlow<CricketUiState>(CricketUiState.Loading)
    val uiState: StateFlow<CricketUiState> = _uiState.asStateFlow()
    
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
            _uiState.value = CricketUiState.Loading
            
            repository.getTodayCricketScores().fold(
                onSuccess = { response ->
                    _uiState.value = CricketUiState.Success(response)
                },
                onFailure = { error ->
                    _uiState.value = CricketUiState.Error(
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
                _uiState.value = CricketUiState.Loading
            } else {
                _isRefreshing.value = true
            }
            
            repository.getCricketScoresForDate(dateString).fold(
                onSuccess = { response ->
                    _uiState.value = CricketUiState.Success(response)
                    _isRefreshing.value = false
                },
                onFailure = { error ->
                    if (showLoading) {
                        _uiState.value = CricketUiState.Error(
                            message = error.message ?: "Failed to load cricket scores for selected date"
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

sealed class CricketUiState {
    object Loading : CricketUiState()
    data class Success(val response: LiveScoresResponse) : CricketUiState()
    data class Error(val message: String) : CricketUiState()
}

class CricketViewModelFactory(private val repository: LiveScoresRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CricketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CricketViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
