# Certification Matrix

| Test | Purpose | Expected Result | Status |
|------|----------|----------------|--------|
| Validation | Reject invalid request | Pass | ✅ |
| Approved Sale | Normal authorization | APPROVED | ✅ |
| Duplicate Request | Idempotency | One authorization | ✅ |
| Duplicate Invoice | Business duplicate | Reject | ✅ |
| Terminal Busy | One active transaction | BUSY | ✅ |
| Timeout Recovery | Inquiry | APPROVED | ✅ |
| Unknown Outcome | Recovery pending | Safe state | ✅ |
| Cancellation | Cancel before credential | CANCELLED | ✅ |
| Reversal | Cancel after approval | REVERSED | ✅ |
| Incident A | Fake gateway counters | Fixed | ✅ |
| Incident B | Duplicate invoice | One authorization | ✅ |
| Incident C | Late callback | Ignored | ✅ |
| Incident D | Process restart | Inquiry only | ✅ |
| Incident E | Partial approval | Reversal | ✅ |