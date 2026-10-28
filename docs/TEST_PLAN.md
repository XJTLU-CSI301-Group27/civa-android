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

Each pull request must list:

- commands actually executed;
- actual pass or fail results;
- tests that were not run;
- remaining risk for uncovered behavior.
