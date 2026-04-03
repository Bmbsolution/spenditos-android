package com.bmbsolution.spenditos.ui.screens.import_data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.repository.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for CSV Import and Statement Import flows
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importRepository: ImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    // MARK: - CSV Import Flow

    fun uploadCSV(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                csvImportState = CsvImportState.Uploading,
                error = null,
                isLoading = true
            )}

            importRepository.uploadCSV(file)
                .onSuccess { csvImport ->
                    _uiState.update { state ->
                        state.copy(
                            importId = csvImport.id,
                            csvImportState = CsvImportState.Processing,
                            isLoading = true
                        )
                    }
                    // Poll for status and load items
                    pollForImportItems(csvImport.id)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        csvImportState = CsvImportState.Error,
                        error = error.message ?: "Failed to upload CSV",
                        isLoading = false
                    )}
                }
        }
    }

    private fun pollForImportItems(importId: String) {
        viewModelScope.launch {
            var attempts = 0
            while (attempts < 30) { // Max 30 attempts (30 seconds with 1s delay)
                importRepository.getImportItems(importId)
                    .onSuccess { items ->
                        _uiState.update { state ->
                            state.copy(
                                importItems = items,
                                csvImportState = CsvImportState.Review,
                                isLoading = false
                            )
                        }
                        return@launch // Exit polling loop
                    }
                    .onFailure {
                        attempts++
                        kotlinx.coroutines.delay(1000)
                    }
            }
            // Timeout
            _uiState.update { it.copy(
                csvImportState = CsvImportState.Error,
                error = "Processing timeout. Please try again.",
                isLoading = false
            )}
        }
    }

    fun updateCSVMapping(mapping: CSVMapping) {
        val importId = _uiState.value.importId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            importRepository.updateMapping(importId, mapping)
                .onSuccess {
                    // Reload items with new mapping
                    pollForImportItems(importId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        error = error.message ?: "Failed to update mapping",
                        isLoading = false
                    )}
                }
        }
    }

    fun finalizeCSVImport(itemIds: List<String>? = null) {
        val importId = _uiState.value.importId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(
                csvImportState = CsvImportState.Importing,
                isLoading = true
            )}

            importRepository.finalizeImport(importId, itemIds)
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            importResult = result,
                            csvImportState = CsvImportState.Complete,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        csvImportState = CsvImportState.Error,
                        error = error.message ?: "Failed to finalize import",
                        isLoading = false
                    )}
                }
        }
    }

    // MARK: - Statement Import Flow

    fun uploadStatementFile(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                statementImportState = StatementImportState.Uploading,
                error = null,
                isLoading = true
            )}

            importRepository.uploadStatementFile(file)
                .onSuccess { fileId ->
                    _uiState.update { state ->
                        state.copy(
                            statementFileId = fileId,
                            statementFileType = file.extension.lowercase(),
                            statementImportState = StatementImportState.Analyzing,
                            isLoading = true
                        )
                    }
                    // Start analysis
                    analyzeStatement(fileId, file.extension.lowercase())
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        statementImportState = StatementImportState.Error,
                        error = error.message ?: "Failed to upload file",
                        isLoading = false
                    )}
                }
        }
    }

    private fun analyzeStatement(fileId: String, fileType: String) {
        viewModelScope.launch {
            importRepository.analyzeStatement(fileId, fileType)
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            statementAnalysis = result,
                            statementImportState = StatementImportState.MapColumns,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        statementImportState = StatementImportState.Error,
                        error = error.message ?: "Failed to analyze statement",
                        isLoading = false
                    )}
                }
        }
    }

    fun getStatementPreview(mapping: ColumnMapping) {
        val fileId = _uiState.value.statementFileId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(
                statementImportState = StatementImportState.Preview,
                isLoading = true
            )}

            importRepository.getStatementPreview(fileId, mapping)
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            statementPreview = result,
                            statementMapping = mapping,
                            statementImportState = StatementImportState.Preview,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        error = error.message ?: "Failed to get preview",
                        isLoading = false
                    )}
                }
        }
    }

    fun importStatement(transactionIds: List<String>? = null) {
        val fileId = _uiState.value.statementFileId ?: return
        val mapping = _uiState.value.statementMapping ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(
                statementImportState = StatementImportState.Importing,
                isLoading = true
            )}

            importRepository.importStatement(fileId, mapping, transactionIds)
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            statementResult = result,
                            statementImportState = StatementImportState.Complete,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        statementImportState = StatementImportState.Error,
                        error = error.message ?: "Failed to import statement",
                        isLoading = false
                    )}
                }
        }
    }

    // MARK: - Common Actions

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetCSVImport() {
        _uiState.update { it.copy(
            csvImportState = CsvImportState.SelectFile,
            importId = null,
            importItems = emptyList(),
            importResult = null,
            error = null,
            isLoading = false
        )}
    }

    fun resetStatementImport() {
        _uiState.update { it.copy(
            statementImportState = StatementImportState.SelectFile,
            statementFileId = null,
            statementFileType = null,
            statementAnalysis = null,
            statementPreview = null,
            statementMapping = null,
            statementResult = null,
            error = null,
            isLoading = false
        )}
    }

    fun skipItem(itemId: String) {
        _uiState.update { state ->
            state.copy(
                skippedItemIds = state.skippedItemIds + itemId
            )
        }
    }

    fun unskipItem(itemId: String) {
        _uiState.update { state ->
            state.copy(
                skippedItemIds = state.skippedItemIds - itemId
            )
        }
    }
}

// MARK: - UI State

data class ImportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,

    // CSV Import
    val csvImportState: CsvImportState = CsvImportState.SelectFile,
    val importId: String? = null,
    val importItems: List<ImportItem> = emptyList(),
    val importResult: ImportFinalizeResponse? = null,

    // Statement Import
    val statementImportState: StatementImportState = StatementImportState.SelectFile,
    val statementFileId: String? = null,
    val statementFileType: String? = null,
    val statementAnalysis: StatementAnalysisResult? = null,
    val statementMapping: ColumnMapping? = null,
    val statementPreview: StatementPreviewResult? = null,
    val statementResult: StatementImportResult? = null,

    // Common
    val skippedItemIds: Set<String> = emptySet()
)

enum class CsvImportState {
    SelectFile,
    Uploading,
    Processing,
    Review,
    Importing,
    Complete,
    Error
}

enum class StatementImportState {
    SelectFile,
    Uploading,
    Analyzing,
    MapColumns,
    Preview,
    Importing,
    Complete,
    Error
}
