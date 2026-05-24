package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.insights.PersonalityAnalytics
import com.anantva.tether.insights.PersonalityAnalyticsEngine
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalityDetailViewModel @Inject constructor(
    private val analyticsEngine: PersonalityAnalyticsEngine,
    private val tetherRepository: TetherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalityAnalytics())
    val uiState: StateFlow<PersonalityAnalytics> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            val transactions = tetherRepository.getAllConfirmedTransactions()
            val analytics = analyticsEngine.getOrCompute(transactions, forceRefresh = true)
            _uiState.value = analytics
        }
    }
}
