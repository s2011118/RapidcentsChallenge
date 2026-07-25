package com.rapidcents.challenge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Displays the payment-engine test cases.
 *
 * The actual tests run through Gradle/JUnit.
 * This dashboard presents the latest verified test results.
 */
@Composable
fun TestCaseSection(
    testCases: List<DemoTestCase>,
    modifier: Modifier = Modifier
) {
    val passedCount =
        testCases.count { testCase ->
            testCase.status == DemoTestStatus.PASSED
        }

    val failedCount =
        testCases.count { testCase ->
            testCase.status == DemoTestStatus.FAILED
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Test Case Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Results reflect the latest successful Gradle/JUnit test run.",
            style = MaterialTheme.typography.bodySmall
        )

        TestSummaryCard(
            passedCount = passedCount,
            failedCount = failedCount,
            totalCount = testCases.size
        )

        if (testCases.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No test cases match the search.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            testCases.forEach { testCase ->
                TestCaseItem(
                    testCase = testCase
                )
            }
        }
    }
}

@Composable
private fun TestSummaryCard(
    passedCount: Int,
    failedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$passedCount / $totalCount Passed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    if (failedCount == 0) {
                        "All displayed test cases passed."
                    } else {
                        "$failedCount test case(s) failed."
                    },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TestCaseItem(
    testCase: DemoTestCase
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = testCase.id,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        text = testCase.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                TestStatusBadge(
                    status = testCase.status
                )
            }

            Text(
                text = testCase.description,
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text(
                text = "Expected Result",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = testCase.expectedResult,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TestStatusBadge(
    status: DemoTestStatus
) {
    val statusText =
        when (status) {
            DemoTestStatus.PASSED -> "✓ PASSED"
            DemoTestStatus.FAILED -> "✕ FAILED"
            DemoTestStatus.NOT_RUN -> "NOT RUN"
        }

    val containerColor =
        when (status) {
            DemoTestStatus.PASSED ->
                MaterialTheme.colorScheme.primaryContainer

            DemoTestStatus.FAILED ->
                MaterialTheme.colorScheme.errorContainer

            DemoTestStatus.NOT_RUN ->
                MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        when (status) {
            DemoTestStatus.PASSED ->
                MaterialTheme.colorScheme.onPrimaryContainer

            DemoTestStatus.FAILED ->
                MaterialTheme.colorScheme.onErrorContainer

            DemoTestStatus.NOT_RUN ->
                MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = statusText,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}