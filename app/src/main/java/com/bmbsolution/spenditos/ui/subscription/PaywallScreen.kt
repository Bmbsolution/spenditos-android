package com.bmbsolution.spenditos.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.billing.RevenueCatService
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType

/**
 * Paywall screen for Pro tier subscription
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onSubscribe: () -> Unit = {},
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val offerings by viewModel.offerings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val purchaseSuccess by viewModel.purchaseSuccess.collectAsState()
    val currentOffering = offerings?.current

    var selectedPackage by remember { mutableStateOf<Package?>(null) }

    // Handle purchase success
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            onSubscribe()
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade to Pro") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Unlock Premium Features",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose the plan that works best for you",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pro Features
            ProFeaturesList()

            Spacer(modifier = Modifier.height(24.dp))

            // Pricing Cards
            if (isLoading) {
                CircularProgressIndicator()
            } else if (currentOffering != null) {
                PricingOptions(
                    offering = currentOffering,
                    selectedPackage = selectedPackage,
                    onSelect = { selectedPackage = it }
                )
            } else {
                Text(
                    text = "Unable to load pricing. Please try again.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subscribe Button
            Button(
                onClick = {
                    selectedPackage?.let { packageToBuy ->
                        viewModel.purchasePackage(context as android.app.Activity, packageToBuy)
                    }
                },
                enabled = selectedPackage != null && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = selectedPackage?.let { "Subscribe Now" } ?: "Select a Plan",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Restore Purchases
            TextButton(
                onClick = { viewModel.restorePurchases() },
                enabled = !isLoading
            ) {
                Text("Restore Purchases")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer
            Text(
                text = "Subscription automatically renews unless auto-renew is turned off. You can manage your subscriptions in Google Play Store settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * List of Pro features
 */
@Composable
private fun ProFeaturesList() {
    val features = listOf(
        Triple(Icons.Default.CameraAlt, "AI Receipt Scanning", "Unlimited receipt scanning with AI"),
        Triple(Icons.Default.UploadFile, "CSV Import", "Import transactions from CSV files"),
        Triple(Icons.Default.AccountBalance, "Bank Statement Import", "AI-powered bank statement analysis"),
        Triple(Icons.Default.Assessment, "Advanced Analytics", "Detailed spending insights and reports"),
        Triple(Icons.Default.Mic, "Voice Entry", "Add expenses with voice commands"),
        Triple(Icons.Default.AllInclusive, "Unlimited Everything", "No limits on transactions or groups")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        features.forEach { (icon, title, description) ->
            FeatureRow(icon = icon, title = title, description = description)
        }
    }
}

/**
 * Single feature row
 */
@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pricing options display
 */
@Composable
private fun PricingOptions(
    offering: Offering,
    selectedPackage: Package?,
    onSelect: (Package) -> Unit
) {
    val monthlyPackage = offering.getPackageByType(PackageType.MONTHLY)
    val annualPackage = offering.getPackageByType(PackageType.ANNUAL)
    val lifetimePackage = offering.getPackageByType(PackageType.LIFETIME)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Annual (Best Value)
        annualPackage?.let {
            PricingCard(
                packageItem = it,
                isSelected = selectedPackage == it,
                onSelect = { onSelect(it) },
                badge = "BEST VALUE - Save 33%",
                badgeColor = Color(0xFF4CAF50)
            )
        }

        // Monthly
        monthlyPackage?.let {
            PricingCard(
                packageItem = it,
                isSelected = selectedPackage == it,
                onSelect = { onSelect(it) },
                badge = null,
                badgeColor = Color.Unspecified
            )
        }

        // Lifetime
        lifetimePackage?.let {
            PricingCard(
                packageItem = it,
                isSelected = selectedPackage == it,
                onSelect = { onSelect(it) },
                badge = "ONE-TIME",
                badgeColor = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

/**
 * Individual pricing card
 */
@Composable
private fun PricingCard(
    packageItem: Package,
    isSelected: Boolean,
    onSelect: () -> Unit,
    badge: String?,
    badgeColor: Color
) {
    val product = packageItem.product
    val price = product.price.formatted
    val period = when (packageItem.packageType) {
        PackageType.MONTHLY -> "/month"
        PackageType.ANNUAL -> "/year"
        PackageType.LIFETIME -> " one-time"
        else -> ""
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (packageItem.packageType) {
                                PackageType.MONTHLY -> "Monthly Pro"
                                PackageType.ANNUAL -> "Yearly Pro"
                                PackageType.LIFETIME -> "Lifetime Pro"
                                else -> "Pro"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "$price$period",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect
                    )
                }

                if (packageItem.packageType == PackageType.ANNUAL) {
                    Text(
                        text = "Save 33% compared to monthly",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // Badge
            badge?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (-8).dp)
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
