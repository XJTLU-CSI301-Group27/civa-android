# Data Contract

Status: Draft

2026-11-20 BD MVP implementation note: the existing OpenAPI remains unchanged.
New Android entry symbols, navigation reference syntax, a minimal shared recipe
read adapter, and the additive starter dictionary/import are proposed in
`contracts/changes/IC-20261120-BD-01.md`. This does not approve the baseline or
record EF acceptance. Implementation, scope reductions, source attribution,
actual validation and integration steps are maintained in `docs/BD_HANDOFF.md`.

Implementation note (2026-11-21, C / Issue #9): the existing candidate
`InventoryApi` operations now have backend implementations in
`server/.../inventory`. No OpenAPI field, enum, method signature or database
schema was changed. See [C_HANDOFF.md](C_HANDOFF.md) for tested behavior.
This implementation does not approve the draft baseline. A future cross-group
allocation entry point is proposed separately in
[IC-9](../contracts/changes/IC-9.md); its implementation is currently internal
and is not an approved public service for BD/EF.

This document is the source of truth for cross-group data. The team must agree on
the final storage representation before implementation. Public contracts must
not be changed silently.

Every contract must define:

- business meaning;
- field names, types, and nullability;
- identifier and relationships;
- ownership: personal or household;
- quantity, unit, date, and time rules;
- empty and error semantics;
- provider and allowed callers;
- related tests.

## Contract status

| Status | Meaning |
| --- | --- |
| Draft | Under discussion; callers must not depend on it |
| Agreed | Approved for implementation |
| Implemented | Provider implementation exists |
| Tested | Contract and integration tests pass |

## Required concepts

The field lists below are provisional and must be finalized by the team.

### User and Preference

Ownership: personal

Suggested fields:

- `userId`
- `displayName`
- `tastePreferences`
- `dietaryRestrictions`

### Household and Membership

Ownership: household

Suggested fields:

- `householdId`
- `name`
- `memberUserId`
- `role`

### Ingredient

Ownership: shared canonical data

Suggested fields:

- `ingredientId`
- `canonicalName`
- `aliases`
- `measurementDimension`
- `defaultUnit`

Rules:

- Integrations use `ingredientId`, not display names.
- Unit conversion is allowed only within one measurement dimension.

### Recipe and RecipeIngredient

Ownership: household-shared recipe with personal wishlist state

Suggested fields:

- `recipeId`
- `name`
- `imageReference`
- `tasteTags`
- `estimatedMinutes`
- `servings`
- `caloriesPerServing`
- `status`: active or archived
- `ingredients`: list of ingredient ID and quantity
- `steps`

Archiving a recipe must not remove historical references.

### Wishlist

Ownership: personal

Suggested fields:

- `userId`
- `recipeId`
- `createdAt`

### InventoryBatch

Ownership: household

Suggested fields:

- `batchId`
- `ingredientId`
- `quantity`
- `unit`
- `storageLocation`
- `expiresAt`
- `receivedAt`

### InventoryMovement

Ownership: household

Suggested fields:

- `movementId`
- `batchId`
- `ingredientId`
- `quantityDelta`
- `reason`
- `actorUserId`
- `requestId`
- `createdAt`

`requestId` supports idempotency.

### MealPlan and PlanItem

Ownership: household

Suggested fields:

- `planId`
- `date`
- `mealSlot`
- `recipeId`
- `servings`
- `locked`
- `createdBy`
- `updatedAt`

### CookingEvent

Ownership: household

Suggested fields:

- `cookingEventId`
- `recipeId`
- `recipeSnapshot`
- `cookedAt`
- `servingsProduced`
- `cookedBy`
- `requestId`

### MealEntry

Ownership: personal

Suggested fields:

- `mealEntryId`
- `userId`
- `mealSlot`
- `actualServings`
- `calories`
- `calorieStatus`: known or unknown
- `recipeSnapshot`
- `photoReference`
- `notes`
- `consumedAt`

## Contract change process

1. Describe the old and new contract.
2. Identify every caller.
3. Update this document.
4. Update provider and callers in a coordinated change.
5. Add or update tests.
6. Run integration checks.
7. Record remaining incompatibilities before requesting review.
