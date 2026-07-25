package com.rapidcents.challenge.contract

data class GatewayRequest(
    val requestId: String,
    val amount: Money,
    val credential: OpaquePaymentCredential
)

sealed interface GatewayResult {

    data class Approved(
        val hostReference: String,
        val approvalCode: String,
        val approvedAmount: Long
    ) : GatewayResult

    data class Declined(
        val code: String,
        val message: String
    ) : GatewayResult

    data class PartialApproval(
        val hostReference: String,
        val approvalCode: String,
        val approvedAmount: Long
    ) : GatewayResult

    data object Timeout : GatewayResult

    data object Unknown : GatewayResult

    data class Malformed(
        val reason: String
    ) : GatewayResult
}

sealed interface InquiryResult {

    data class Approved(
        val hostReference: String,
        val approvalCode: String,
        val approvedAmount: Long
    ) : InquiryResult

    data class Declined(
        val code: String,
        val message: String
    ) : InquiryResult

    data object NotFound : InquiryResult

    data object Unknown : InquiryResult
}

sealed interface ReversalResult {

    data object Success : ReversalResult

    data object Pending : ReversalResult

    data class Failed(
        val reason: String
    ) : ReversalResult
}

interface GatewayClient {

    suspend fun authorize(
        request: GatewayRequest
    ): GatewayResult

    suspend fun inquire(
        requestId: String
    ): InquiryResult

    suspend fun reverse(
        requestId: String
    ): ReversalResult
}