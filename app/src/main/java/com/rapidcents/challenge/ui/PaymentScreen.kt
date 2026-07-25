package com.rapidcents.challenge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rapidcents.challenge.contract.PaymentMethod
import com.rapidcents.challenge.contract.TransactionSnapshot

/**
 * Main bottom navigation screens.
 */
private enum class PaymentAppScreen {
    PAYMENT,
    HISTORY,
    TESTS
}

/**
 * Main application screen.
 *
 * Payment:
 * - Enter invoice and amount
 * - Select payment method and gateway scenario
 * - Complete transaction
 *
 * History:
 * - Display saved transactions
 *
 * Tests:
 * - Display verified test cases
 */
@Composable
fun PaymentScreen(
    uiState: PaymentUiState,
    onInvoiceChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onInvoiceOverrideChanged: (Boolean) -> Unit,
    onDemoScenarioChanged: (DemoScenario) -> Unit,
    onContinueToPaymentOptions: () -> Unit,
    onPaymentMethodChanged: (PaymentMethod) -> Unit,
    onBackToPaymentDetails: () -> Unit,
    onStartSale: () -> Unit,
    onCancel: () -> Unit,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedScreen by remember {
        mutableStateOf(PaymentAppScreen.PAYMENT)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            PaymentBottomNavigation(
                selectedScreen = selectedScreen,
                onScreenSelected = { newScreen ->
                    selectedScreen = newScreen
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedScreen) {
                PaymentAppScreen.PAYMENT -> {
                    PaymentInterface(
                        uiState = uiState,
                        onInvoiceChanged = onInvoiceChanged,
                        onAmountChanged = onAmountChanged,
                        onInvoiceOverrideChanged =
                            onInvoiceOverrideChanged,
                        onDemoScenarioChanged =
                            onDemoScenarioChanged,
                        onContinueToPaymentOptions =
                            onContinueToPaymentOptions,
                        onPaymentMethodChanged =
                            onPaymentMethodChanged,
                        onBackToPaymentDetails =
                            onBackToPaymentDetails,
                        onStartSale = onStartSale,
                        onCancel = onCancel,
                        onRecover = onRecover
                    )
                }

                PaymentAppScreen.HISTORY -> {
                    HistoryInterface(
                        transactions =
                            uiState.transactionHistory
                    )
                }

                PaymentAppScreen.TESTS -> {
                    TestCasesInterface()
                }
            }
        }
    }
}

/**
 * Bottom navigation bar.
 */
@Composable
private fun PaymentBottomNavigation(
    selectedScreen: PaymentAppScreen,
    onScreenSelected: (PaymentAppScreen) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected =
                selectedScreen == PaymentAppScreen.PAYMENT,
            onClick = {
                onScreenSelected(
                    PaymentAppScreen.PAYMENT
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Payment"
                )
            },
            label = {
                Text("Payment")
            }
        )

        NavigationBarItem(
            selected =
                selectedScreen == PaymentAppScreen.HISTORY,
            onClick = {
                onScreenSelected(
                    PaymentAppScreen.HISTORY
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History"
                )
            },
            label = {
                Text("History")
            }
        )

        NavigationBarItem(
            selected =
                selectedScreen == PaymentAppScreen.TESTS,
            onClick = {
                onScreenSelected(
                    PaymentAppScreen.TESTS
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Tests"
                )
            },
            label = {
                Text("Tests")
            }
        )
    }
}

/**
 * Two-step payment interface.
 */
@Composable
private fun PaymentInterface(
    uiState: PaymentUiState,
    onInvoiceChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onInvoiceOverrideChanged: (Boolean) -> Unit,
    onDemoScenarioChanged: (DemoScenario) -> Unit,
    onContinueToPaymentOptions: () -> Unit,
    onPaymentMethodChanged: (PaymentMethod) -> Unit,
    onBackToPaymentDetails: () -> Unit,
    onStartSale: () -> Unit,
    onCancel: () -> Unit,
    onRecover: () -> Unit
) {
    when (uiState.currentCheckoutStep) {
        CheckoutStep.PAYMENT_DETAILS -> {
            PaymentDetailsInterface(
                uiState = uiState,
                onInvoiceChanged = onInvoiceChanged,
                onAmountChanged = onAmountChanged,
                onInvoiceOverrideChanged =
                    onInvoiceOverrideChanged,
                onContinue =
                    onContinueToPaymentOptions
            )
        }

        CheckoutStep.PAYMENT_OPTIONS -> {
            PaymentOptionsInterface(
                uiState = uiState,
                onPaymentMethodChanged =
                    onPaymentMethodChanged,
                onDemoScenarioChanged =
                    onDemoScenarioChanged,
                onBack =
                    onBackToPaymentDetails,
                onCompleteTransaction =
                    onStartSale,
                onCancel = onCancel,
                onRecover = onRecover
            )
        }
    }
}

