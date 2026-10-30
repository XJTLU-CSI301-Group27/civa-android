# OpenAPI 接口索引（候选草稿）

由 `contracts/openapi.json` 生成；不代表契约已批准。

| operationId | 方法 | 路径 | 负责人 | 请求体 | 成功响应 |
| --- | --- | --- | --- | --- | --- |
| addWishlist | PUT | /api/v1/me/wishlist/{recipeId} | A | — | 204 — |
| changeInventory | POST | /api/v1/households/{householdId}/inventory-batches/{batchId}/movements | C | application/json: InventoryChangeRequest | 201 application/json: InventoryMovement |
| commitPlan | POST | /api/v1/households/{householdId}/plan-commits | B | application/json: PlanCommitRequest | 200 application/json: PlanSlot[] |
| confirmCooking | POST | /api/v1/households/{householdId}/cooking-events | D+E | application/json: ConfirmCookingRequest | 201 application/json: CookingEvent |
| createHousehold | POST | /api/v1/households | E | application/json: CreateHouseholdRequest | 201 application/json: Household |
| createInventoryBatch | POST | /api/v1/households/{householdId}/inventory-batches | C | application/json: CreateInventoryBatchRequest | 201 application/json: InventoryBatch |
| createInvitation | POST | /api/v1/households/{householdId}/invitations | E | — | 201 application/json: Invitation |
| createMealEntry | POST | /api/v1/me/meal-entries | D | application/json: CreateMealEntryRequest | 201 application/json: MealEntry |
| createRecipe | POST | /api/v1/households/{householdId}/recipes | A | application/json: CreateRecipeRequest | 201 application/json: Recipe |
| deleteMealEntry | DELETE | /api/v1/me/meal-entries/{entryId} | D | — | 204 — |
| getHousehold | GET | /api/v1/households/{householdId} | E | — | 200 application/json: Household |
| getMe | GET | /api/v1/me | E | — | 200 application/json: User |
| getMealSummary | GET | /api/v1/me/meal-summary | D | — | 200 application/json: PeriodSummary |
| getMediaContent | GET | /api/v1/media/{mediaId}/content | E | — | 200 image/jpeg: binary, image/png: binary |
| getPreferences | GET | /api/v1/me/preferences | E | — | 200 application/json: Preference |
| getRecipe | GET | /api/v1/households/{householdId}/recipes/{recipeId} | A | — | 200 application/json: Recipe |
| joinHousehold | POST | /api/v1/households/join | E | application/json: JoinHouseholdRequest | 201 application/json: Membership |
| listCookingEvents | GET | /api/v1/households/{householdId}/cooking-events | D | — | 200 application/json: CookingEventPage |
| listIngredients | GET | /api/v1/ingredients | C | — | 200 application/json: IngredientPage |
| listInventoryBatches | GET | /api/v1/households/{householdId}/inventory-batches | C | — | 200 application/json: InventoryBatchPage |
| listInventoryMovements | GET | /api/v1/households/{householdId}/inventory-movements | C | — | 200 application/json: InventoryMovementPage |
| listMealEntries | GET | /api/v1/me/meal-entries | D | — | 200 application/json: MealEntryPage |
| listMembers | GET | /api/v1/households/{householdId}/members | E | — | 200 application/json: MembershipPage |
| listPlanSlots | GET | /api/v1/households/{householdId}/plan-slots | B | — | 200 application/json: PlanSlotPage |
| listRecipes | GET | /api/v1/households/{householdId}/recipes | A | — | 200 application/json: RecipePage |
| listWishlist | GET | /api/v1/me/wishlist | A | — | 200 application/json: RecipePage |
| login | POST | /api/v1/auth/login | E | application/json: LoginRequest | 200 application/json: AuthSession |
| logout | POST | /api/v1/auth/logout | E | — | 204 — |
| matchRecipes | POST | /api/v1/households/{householdId}/recipe-matches | C | application/json: MatchRequest | 200 application/json: RecipeMatch[] |
| previewCooking | POST | /api/v1/households/{householdId}/cooking-previews | D+E | application/json: CookingPreviewRequest | 200 application/json: CookingPreview |
| register | POST | /api/v1/auth/register | E | application/json: RegisterRequest | 201 application/json: User |
| removeMember | DELETE | /api/v1/households/{householdId}/members/{userId} | E | — | 204 — |
| removeWishlist | DELETE | /api/v1/me/wishlist/{recipeId} | A | — | 204 — |
| replacePlanSlot | PUT | /api/v1/households/{householdId}/plan-slots/{date}/{mealType} | B | application/json: ReplacePlanSlotRequest | 200 application/json: PlanSlot |
| setPlanSlotLock | PUT | /api/v1/households/{householdId}/plan-slots/{date}/{mealType}/lock | B | application/json: SetPlanLockRequest | 200 application/json: PlanSlot |
| setRecipeArchived | PUT | /api/v1/households/{householdId}/recipes/{recipeId}/archive | A | application/json: ArchiveRecipeRequest | 200 application/json: Recipe |
| updateInventoryBatch | PUT | /api/v1/households/{householdId}/inventory-batches/{batchId} | C | application/json: UpdateInventoryBatchRequest | 200 application/json: InventoryBatch |
| updateMealEntry | PUT | /api/v1/me/meal-entries/{entryId} | D | application/json: UpdateMealEntryRequest | 200 application/json: MealEntry |
| updatePreferences | PUT | /api/v1/me/preferences | E | application/json: UpdatePreferenceRequest | 200 application/json: Preference |
| updateRecipe | PUT | /api/v1/households/{householdId}/recipes/{recipeId} | A | application/json: UpdateRecipeRequest | 200 application/json: Recipe |
| uploadMedia | POST | /api/v1/media | E | multipart/form-data: object | 201 application/json: Media |
