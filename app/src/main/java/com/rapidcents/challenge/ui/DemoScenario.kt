package com.rapidcents.challenge.ui

/**
 * Payment outcomes available in the demo application.
 *
 * These scenarios only configure fake dependencies.
 * The payment engine itself does not contain demo-specific logic.
 */
enum class DemoScenario(
    val displayName: String
) {
    APPROVED("Approved"),

    DECLINED("Declined"),

    TIMEOUT_INQUIRY_APPROVED(
        "Timeout → Inquiry Approved"
    ),

    TIMEOUT_RECOVERY_PENDING(
        "Timeout → Recovery Pending"
    ),

    REVERSAL_PENDING(
        "Reversal Pending"
    )
}