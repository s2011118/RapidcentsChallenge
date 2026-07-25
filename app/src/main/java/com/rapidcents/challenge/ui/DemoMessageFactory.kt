package com.rapidcents.challenge.ui

object DemoMessageFactory {

    fun messagesFor(
        scenario: DemoScenario
    ): List<DemoHostMessage> {
        return when (scenario) {
            DemoScenario.APPROVED ->
                approvedMessages()

            DemoScenario.DECLINED ->
                declinedMessages()

            DemoScenario.TIMEOUT_INQUIRY_APPROVED ->
                timeoutInquiryApprovedMessages()

            DemoScenario.TIMEOUT_RECOVERY_PENDING ->
                recoveryPendingMessages()

            DemoScenario.REVERSAL_PENDING ->
                reversalPendingMessages()
        }
    }

    private fun approvedMessages(): List<DemoHostMessage> =
        listOf(
            DemoHostMessage(
                id = "approved-1",
                timestamp = "10:20:01",
                direction = DemoMessageDirection.SYSTEM,
                title = "Card Accepted",
                details = listOf(
                    "Provider: Hardware Card",
                    "Credential: CARD-****-1234"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "approved-2",
                timestamp = "10:20:02",
                direction = DemoMessageDirection.TERMINAL_TO_HOST,
                title = "Authorization Request Sent",
                details = listOf(
                    "Request ID: demo-request-001",
                    "Invoice ID: demo-invoice-001",
                    "Amount: CAD 10.00",
                    "Message Type: AUTHORIZATION"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "approved-3",
                timestamp = "10:20:03",
                direction = DemoMessageDirection.HOST_TO_TERMINAL,
                title = "Authorization Approved",
                details = listOf(
                    "Response Code: 00",
                    "State: APPROVED",
                    "Approved Amount: CAD 10.00"
                ),
                status = DemoMessageStatus.SUCCESS
            )
        )

    private fun declinedMessages(): List<DemoHostMessage> =
        listOf(
            DemoHostMessage(
                id = "declined-1",
                timestamp = "10:21:01",
                direction = DemoMessageDirection.TERMINAL_TO_HOST,
                title = "Authorization Request Sent",
                details = listOf(
                    "Request ID: demo-request-002",
                    "Invoice ID: demo-invoice-002",
                    "Amount: CAD 25.00"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "declined-2",
                timestamp = "10:21:02",
                direction = DemoMessageDirection.HOST_TO_TERMINAL,
                title = "Authorization Declined",
                details = listOf(
                    "Response Code: 05",
                    "State: DECLINED",
                    "Message: Do not honour"
                ),
                status = DemoMessageStatus.ERROR
            )
        )

    private fun timeoutInquiryApprovedMessages(): List<DemoHostMessage> =
        listOf(
            DemoHostMessage(
                id = "timeout-1",
                timestamp = "10:22:01",
                direction = DemoMessageDirection.TERMINAL_TO_HOST,
                title = "Authorization Request Sent",
                details = listOf(
                    "Request ID: incident-a-request-001",
                    "Invoice ID: incident-a-invoice-001",
                    "Amount: CAD 25.00"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "timeout-2",
                timestamp = "10:22:06",
                direction = DemoMessageDirection.SYSTEM,
                title = "Authorization Timeout",
                details = listOf(
                    "Final host result is unknown.",
                    "Authorization will not be sent again."
                ),
                status = DemoMessageStatus.WARNING
            ),
            DemoHostMessage(
                id = "timeout-3",
                timestamp = "10:22:07",
                direction = DemoMessageDirection.TERMINAL_TO_HOST,
                title = "Inquiry Request Sent",
                details = listOf(
                    "Original Request ID: incident-a-request-001",
                    "Purpose: Resolve ambiguous authorization"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "timeout-4",
                timestamp = "10:22:08",
                direction = DemoMessageDirection.HOST_TO_TERMINAL,
                title = "Original Transaction Approved",
                details = listOf(
                    "Inquiry Result: APPROVED",
                    "Authorization Count: 1",
                    "Inquiry Count: 1"
                ),
                status = DemoMessageStatus.SUCCESS
            )
        )

    private fun recoveryPendingMessages(): List<DemoHostMessage> =
        listOf(
            DemoHostMessage(
                id = "recovery-1",
                timestamp = "10:23:01",
                direction = DemoMessageDirection.TERMINAL_TO_HOST,
                title = "Authorization Request Sent",
                details = listOf(
                    "Request ID: recovery-request-001"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "recovery-2",
                timestamp = "10:23:06",
                direction = DemoMessageDirection.SYSTEM,
                title = "Authorization Timeout",
                details = listOf(
                    "Outcome remains uncertain."
                ),
                status = DemoMessageStatus.WARNING
            ),
            DemoHostMessage(
                id = "recovery-3",
                timestamp = "10:23:07",
                direction = DemoMessageDirection.HOST_TO_TERMINAL,
                title = "Inquiry Result Unknown",
                details = listOf(
                    "Transaction moved to RECOVERY_PENDING.",
                    "Recovery will continue later."
                ),
                status = DemoMessageStatus.WARNING
            )
        )

    private fun reversalPendingMessages(): List<DemoHostMessage> =
        listOf(
            DemoHostMessage(
                id = "reversal-1",
                timestamp = "10:24:01",
                direction = DemoMessageDirection.SYSTEM,
                title = "Approved Payment Cancellation Requested",
                details = listOf(
                    "Direct local cancellation is not allowed."
                ),
                status = DemoMessageStatus.WARNING
            ),
            DemoHostMessage(
                id = "reversal-2",
                timestamp = "10:24:02",
                direction = DemoMessageDirection.TERMINAL_TO_HOST,
                title = "Reversal Request Sent",
                details = listOf(
                    "Original Request ID: reversal-request-001"
                ),
                status = DemoMessageStatus.INFO
            ),
            DemoHostMessage(
                id = "reversal-3",
                timestamp = "10:24:05",
                direction = DemoMessageDirection.HOST_TO_TERMINAL,
                title = "Reversal Confirmation Pending",
                details = listOf(
                    "State: REVERSAL_PENDING",
                    "Recovery is required."
                ),
                status = DemoMessageStatus.WARNING
            )
        )
}