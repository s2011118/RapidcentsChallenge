package com.rapidcents.challenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidcents.challenge.ui.theme.RapidcentsTheme
import com.rapidcents.challenge.ui.PaymentScreen
import com.rapidcents.challenge.ui.PaymentViewModel
import com.rapidcents.challenge.ui.PaymentViewModelFactory
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            RapidcentsTheme {
                val paymentViewModel: PaymentViewModel = viewModel(
                    factory = PaymentViewModelFactory(
                        engine = DemoAppContainer.engine,
                        demoGateway = DemoAppContainer.gateway,
                        demoJournal = DemoAppContainer.journal
                    )
                )

                val uiState by
                paymentViewModel.uiState.collectAsState()

                PaymentScreen(
                    uiState = uiState,
                    onInvoiceChanged =
                        paymentViewModel::onInvoiceIdChanged,
                    onAmountChanged =
                        paymentViewModel::onAmountChanged,
                    onInvoiceOverrideChanged =
                        paymentViewModel::onInvoiceOverrideChanged,
                    onDemoScenarioChanged =
                        paymentViewModel::onDemoScenarioChanged,
                    onContinueToPaymentOptions =
                        paymentViewModel::continueToPaymentOptions,
                    onPaymentMethodChanged =
                        paymentViewModel::onPaymentMethodChanged,
                    onBackToPaymentDetails =
                        paymentViewModel::backToPaymentDetails,
                    onStartSale =
                        paymentViewModel::startSale,
                    onCancel =
                        paymentViewModel::cancelCurrentTransaction,
                    onRecover =
                        paymentViewModel::recoverTransactions
                )
            }
        }

    }
}