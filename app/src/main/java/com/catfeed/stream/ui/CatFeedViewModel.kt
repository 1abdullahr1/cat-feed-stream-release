package com.catfeed.stream.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.data.model.FilterMode
import com.catfeed.stream.data.repository.CatFeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatFeedViewModel(
    private val repository: CatFeedRepository
) : ViewModel() {

    private val _feedItems = MutableStateFlow<List<CatItem>>(emptyList())
    val feedItems: StateFlow<List<CatItem>> = _feedItems.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterMode.ALL)
    val currentFilter: StateFlow<FilterMode> = _currentFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isEmpty = MutableStateFlow(false)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    private var likedSubscriptionJob: Job? = null
    private var isFetching = false

    init {
        viewModelScope.launch {
            repository.initLikedCache()
            loadInitialFeed(FilterMode.ALL)
        }
    }

    fun setFilter(mode: FilterMode) {
        if (_currentFilter.value == mode) return
        _currentFilter.value = mode

        if (mode == FilterMode.LIKED) {
            subscribeToLikedCats()
        } else {
            likedSubscriptionJob?.cancel()
            likedSubscriptionJob = null
            loadInitialFeed(mode)
        }
    }

    fun refresh() {
        if (_currentFilter.value == FilterMode.LIKED) {
            _isRefreshing.value = false
            return
        }
        loadInitialFeed(_currentFilter.value)
    }

    private fun loadInitialFeed(mode: FilterMode) {
        viewModelScope.launch {
            _isRefreshing.value = true
            isFetching = true
            try {
                val newCats = repository.fetchMoreCats(mode, count = 10)
                _feedItems.value = newCats
                _isEmpty.value = newCats.isEmpty()
            } catch (_: Exception) {
                // Keep existing items if any
            } finally {
                _isRefreshing.value = false
                isFetching = false
            }
        }
    }

    fun loadMore() {
        if (_currentFilter.value == FilterMode.LIKED || isFetching || _isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            isFetching = true
            try {
                val moreCats = repository.fetchMoreCats(_currentFilter.value, count = 10)
                if (moreCats.isNotEmpty()) {
                    val currentList = _feedItems.value.toMutableList()
                    val existingIds = currentList.map { it.id }.toSet()
                    val uniqueNewCats = moreCats.filter { it.id !in existingIds }
                    currentList.addAll(if (uniqueNewCats.isNotEmpty()) uniqueNewCats else moreCats)
                    _feedItems.value = currentList
                    _isEmpty.value = false
                }
            } catch (_: Exception) {
                // Silently ignore to avoid UI disruption
            } finally {
                _isLoadingMore.value = false
                isFetching = false
            }
        }
    }

    private fun subscribeToLikedCats() {
        likedSubscriptionJob?.cancel()
        likedSubscriptionJob = viewModelScope.launch {
            repository.observeLikedCats().collect { likedList ->
                _feedItems.value = likedList
                _isEmpty.value = likedList.isEmpty()
            }
        }
    }

    fun toggleLike(cat: CatItem) {
        viewModelScope.launch {
            val nowLiked = repository.toggleLike(cat)
            // Optimistic update
            val updated = _feedItems.value.map { item ->
                if (item.id == cat.id) {
                    item.copy(isLiked = nowLiked)
                } else {
                    item
                }
            }
            _feedItems.value = updated
        }
    }

    class Factory(private val repository: CatFeedRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatFeedViewModel(repository) as T
        }
    }
}
