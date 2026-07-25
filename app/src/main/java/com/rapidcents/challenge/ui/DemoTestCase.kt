package com.rapidcents.challenge.ui

/**
 * Status displayed by the demo test-case dashboard.
 *
 * These values present the result of the latest verified
 * Gradle/JUnit test run. The tests are not executed inside
 * the Android UI.
 */
enum class DemoTestStatus {
    PASSED,
    FAILED,
    NOT_RUN
}

/**
 * UI model used to describe one verified payment test case.
 */
data class DemoTestCase(
    val id: String,
    val title: String,
    val description: String,
    val expectedResult: String,
    val status: DemoTestStatus
)

/**
 * Test cases currently covered by the payment-engine
 * unit-test suite.
 */
object DemoTestCases {

    val all: List<DemoTestCase> = listOf(
        DemoTestCase(
            id = "TC-01",
            title = "Happy Path Approval",
            description =
                "A hardware-card payment is accepted and authorized normally.",
            expectedResult =
                "The transaction reaches the APPROVED state.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-02",
            title = "Journal Persistence",
            description =
                "The completed transaction is written to the transaction journal.",
            expectedResult =
                "The approved transaction can be retrieved using its request ID.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-03",
            title = "Safe Audit Events",
            description =
                "The payment lifecycle writes sanitized audit events.",
            expectedResult =
                "Audit events are recorded without sensitive card information.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-04",
            title = "Request ID Idempotency",
            description =
                "The same request ID is submitted more than once.",
            expectedResult =
                "The existing result is returned and authorization is called only once.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-05",
            title = "Timeout Resolved by Inquiry",
            description =
                "Authorization times out, but inquiry confirms that the host approved it.",
            expectedResult =
                "The transaction becomes APPROVED without sending authorization again.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-06",
            title = "Unknown Inquiry Recovery",
            description =
                "Neither authorization nor inquiry can confirm the final host result.",
            expectedResult =
                "The transaction moves to RECOVERY_PENDING.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-07",
            title = "Cancel Approved Payment",
            description =
                "Cancellation is requested after a payment has already been approved.",
            expectedResult =
                "A reversal is sent and the transaction becomes CANCELLED.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-08",
            title = "Recover Pending Reversal",
            description =
                "A reversal that previously remained pending is processed during recovery.",
            expectedResult =
                "The reversal is confirmed and the transaction becomes CANCELLED.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-09",
            title = "Explicit Invoice Override",
            description =
                "The same invoice ID is intentionally reused with explicit override enabled.",
            expectedResult =
                "A new authorization is allowed.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-10",
            title = "Busy Terminal Protection",
            description =
                "A second sale is started while another transaction is active.",
            expectedResult =
                "The second request is rejected as Busy.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-11",
            title = "Cancel Before Authorization",
            description =
                "The transaction is cancelled while waiting for payment acceptance.",
            expectedResult =
                "No authorization or reversal request is sent.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "INC-A",
            title = "Incident A — Ambiguous Timeout",
            description =
                "The host approved the payment, but the terminal received an authorization timeout.",
            expectedResult =
                "Inquiry confirms APPROVED and authorization count remains one.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "INC-B",
            title = "Incident B — Duplicate Invoice",
            description =
                "A new request ID attempts to reuse an existing invoice ID.",
            expectedResult =
                "The duplicate invoice is rejected and no second authorization is sent.",
            status = DemoTestStatus.PASSED
        ),
        DemoTestCase(
            id = "TC-14",
            title = "Late Callback After Cancellation",
            description =
                "A Tap to Pay sale is cancelled before a credential is returned. " +
                        "A delayed credential callback is then delivered.",
            expectedResult =
                "The transaction remains CANCELLED and no gateway authorization is sent.",
            status = DemoTestStatus.PASSED
        ),DemoTestCase(
            id = "TC-15",
            title = "Process Death Recovery",
            description =
                "An AUTHORIZING transaction is persisted before the app process stops. " +
                        "A fresh engine recovers the transaction after restart.",
            expectedResult =
                "Recovery uses inquiry with the original request ID and sends no new authorization.",
            status = DemoTestStatus.PASSED
        ),DemoTestCase(
            id = "TC-16",
            title = "Partial Approval Rejection",
            description =
                "A sale for 1005 minor units receives a partial approval for 700. " +
                        "The customer rejects paying the remaining balance.",
            expectedResult =
                "The approved 700 is reversed, or the transaction remains REVERSAL_PENDING if uncertain.",
            status = DemoTestStatus.PASSED
        )

    )
}