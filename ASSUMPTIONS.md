# Assumptions

## Business

- One active transaction is allowed per terminal.
- `requestId` is the technical idempotency key.
- `invoiceId` is used for business duplicate detection.
- The gateway is considered the source of truth for authorization status.

## Money

- Money is represented using integer minor units.
- Floating-point values are never used for authorization amounts.

## Gateway

- Gateway timeout does not imply decline.
- Unknown or malformed responses are treated as uncertain outcomes.
- Inquiry uses the original request ID.

## Acceptance Providers

- Hardware and Tap to Pay SDKs are trusted to deliver opaque payment credentials.
- Raw PAN, EMV tags, PIN data and cryptograms are never exposed to the payment engine.

## Recovery

- Transactions persisted as AUTHORIZING, RESOLVING_UNKNOWN or REVERSAL_PENDING are recoverable.
- Recovery resumes using the original identifiers.
- Recovery never sends another authorization.

## Timeout Recovery Interpretation

In this implementation, a gateway timeout is treated as an uncertain outcome rather than an immediate decline.
If a subsequent Inquiry confirms that the original authorization was approved, the payment engine treats the transaction as successfully authorized, because the gateway is considered the source of truth.
Therefore, the transaction is persisted as APPROVED and no reversal is performed in this recovery path.
This behaviour represents the design decision adopted for this challenge. Production payment systems may instead require an automatic reversal after a timeout, depending on gateway and acquirer rules.