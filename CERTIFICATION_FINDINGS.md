# Certification Findings

## Observation 1

Gateway timeout does not prove authorization failed.

Action

Perform Inquiry using the original request ID.

---

## Observation 2

Duplicate authorizations create financial risk.

Action

Stable idempotency prevents duplicate gateway requests.

---

## Observation 3

Late SDK callbacks may occur after cancellation.

Action

Session ownership prevents stale callbacks from reviving cancelled transactions.

---

## Observation 4

Partial approval cannot be treated as full approval.

Action

Approved amount is stored independently.

Customer rejection performs reversal.

---

## Observation 5

Recovery must never resend authorization.

Action

Recovery resumes using persisted transaction state and Inquiry.

---

## Safe Diagnostics

Logs include:

- Correlation ID
- Request ID
- Transaction state
- Event type
- Provider

Logs exclude:

- PAN
- Track data
- PIN
- EMV data
- Cryptograms
- Opaque payment credential