package com.rapidcents.challenge.ui

enum class DemoMessageDirection {
    TERMINAL_TO_HOST,
    HOST_TO_TERMINAL,
    SYSTEM
}

enum class DemoMessageStatus {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class DemoHostMessage(
    val id: String,
    val timestamp: String,
    val direction: DemoMessageDirection,
    val title: String,
    val details: List<String>,
    val status: DemoMessageStatus
)