# RapidCents Kotlin Payment Engine Challenge

## Environment

| Component | Version |
|-----------|----------|
| JDK | 17 |
| Kotlin | 2.x |
| Gradle | 8.x |
| Android Studio | Ladybug / Koala |
| Compile SDK | 35 |
| Min SDK | 26 |

---

# Build

## Run unit tests

```bash
./gradlew testDebugUnitTest
```

## Build Android app

```bash
./gradlew assembleDebug
```

Or simply open the project in Android Studio and run the **app** module.

---

# Architecture

The solution separates the payment engine from Android UI components.

```
Compose UI
        │
        ▼
PaymentViewModel
        │
        ▼
ChallengePaymentEngine
        │
 ┌──────┼─────────┐
 │      │         │
 ▼      ▼         ▼
Acceptance  Gateway  Journal
Providers   Client   Storage
```

The payment engine is platform independent and owns all transaction state transitions.

---

# Transaction State Model

Typical flow:

```
VALIDATING
      │
      ▼
WAITING_FOR_ACCEPTANCE
      │
      ▼
CARD_PRESENTED
      │
      ▼
AUTHORIZING
      │
      ├────────────► APPROVED
      │
      ├────────────► DECLINED
      │
      ├────────────► RESOLVING_UNKNOWN
      │                   │
      │                   ▼
      │             RECOVERY_PENDING
      │
      ▼
PARTIALLY_APPROVED
      │
      ▼
REVERSAL_PENDING / CANCELLED
```

The engine guarantees only one terminal transaction is active at a time.

---

# Idempotency Policy

- `requestId` is the technical idempotency key.
- Repeating the same request never sends another authorization.
- Different request IDs with the same invoice are treated as duplicate business requests.
- Duplicate invoice attempts are rejected unless explicitly overridden.

---

# Timeout / Recovery Policy

Gateway timeout is treated as an **unknown outcome**.

The engine:

1. Persists AUTHORIZING before contacting the gateway.
2. Uses Inquiry instead of resending authorization.
3. Uses the original request ID.
4. Never performs blind retry.

If the process dies during authorization, recovery loads unfinished journal entries and continues with Inquiry.

---

# Cancellation Policy

Cancellation behaviour depends on transaction progress.

### Before card credential

- Acceptance SDK session is cancelled.
- Late callbacks are ignored.

### During authorization

- Authorization outcome is first resolved.
- If already approved, reversal is attempted.

### After approval

- Reversal is performed.
- Failed reversal enters `REVERSAL_PENDING`.

---

# Implemented Safety Scenarios

✅ Approved transaction

✅ Timeout followed by Inquiry

✅ Duplicate request id

✅ Duplicate invoice protection

✅ Terminal BUSY protection

✅ Recovery after process restart

✅ Cancellation

✅ Reversal

✅ Incident A

✅ Incident B

✅ Incident C

✅ Incident D

✅ Incident E

---

# Five Minute Demo

Recommended demonstration sequence:

1. Normal approved sale
2. Timeout resolved by Inquiry
3. Duplicate request ID returns existing result
4. Duplicate invoice rejected
5. Cancel before credential and demonstrate late callback is ignored
6. Partial approval followed by customer rejection and reversal

---

# Known Limitations

- Uses in-memory journal implementation for demonstration.
- No production payment processor.
- No EMV kernel implementation.
- No production cryptography.
- No receipt printer integration.
- Uses simulated gateway and acceptance providers.

---

# Future Improvements

- Persistent database journal
- Offline queue
- Receipt printing
- Encrypted journal storage
- Production gateway implementation
- Additional provider adapters
- Extended certification tooling

---

# Test Summary

Current automated coverage includes:

- Validation
- Idempotency
- Terminal concurrency
- Timeout recovery
- Inquiry recovery
- Cancellation
- Reversal
- Late callback protection
- Process restart recovery
- Partial approval handling

All mandatory safety scenarios required by the challenge are covered by automated unit tests.
