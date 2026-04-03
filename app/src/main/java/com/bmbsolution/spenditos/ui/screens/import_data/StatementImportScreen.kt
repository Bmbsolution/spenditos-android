package com.bmbsolution.spenditos.ui.screens.import_data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.bmbsolution.spenditos.data.model.ColumnMapping
import com.bmbsolution.spenditos.data.model.DetectedColumn
import com.bmbsolution.spenditos.data.model.PreviewTransaction
import com.bmbsolution.spenditos.data.model.StatementAnalysisResult
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Statement Import Screen with AI-powered analysis and column mapping
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val inputStream = context.contentResolver.openInputStream(selectedUri)
            val fileName = context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "statement.csv"

            val extension = fileName.substringAfterLast(".", "csv")
            val tempFile = File(context.cacheDir, "import_$fileName")
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.uploadStatementFile(tempFile)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank Statement Import") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.statementImportState == StatementImportState.Complete) {
                        TextButton(onClick = {
                            viewModel.resetStatementImport()
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
            when (uiState.statementImportState) {
                StatementImportState.SelectFile -> StatementSelectFileContent(
                    onSelectFile = { filePicker.launch("*/*") }
                )
                StatementImportState.Uploading -> UploadingContent(message = "Uploading file...")
                StatementImportState.Analyzing -> UploadingContent(message = "AI analyzing statement...")
                StatementImportState.MapColumns -> ColumnMappingContent(
                    analysis = uiState.statementAnalysis,
                    onContinue = { mapping ->
                        viewModel.getStatementPreview(mapping)
                    }
                )
                StatementImportState.Preview -> StatementPreviewContent(
                    preview = uiState.statementPreview,
                    onImport = { viewModel.importStatement() },
                    onBack = { viewModel.resetStatementImport() }
                )
                StatementImportState.Importing -> UploadingContent(message = "Importing transactions...")
                StatementImportState.Complete -> StatementCompleteContent(
                    result = uiState.statementResult,
                    onDone = onNavigateBack
                )
                StatementImportState.Error -> ErrorContent(
                    error = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.resetStatementImport() }
                )
            }

            // Error snackbar
            uiState.error?.let { error ->
                if (uiState.statementImportState != StatementImportState.Error) {
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
private fun StatementSelectFileContent(
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
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bank Statement Import",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Upload your bank statement (CSV, Excel, or PDF) and our AI will automatically detect columns and import your transactions.",
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
                    text = "Supported formats:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CSV files (.csv)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Excel files (.xlsx)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PDF statements (.pdf)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Features
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AI-Powered Features:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                FeatureRow("Automatic column detection")
                FeatureRow("Smart transaction categorization")
                FeatureRow("Duplicate detection")
                FeatureRow("Preview before importing")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Statement File")
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColumnMappingContent(
    analysis: StatementAnalysisResult?,
    onContinue: (ColumnMapping) -> Unit
) {
    if (analysis == null) return

    var dateColumn by remember { mutableStateOf(analysis.detectedColumns.find { it.suggestedType == "date" }?.columnName ?: "") }
    var descriptionColumn by remember { mutableStateOf(analysis.detectedColumns.find { it.suggestedType == "description" }?.columnName ?: "") }
    var amountColumn by remember { mutableStateOf(analysis.detectedColumns.find { it.suggestedType == "amount" }?.columnName ?: "") }
    var typeColumn by remember { mutableStateOf(analysis.detectedColumns.find { it.suggestedType == "type" }?.columnName ?: "") }
    var dateFormat by remember { mutableStateOf(analysis.detectedDateFormat ?: "yyyy-MM-dd") }

    val columnNames = analysis.detectedColumns.map { it.columnName }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // AI Confidence banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI Analysis Complete",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Confidence: ${(analysis.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Review Column Mapping",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Column dropdowns
        ColumnDropdown(
            label = "Date Column *",
            selected = dateColumn,
            options = columnNames,
            onSelect = { dateColumn = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ColumnDropdown(
            label = "Description Column *",
            selected = descriptionColumn,
            options = columnNames,
            onSelect = { descriptionColumn = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ColumnDropdown(
            label = "Amount Column *",
            selected = amountColumn,
            options = columnNames,
            onSelect = { amountColumn = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ColumnDropdown(
            label = "Type Column (Optional)",
            selected = typeColumn,
            options = columnNames + "",
            onSelect = { typeColumn = it },
            optional = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = dateFormat,
            onValueChange = { dateFormat = it },
            label = { Text("Date Format") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("e.g., yyyy-MM-dd, MM/dd/yyyy") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sample data preview
        if (analysis.sampleRows.isNotEmpty()) {
            Text(
                text = "Sample Data",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    analysis.sampleRows.take(3).forEach { row ->
                        Text(
                            text = row.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (dateColumn.isNotBlank() && descriptionColumn.isNotBlank() && amountColumn.isNotBlank()) {
                    onContinue(ColumnMapping(
                        dateColumn = dateColumn,
                        descriptionColumn = descriptionColumn,
                        amountColumn = amountColumn,
                        typeColumn = typeColumn.takeIf { it.isNotBlank() },
                        dateFormat = dateFormat.takeIf { it.isNotBlank() }
                    ))
                }
            },
            enabled = dateColumn.isNotBlank() && descriptionColumn.isNotBlank() && amountColumn.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Preview Transactions")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    optional: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                if (option.isNotBlank() || optional) {
                    DropdownMenuItem(
                        text = { Text(option.ifBlank { "None" }) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatementPreviewContent(
    preview: com.bmbsolution.spenditos.data.model.StatementPreviewResult?,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    if (preview == null) return

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    currencyFormat.currency = Currency.getInstance("USD")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Summary
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Import Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Transactions:")
                    Text("${preview.previewTransactions.size}", fontWeight = FontWeight.Bold)
                }

                if (preview.duplicateCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Duplicates detected:")
                        Text(
                            "${preview.duplicateCount}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total amount:")
                    Text(
                        currencyFormat.format(preview.totalAmount),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Transactions list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(preview.previewTransactions, key = { it.id }) { transaction ->
                PreviewTransactionCard(transaction = transaction)
            }
        }

        // Actions
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }

                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import ${preview.previewTransactions.size}")
                }
            }
        }
    }
}

@Composable
private fun PreviewTransactionCard(
    transaction: PreviewTransaction
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    currencyFormat.currency = Currency.getInstance("USD")

    val isExpense = transaction.type == "expense"
    val amountText = currencyFormat.format(kotlin.math.abs(transaction.amount))
    val prefix = if (isExpense) "-" else "+"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (transaction.isDuplicate) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2
                )
                Text(
                    text = transaction.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.isDuplicate) {
                    Text(
                        text = "Duplicate detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "$prefix$amountText",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatementCompleteContent(
    result: com.bmbsolution.spenditos.data.model.StatementImportResult?,
    onDone: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    currencyFormat.currency = Currency.getInstance("USD")

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
                    if (r.duplicateCount > 0) {
                        ResultRow("Duplicates skipped", r.duplicateCount, MaterialTheme.colorScheme.error)
                    }
                    if (r.skippedCount > 0) {
                        ResultRow("Skipped", r.skippedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total amount:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            currencyFormat.format(r.totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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
