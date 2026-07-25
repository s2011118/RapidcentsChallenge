package com.rapidcents.challenge.internal.gateway

import com.rapidcents.challenge.contract.GatewayClient
import com.rapidcents.challenge.contract.GatewayRequest
import com.rapidcents.challenge.contract.GatewayResult
import com.rapidcents.challenge.contract.InquiryResult
import com.rapidcents.challenge.contract.ReversalResult

/**
 * Configurable fake gateway for unit tests and demo scenarios.
 *
 * It also records call counts and the last request identifiers,
 * so recovery tests can prove that inquiry uses the original
 * transaction request ID without sending a new authorization.
 */
class FakeGatewayClient(
    var authorizeBehavior: AuthorizeBehavior =
        AuthorizeBehavior.APPROVED,
    var inquiryBehavior: InquiryBehavior =
        InquiryBehavior.APPROVED,
    var reversalBehavior: ReversalBehavior =
        ReversalBehavior.SUCCESS,
    var inquiryApprovedAmount: Long = 1_000L
) : GatewayClient {

    var authorizeCallCount: Int = 0
        private set

    var inquiryCallCount: Int = 0
        private set

    var reversalCallCount: Int = 0
        private set

    /**
     * Records the request ID used by the most recent
     * authorization call.
     */
    var lastAuthorizeRequestId: String? = null
        private set

    /**
     * Records the request ID used by the most recent inquiry.
     *
     * Incident D uses this to prove recovery queried the
     * processor with the original transaction identifier.
     */
    var lastInquiryRequestId: String? = null
        private set

    /**
     * Records the request ID used by the most recent reversal.
     */
    var lastReversalRequestId: String? = null
        private set

    override suspend fun authorize(
        request: GatewayRequest
    ): GatewayResult {
        authorizeCallCount++
        lastAuthorizeRequestId = request.requestId

        return when (authorizeBehavior) {
            AuthorizeBehavior.APPROVED -> {
                GatewayResult.Approved(
                    hostReference =
                        "HOST-${request.requestId}",
                    approvalCode = "APPROVED",
                    approvedAmount =
                        request.amount.minor
                )
            }

            AuthorizeBehavior.PARTIAL_APPROVAL -> {
                GatewayResult.PartialApproval(
                    hostReference =
                        "HOST-${request.requestId}",
                    approvalCode =
                        "PARTIALLY_APPROVED",
                    approvedAmount = 700L
                )
            }

            AuthorizeBehavior.DECLINED -> {
                GatewayResult.Declined(
                    code = "DECLINED",
                    message =
                        "Transaction declined by simulated host."
                )
            }

            AuthorizeBehavior.TIMEOUT -> {
                GatewayResult.Timeout
            }

            AuthorizeBehavior.UNKNOWN -> {
                GatewayResult.Unknown
            }
        }
    }

    override suspend fun inquire(
        requestId: String
    ): InquiryResult {
        inquiryCallCount++
        lastInquiryRequestId = requestId

        return when (inquiryBehavior) {
            InquiryBehavior.APPROVED -> {
                InquiryResult.Approved(
                    hostReference = "HOST-$requestId",
                    approvalCode = "APPROVED",
                    approvedAmount =
                        inquiryApprovedAmount
                )
            }

            InquiryBehavior.DECLINED -> {
                InquiryResult.Declined(
                    code = "DECLINED",
                    message =
                        "Transaction declined."
                )
            }

            InquiryBehavior.NOT_FOUND -> {
                InquiryResult.NotFound
            }

            InquiryBehavior.UNKNOWN -> {
                InquiryResult.Unknown
            }
        }
    }

    override suspend fun reverse(
        requestId: String
    ): ReversalResult {
        reversalCallCount++
        lastReversalRequestId = requestId

        return when (reversalBehavior) {
            ReversalBehavior.SUCCESS -> {
                ReversalResult.Success
            }

            ReversalBehavior.PENDING -> {
                ReversalResult.Pending
            }

            ReversalBehavior.FAILED -> {
                ReversalResult.Failed(
                    reason =
                        "Simulated reversal failure."
                )
            }
        }
    }
}

enum class AuthorizeBehavior {
    APPROVED,
    PARTIAL_APPROVAL,
    DECLINED,
    TIMEOUT,
    UNKNOWN
}

enum class InquiryBehavior {
    APPROVED,
    DECLINED,
    NOT_FOUND,
    UNKNOWN
}

enum class ReversalBehavior {
    SUCCESS,
    PENDING,
    FAILED
}