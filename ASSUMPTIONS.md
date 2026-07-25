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