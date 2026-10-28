# Product Rules

These rules are mandatory product constraints. They are not optional
implementation preferences.

## Core invariants

1. A meal plan is not a cooking record. Planning a meal must not create a real
   cooking event or personal meal entry.
2. A household cooking event does not mean every member ate the meal.
3. Personal meal entries must not deduct inventory again. Inventory is deducted
   only during the confirmed cooking flow.
4. Inventory must never become negative.
5. Duplicate submissions, retries, and double taps must not create duplicate
   cooking records or inventory movements.
6. Household-shared data and personal data must be separated.
7. Authorization must be enforced by a shared service or trusted data layer,
   not only by hiding UI.
8. Unknown values must remain unknown. Unknown calories, dates, or quantities
   must not silently become zero.
9. Historical records must preserve the recipe data that applied at the time.
10. Shared writes must detect concurrent changes and must not silently overwrite
    another member's update.

## Planning

- A plan may be empty. It does not need all meals for 14 days.
- Regenerating a plan must preserve locked meal slots.
- Previewing generation must not overwrite existing plan entries.
- Saving a plan does not deduct inventory or create meal records.

## Inventory

- Expired batches are not available inventory.
- Unit conversion is allowed only within the same measurement dimension.
- Every deduction must validate batch, quantity, and unit before writing.
- Inventory changes must be represented as traceable movements.

## Records and calories

- A cooking event and personal meal entries are separate records.
- One cooked dish may be shared by multiple members and may be eaten later.
- Unknown calorie data is displayed as unknown and excluded from numeric totals.
- Historical meal entries use a recipe snapshot or stable version reference.

## Permissions

- Non-members cannot read or modify household data.
- Removed members cannot continue accessing household data.
- Personal calorie and meal data must not be exposed to another account.
