# Incident Notes

## Incident A

Problem

Fake gateway call counters were incorrectly initialised.

Solution

Reset counters to zero and added regression coverage.

Result

Gateway call counts are now deterministic.

---

## Incident B

Problem

Duplicate invoices using different request IDs could create another authorization.

Solution

Reject duplicate invoices unless explicitly overridden.

Result

No duplicate authorization is sent.

---

## Incident C

Problem

A delayed SDK callback could arrive after cancellation.

Solution

Acceptance sessions are invalidated before SDK cancellation.

Late callbacks are ignored.

Result

Exactly one final outcome is produced.

---

## Incident D

Problem

Application terminates during gateway authorization.

Solution

Persist AUTHORIZING before gateway call.

Recovery performs Inquiry using the original request ID.

Result

No second authorization occurs.

---

## Incident E

Problem

Gateway returns partial approval.

Customer rejects remaining balance.

Solution

Track approved amount separately.

Attempt reversal.

If reversal fails, enter REVERSAL_PENDING.

Result

Requested amount is never reported as fully approved.