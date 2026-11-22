# Test Plan

## Required first-release tests

| Test | Expected result |
| --- | --- |
| Recipe and inventory matching | One canonical ingredient and unit rule produces one result |
| Locked meal slots | Regeneration preserves locked slots |
| Plan versus meal log | Future plans never enter actual meal statistics |
| Cooking and inventory | Confirm cooking deducts according to batch rules |
| Duplicate request | Double tap or retry does not duplicate inventory movements |
| Household sharing | Members see consistent shared data |
| Authorization | Non-members and removed members cannot read or write |
| Personal isolation | One user's meal log is not copied to another member |
| Historical snapshot | Editing a recipe does not rewrite saved meal-entry data |
| Concurrent edits | Conflicts are detected and not silently overwritten |
| Unit validation | Incompatible units are rejected or clearly reported |
| Unknown calories | Unknown calories are not treated as zero |

## Ownership

- AC owns recipe and inventory tests.
- BD owns planning, recommendation, cooking, and meal-record tests.
- EF owns shared authorization, concurrency, synchronization, and integration
  tests.
- EF wires all required tests into CI but does not replace feature-owner tests.

## Evidence

### C initial native fridge UI: taskc_version2

Adds InventoryPresentationTest (four cases), native fridge fixtures and historical
wide/narrow screenshots. The current revalidation and comparison with uploaded
version 1 are maintained in [taskc_version2](c-history/taskc_version2.md).
Android build/contract checks passed; local test reports total 30 cases with
no failures (4 new AC presentation tests executed), lint 0 errors / 9 warnings.
The unchanged server tests were not rerun for this UI-only restoration.
Historical UI evidence is distinguished from the current build; no physical
Xiaomi or new emulator click-through result is claimed.


### C inventory backend verification (2026-11-21)

`InventoryHttpIntegrationTest` exercises the existing generated routes through
real authentication and PostgreSQL: alias search, active ingredients, pagination,
separate batches, canonical units, expiry endpoints, metadata-only updates,
quantity movements, household isolation, revoked access/replay, idempotency,
stale concurrent writes, same-key concurrent requests, matching/allocation
read-only behavior, and lock timeout rollback/retry.

`InventoryRulesTest` and `InventoryCalculatorTest` cover decimal bounds,
strict dates, rounding, full/partial/missing/unknown matching, untracked lines,
expired/zero stock, and earliest-expiry/creation/ID allocation ordering.
`IngredientNamesTest` includes Unicode length boundaries.

The original C development run has 37 tests with no failures; the isolated unit task
has 18 tests and the candidate contract task has 4 (tasks overlap).
Commands, setup, limitations, and the proposed cooking integration are recorded
in [C_HANDOFF.md](C_HANDOFF.md). No Android inventory UI or tablet validation
is claimed by these backend tests.

C-V01 revalidation against main `a0943b1` (2026-11-22): server 43 tests,
isolated unit task 18, candidate contract task 4 and Android local tests 26
all passed. Android assemble, all-module lint (0 errors, 2 existing app warnings),
contract checks, 17 Python tests and the Windows launcher check passed.
Commands and environment corrections are recorded in
[c-history/C-V01-库存后端基础实现.md](c-history/C-V01-库存后端基础实现.md).
No inventory UI or physical-tablet validation was performed for this backend version.

Each pull request must list:

- commands actually executed;
- actual pass or fail results;
- tests that were not run;
- remaining risk for uncovered behavior.
