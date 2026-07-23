package app.lazydex.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.scraper.source.SearchResult
import app.lazydex.scraper.source.Source
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowserUiState(
    val sources: List<Source> = emptyList(),
    val selectedSource: Source? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null
)

class BrowserViewModel(
    private val sourceRegistry: SourceRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            sources = sourceRegistry.getSearchableSources()
        )
    }

    fun selectSource(source: Source?) {
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            searchQuery = "",
            searchResults = emptyList(),
            searchError = null
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun performSearch() {
        val source = _uiState.value.selectedSource ?: return
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)

        viewModelScope.launch {
            try {
                val results = source.search(query)
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchError = e.message ?: "Search failed",
                    isSearching = false
                )
            }
        }
    }
}
