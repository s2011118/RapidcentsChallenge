# Decisions

## Persist before gateway side effects

The engine persists `AUTHORIZING` before calling the gateway. After process
death, recovery uses inquiry with the original request ID instead of sending
another authorization.

## Idempotency

The same `requestId` returns the existing result. A different request ID with
the same invoice is rejected unless an explicit override is supplied.

## Cancellation

Cancellation before authorization cancels the active SDK session. Cancellation
after approval performs reversal. An uncertain reversal remains
`REVERSAL_PENDING`.

## Late callbacks

An atomic active-session reference invalidates the acceptance session before
SDK cancellation. Delayed callbacks cannot revive a cancelled transaction.

## Partial approval

Requested and approved amounts are stored separately. Customer rejection
reverses the approved portion or leaves the transaction in
`REVERSAL_PENDING`.