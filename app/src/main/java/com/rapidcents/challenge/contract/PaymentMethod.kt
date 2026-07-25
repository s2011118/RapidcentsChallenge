package com.rapidcents.challenge.contract

/**
 * How the customer payment information was entered.
 */
enum class PaymentMethod(
    val displayName: String
) {
    TAP("Tap"),
    CHIP("Chip"),
    SWIPE("Swipe"),
    KEY_IN("Key-in")
}