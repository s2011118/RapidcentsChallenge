# AI Usage

## Tool

ChatGPT

---

## Tasks

- Kotlin implementation review
- Unit test generation
- Incident analysis
- Documentation drafting
- Kotlin debugging
- README generation

---

## Accepted Suggestions

- Recovery unit-test structure
- Late callback test structure
- Documentation templates
- Kotlin formatting improvements

---

## Rejected / Corrected Suggestions

Example:

- An early suggestion referenced an SDK API that does not exist in the supplied contracts.
- The implementation was corrected to use the project's actual acceptance provider.
- A generated test originally expected a fixed host reference. The fake gateway actually derives the host reference from the request ID, so the assertion was updated.

---

## Independent Verification

Every accepted AI suggestion was verified by:

- Running the complete unit test suite
- Reviewing compiler output
- Confirming gateway call counts
- Reviewing persisted transaction snapshots

---

## Risk Identified

AI-generated code may assume interfaces that differ from the supplied contracts.

All accepted changes were manually reviewed and tested before submission.