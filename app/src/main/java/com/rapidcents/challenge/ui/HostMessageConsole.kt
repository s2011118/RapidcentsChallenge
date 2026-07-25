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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HostMessageConsole(
    messages: List<DemoHostMessage>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Host Communication",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Sanitized terminal and processor message simulation",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        messages.forEach { message ->
            HostMessageCard(message)

            Spacer(
                modifier = Modifier.height(10.dp)
            )
        }
    }
}

@Composable
private fun HostMessageCard(
    message: DemoHostMessage
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    text = directionText(message.direction),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = message.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            message.details.forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = statusText(message.status),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun directionText(
    direction: DemoMessageDirection
): String =
    when (direction) {
        DemoMessageDirection.TERMINAL_TO_HOST ->
            "TERMINAL → HOST"

        DemoMessageDirection.HOST_TO_TERMINAL ->
            "HOST → TERMINAL"

        DemoMessageDirection.SYSTEM ->
            "PAYMENT ENGINE"
    }

private fun statusText(
    status: DemoMessageStatus
): String =
    when (status) {
        DemoMessageStatus.INFO -> "INFO"
        DemoMessageStatus.SUCCESS -> "SUCCESS"
        DemoMessageStatus.WARNING -> "WARNING"
        DemoMessageStatus.ERROR -> "FAILED"
    }