/**
 * Step 1:
 * Enter invoice and amount.
 */
@Composable
private fun PaymentDetailsInterface(
    uiState: PaymentUiState,
    onInvoiceChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onInvoiceOverrideChanged: (Boolean) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "New Payment",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Enter the transaction details.",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Transaction Details",
                    style =
                        MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = uiState.invoiceId,
                    onValueChange = onInvoiceChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Invoice ID")
                    },
                    placeholder = {
                        Text("INV-001")
                    },
                    supportingText = {
                        Text("Enter a unique invoice ID.")
                    },
                    enabled = !uiState.isProcessing,
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.amountText,
                    onValueChange = onAmountChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Amount (CAD)")
                    },
                    placeholder = {
                        Text("10.50")
                    },
                    supportingText = {
                        Text("Enter the transaction amount.")
                    },
                    enabled = !uiState.isProcessing,
                    singleLine = true
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked =
                            uiState.explicitInvoiceOverride,
                        onCheckedChange =
                            onInvoiceOverrideChanged,
                        enabled = !uiState.isProcessing
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text =
                                "Duplicate Invoice Override",
                            style =
                                MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text =
                                "Allow intentional reuse of an existing invoice.",
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        uiState.errorMessage?.let { errorMessage ->
            ErrorCard(
                message = errorMessage
            )
        }

        Button(
            onClick = onContinue,
            enabled = !uiState.isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Sale")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}

/**
 * Step 2:
 * Select payment method and gateway scenario.
 */
@Composable
private fun PaymentOptionsInterface(
    uiState: PaymentUiState,
    onPaymentMethodChanged: (PaymentMethod) -> Unit,
    onDemoScenarioChanged: (DemoScenario) -> Unit,
    onBack: () -> Unit,
    onCompleteTransaction: () -> Unit,
    onCancel: () -> Unit,
    onRecover: () -> Unit
) {
    val demoMessages =
        DemoMessageFactory.messagesFor(
            uiState.demoScenario
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Payment Options",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text =
                "${uiState.invoiceId} • CAD ${uiState.amountText}",
            style = MaterialTheme.typography.titleMedium
        )

        HorizontalDivider()

        PaymentMethodSelector(
            selectedMethod =
                uiState.selectedPaymentMethod,
            enabled = !uiState.isProcessing,
            onPaymentMethodChanged =
                onPaymentMethodChanged
        )

        GatewayScenarioCard(
            uiState = uiState,
            onDemoScenarioChanged =
                onDemoScenarioChanged
        )

        uiState.errorMessage?.let { errorMessage ->
            ErrorCard(
                message = errorMessage
            )
        }

        Button(
            onClick = onCompleteTransaction,
            enabled =
                !uiState.isProcessing &&
                        uiState.selectedPaymentMethod != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isProcessing) {
                CircularProgressIndicator()
            } else {
                Text("Complete Transaction")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !uiState.isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }

        OutlinedButton(
            onClick = onRecover,
            enabled = !uiState.isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Recovery")
        }

        if (uiState.isProcessing) {
            ProcessingCard()
        }

        StatusCard(
            statusMessage =
                uiState.statusMessage,
            transaction =
                uiState.currentTransaction
        )

        TransactionResultBanner(
            scenario = uiState.demoScenario
        )

        HostMessageConsole(
            messages = demoMessages
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}

/**
 * Tap / Chip / Swipe / Key-in selector.
 */
@Composable
private fun PaymentMethodSelector(
    selectedMethod: PaymentMethod?,
    enabled: Boolean,
    onPaymentMethodChanged: (PaymentMethod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Payment Method",
                style = MaterialTheme.typography.titleLarge
            )

            PaymentMethod.entries
                .chunked(2)
                .forEach { rowMethods ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        rowMethods.forEach { method ->
                            val isSelected =
                                selectedMethod == method

                            if (isSelected) {
                                Button(
                                    onClick = {
                                        onPaymentMethodChanged(
                                            method
                                        )
                                    },
                                    enabled = enabled,
                                    modifier =
                                        Modifier.weight(1f)
                                ) {
                                    Text(
                                        text =
                                            method.displayName
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onPaymentMethodChanged(
                                            method
                                        )
                                    },
                                    enabled = enabled,
                                    modifier =
                                        Modifier.weight(1f)
                                ) {
                                    Text(
                                        text =
                                            method.displayName
                                    )
                                }
                            }
                        }

                        if (rowMethods.size == 1) {
                            Spacer(
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }
                }
        }
    }
}

/**
 * Gateway simulator scenario card.
 */
@Composable
private fun GatewayScenarioCard(
    uiState: PaymentUiState,
    onDemoScenarioChanged: (DemoScenario) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            DemoScenarioSelector(
                selectedScenario =
                    uiState.demoScenario,
                enabled = !uiState.isProcessing,
                onScenarioSelected =
                    onDemoScenarioChanged
            )
        }
    }
}

