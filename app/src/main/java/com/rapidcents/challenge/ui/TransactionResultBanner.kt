package com.rapidcents.challenge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Displays the expected final result for the selected
 * demo gateway scenario.
 *
 * Using scenario.name keeps the UI compatible even when
 * the DemoScenario enum contains additional values.
 */
@Composable
fun TransactionResultBanner(
    scenario: DemoScenario,
    modifier: Modifier = Modifier
) {
    val bannerContent =
        resultContentFor(
            scenario = scenario
        )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = bannerContent.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = bannerContent.titleColor()
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = bannerContent.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Selected scenario: ${scenario.displayName}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private data class TransactionBannerContent(
    val type: TransactionBannerType,
    val title: String,
    val description: String
)

private enum class TransactionBannerType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * Uses enum names instead of an exhaustive enum when-expression.
 *
 * This avoids compilation errors if the project contains
 * slightly different DemoScenario values.
 */
private fun resultContentFor(
    scenario: DemoScenario
): TransactionBannerContent {
    val scenarioName =
        scenario.name.uppercase()

    return when {
        scenarioName.contains("DECLIN") -> {
            TransactionBannerContent(
                type = TransactionBannerType.ERROR,
                title = "✕ TRANSACTION DECLINED",
                description =
                    "The processor declined the authorization request."
            )
        }

        scenarioName.contains("TIMEOUT") &&
                scenarioName.contains("APPROV") -> {

            TransactionBannerContent(
                type = TransactionBannerType.SUCCESS,
                title = "✓ APPROVED AFTER INQUIRY",
                description =
                    "Authorization timed out, but inquiry confirmed that the original transaction was approved."
            )
        }

        scenarioName.contains("RECOVERY") ||
                scenarioName.contains("UNKNOWN") -> {

            TransactionBannerContent(
                type = TransactionBannerType.WARNING,
                title = "⚠ RECOVERY PENDING",
                description =
                    "The processor outcome remains uncertain and requires recovery."
            )
        }

        scenarioName.contains("REVERSAL") -> {
            TransactionBannerContent(
                type = TransactionBannerType.WARNING,
                title = "⚠ REVERSAL PENDING",
                description =
                    "The reversal request is waiting for processor confirmation."
            )
        }

        scenarioName.contains("APPROV") ||
                scenarioName.contains("SUCCESS") -> {

            TransactionBannerContent(
                type = TransactionBannerType.SUCCESS,
                title = "✓ TRANSACTION APPROVED",
                description =
                    "The payment was authorized successfully."
            )
        }

        else -> {
            TransactionBannerContent(
                type = TransactionBannerType.INFO,
                title = "PAYMENT SCENARIO READY",
                description =
                    "Start the sale to demonstrate the selected gateway scenario."
            )
        }
    }
}

/**
 * This is a composable extension because MaterialTheme
 * can only be accessed from a composable context.
 */
@Composable
private fun TransactionBannerContent.titleColor() =
    when (type) {
        TransactionBannerType.SUCCESS ->
            MaterialTheme.colorScheme.primary

        TransactionBannerType.ERROR ->
            MaterialTheme.colorScheme.error

        TransactionBannerType.WARNING ->
            MaterialTheme.colorScheme.tertiary

        TransactionBannerType.INFO ->
            MaterialTheme.colorScheme.onSurface
    }