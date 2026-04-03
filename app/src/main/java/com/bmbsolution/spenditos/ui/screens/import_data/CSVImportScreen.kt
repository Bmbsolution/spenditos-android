package com.bmbsolution.spenditos.ui.screens.import_data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.model.CSVMapping
import com.bmbsolution.spenditos.data.model.ImportItem
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * CSV Import Screen with multi-step import flow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CSVImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Copy file to temp location and upload
            val inputStream = context.contentResolver.openInputStream(selectedUri)
            val fileName = context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "import.csv"

            val tempFile = File(context.cacheDir, fileName)
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.uploadCSV(tempFile)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CSV Import") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.csvImportState == CsvImportState.Complete) {
                        TextButton(onClick = {
                            viewModel.resetCSVImport()
                        }) {
                            Text("Import Another")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.csvImportState) {
                CsvImportState.SelectFile -> CSVSelectFileContent(
                    onSelectFile = { filePicker.launch("text/csv") }
                )
                CsvImportState.Uploading -> UploadingContent(message = "Uploading CSV file...")
                CsvImportState.Processing -> UploadingContent(message = "Processing file...")
                CsvImportState.Review -> CSVReviewContent(
                    items = uiState.importItems,
                    skippedItemIds = uiState.skippedItemIds,
                    onSkipItem = { viewModel.skipItem(it) },
                    onUnskipItem = { viewModel.unskipItem(it) },
                    onImport = { viewModel.finalizeCSVImport(uiState.importItems.filter { it.id !in uiState.skippedItemIds }.map { it.id }) }
                )
                CsvImportState.Importing -> UploadingContent(message = "Importing transactions...")
                CsvImportState.Complete -> CSVCompleteContent(
                    result = uiState.importResult,
                    onDone = onNavigateBack
                )
                CsvImportState.Error -> ErrorContent(
                    error = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.resetCSVImport() }
                )
            }

            // Error snackbar
            uiState.error?.let { error ->
                if (uiState.csvImportState != CsvImportState.Error) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CSVSelectFileContent(
    onSelectFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Import from CSV",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Upload a CSV file with your transactions. We'll automatically detect columns and let you review before importing.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Expected CSV format:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "• Date column (e.g., 2024-01-15)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Description column",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Amount column (positive for income, negative for expense)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Optional: Type column (income/expense)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Optional: Category column",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select CSV File")
        }
    }
}

@Composable
private fun UploadingContent(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Import Failed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Composable
private fun CSVReviewContent(
    items: List<ImportItem>,
    skippedItemIds: Set<String>,
    onSkipItem: (String) -> Unit,
    onUnskipItem: (String) -> Unit,
    onImport: () -> Unit
) {
    val importableCount = items.count { it.id !in skippedItemIds && it.status != "error" }
    val skippedCount = skippedItemIds.size

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Summary card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Review Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ready to import:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$importableCount",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (skippedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Skipped:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$skippedCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Items list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val isSkipped = item.id in skippedItemIds
                val hasError = item.status == "error"

                ImportItemCard(
                    item = item,
                    isSkipped = isSkipped,
                    hasError = hasError,
                    onToggleSkip = {
                        if (isSkipped) onUnskipItem(item.id) else onSkipItem(item.id)
                    }
                )
            }
        }

        // Import button
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onImport,
                enabled = importableCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import $importableCount Transactions")
            }
        }
    }
}

@Composable
private fun ImportItemCard(
    item: ImportItem,
    isSkipped: Boolean,
    hasError: Boolean,
    onToggleSkip: () -> Unit
) {
    val parsed = item.parsedData
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    currencyFormat.currency = Currency.getInstance("USD")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> MaterialTheme.colorScheme.errorContainer
                isSkipped -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parsed?.description ?: item.rowData.values.firstOrNull() ?: "Unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2
                    )

                    parsed?.date?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                parsed?.amount?.let { amount ->
                    val isExpense = parsed.type == "expense" || amount < 0
                    val amountText = currencyFormat.format(kotlin.math.abs(amount))
                    val prefix = if (isExpense) "-" else "+"

                    Text(
                        text = "$prefix$amountText",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (hasError) {
                Text(
                    text = item.errorMessage ?: "Error parsing this row",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Skip toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onToggleSkip) {
                    Icon(
                        imageVector = if (isSkipped) Icons.Default.AddCircle else Icons.Default.RemoveCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSkipped) "Include" else "Skip")
                }
            }
        }
    }
}

@Composable
private fun CSVCompleteContent(
    result: com.bmbsolution.spenditos.data.model.ImportFinalizeResponse?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Import Complete!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        result?.let { r ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ResultRow("Imported", r.importedCount, MaterialTheme.colorScheme.primary)
                    if (r.skippedCount > 0) {
                        ResultRow("Skipped", r.skippedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r.errorCount > 0) {
                        ResultRow("Errors", r.errorCount, MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ResultRow(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
