package com.apkmanager.app.ui.screens.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.data.store.StoreAppItem
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the open-source app Store catalog.
 */
class StoreViewModel(
    private val storeRepository: StoreRepository
) : ViewModel() {

    private val _rawItems = MutableStateFlow<List<StoreAppItem>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val categories: StateFlow<List<String>> = _rawItems
        .map { items -> items.map { it.app.category }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredItems: StateFlow<List<StoreAppItem>> = combine(
        _rawItems,
        _searchQuery,
        _selectedCategory
    ) { items, query, cat ->
        items.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.app.name.contains(query, ignoreCase = true) ||
                    item.app.description.contains(query, ignoreCase = true) ||
                    item.app.githubRepo.contains(query, ignoreCase = true)

            val matchesCategory = cat == null || item.app.category.equals(cat, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadCatalog()
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val items = storeRepository.getStoreItems(forceRefresh)
                _rawItems.value = items
            } catch (e: Exception) {
                // Keep existing items on failure
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun installOrUpdate(item: StoreAppItem) {
        viewModelScope.launch {
            val updatedItem = item.copy(status = UpdateStatus.Downloading(0f, 0L, item.latestAsset?.size ?: 0L))
            updateItemInList(updatedItem)

            val success = storeRepository.installOrUpdateApp(item) { progressStatus ->
                updateItemInList(item.copy(status = progressStatus))
            }

            if (success) {
                val newVer = item.latestRelease?.cleanVersion ?: item.latestRelease?.tagName ?: ""
                val refreshedItem = item.copy(
                    isInstalled = true,
                    installedVersionName = newVer,
                    isUpdateAvailable = false,
                    status = UpdateStatus.UpToDate
                )
                updateItemInList(refreshedItem)
            }
        }
    }

    private fun updateItemInList(updated: StoreAppItem) {
        val current = _rawItems.value.toMutableList()
        val index = current.indexOfFirst { it.app.id == updated.app.id }
        if (index != -1) {
            current[index] = updated
            _rawItems.value = current
        }
    }

    class Factory(
        private val storeRepository: StoreRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StoreViewModel(storeRepository) as T
        }
    }
}