/**
 * Gateway simulator scenario selector.
 */
@Composable
private fun DemoScenarioSelector(
    selectedScenario: DemoScenario,
    enabled: Boolean,
    onScenarioSelected: (DemoScenario) -> Unit
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Gateway Scenario",
            style = MaterialTheme.typography.titleLarge
        )

        DemoScenario.entries.forEach { scenario ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                RadioButton(
                    selected =
                        selectedScenario == scenario,
                    onClick = {
                        onScenarioSelected(scenario)
                    },
                    enabled = enabled
                )

                Text(
                    text = scenario.displayName
                )
            }
        }
    }
}

/**
 * Processing state card.
 */
@Composable
private fun ProcessingCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            CircularProgressIndicator()

            Text(
                text = "Processing transaction..."
            )
        }
    }
}

/**
 * Error message card.
 */
@Composable
private fun ErrorCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Current transaction status.
 */
@Composable
private fun StatusCard(
    statusMessage: String,
    transaction: TransactionSnapshot?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Current Transaction",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.headlineSmall
            )

            if (transaction == null) {
                Text(
                    text =
                        "No transaction has been processed yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                HorizontalDivider()

                TransactionDetailRow(
                    label = "Request ID",
                    value = transaction.requestId
                )

                TransactionDetailRow(
                    label = "Invoice ID",
                    value = transaction.invoiceId
                )

                TransactionDetailRow(
                    label = "Payment Method",
                    value =
                        transaction.paymentMethod.displayName
                )

                TransactionDetailRow(
                    label = "State",
                    value = transaction.state.name
                )

                transaction.outcomeCode?.let { outcomeCode ->
                    TransactionDetailRow(
                        label = "Outcome",
                        value = outcomeCode
                    )
                }
            }
        }
    }
}

/**
 * Transaction history interface.
 */
@Composable
private fun HistoryInterface(
    transactions: List<TransactionSnapshot>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Transaction History",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text =
                "${transactions.size} transaction(s) recorded in the journal.",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        TransactionHistorySection(
            transactions = transactions
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}

/**
 * Transaction history list.
 */
@Composable
private fun TransactionHistorySection(
    transactions: List<TransactionSnapshot>
) {
    if (transactions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Transactions",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Completed transactions will appear here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        return
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        transactions
            .asReversed()
            .forEach { transaction ->
                TransactionHistoryItem(
                    transaction = transaction
                )
            }
    }
}

/**
 * Single transaction history item.
 */
@Composable
private fun TransactionHistoryItem(
    transaction: TransactionSnapshot
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.invoiceId,
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = transaction.state.name,
                    style =
                        MaterialTheme.typography.labelLarge
                )
            }

            HorizontalDivider()

            TransactionDetailRow(
                label = "Request ID",
                value = transaction.requestId
            )

            TransactionDetailRow(
                label = "Payment Method",
                value =
                    transaction.paymentMethod.displayName
            )

            transaction.outcomeCode?.let { outcomeCode ->
                TransactionDetailRow(
                    label = "Outcome",
                    value = outcomeCode
                )
            }
        }
    }
}

/**
 * Generic label and value row.
 */
@Composable
private fun TransactionDetailRow(
    label: String,
    value: String
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Test case interface.
 */
@Composable
private fun TestCasesInterface() {
    var testSearchQuery by remember {
        mutableStateOf("")
    }

    val filteredTestCases =
        DemoTestCases.all.filter { testCase ->
            testSearchQuery.isBlank() ||
                    testCase.id.contains(
                        testSearchQuery,
                        ignoreCase = true
                    ) ||
                    testCase.title.contains(
                        testSearchQuery,
                        ignoreCase = true
                    ) ||
                    testCase.description.contains(
                        testSearchQuery,
                        ignoreCase = true
                    )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Test Cases",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text =
                "Verified payment-engine regression coverage.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = testSearchQuery,
            onValueChange = { newValue ->
                testSearchQuery = newValue
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Search test cases")
            },
            placeholder = {
                Text("timeout, invoice, cancel...")
            },
            singleLine = true
        )

        TestCaseSection(
            testCases = filteredTestCases
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}