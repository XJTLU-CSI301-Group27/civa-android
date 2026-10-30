package edu.xjtlu.civa.server.web;

import edu.xjtlu.civa.server.api.InventoryApi;
import edu.xjtlu.civa.server.api.MediaApi;
import edu.xjtlu.civa.server.api.PlanningApi;
import edu.xjtlu.civa.server.api.RecipeApi;
import edu.xjtlu.civa.server.api.RecordsApi;
import edu.xjtlu.civa.server.api.model.*;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Explicit 501 placeholders for the generated contracts that do not yet have implementations. */
@RestController
class UnimplementedController implements RecipeApi, InventoryApi, PlanningApi, RecordsApi, MediaApi {
    @Override public ResponseEntity<Void> addWishlist(UUID recipeId) { throw new NotImplemented(); }
    @Override public ResponseEntity<Recipe> createRecipe(UUID householdId, UUID key, CreateRecipeRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<Recipe> getRecipe(UUID householdId, UUID recipeId) { throw new NotImplemented(); }
    @Override public ResponseEntity<RecipePage> listRecipes(UUID householdId, String q, String tag, UUID ingredientId, Boolean archived, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<RecipePage> listWishlist(Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<Void> removeWishlist(UUID recipeId) { throw new NotImplemented(); }
    @Override public ResponseEntity<Recipe> setRecipeArchived(UUID householdId, UUID recipeId, ArchiveRecipeRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<Recipe> updateRecipe(UUID householdId, UUID recipeId, UpdateRecipeRequest body) { throw new NotImplemented(); }

    @Override public ResponseEntity<InventoryMovement> changeInventory(UUID householdId, UUID batchId, UUID key, InventoryChangeRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<InventoryBatch> createInventoryBatch(UUID householdId, UUID key, CreateInventoryBatchRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<IngredientPage> listIngredients(String q, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<InventoryBatchPage> listInventoryBatches(UUID householdId, UUID ingredientId, ExpiryStatus status, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<InventoryMovementPage> listInventoryMovements(UUID householdId, UUID batchId, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<List<RecipeMatch>> matchRecipes(UUID householdId, MatchRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<InventoryBatch> updateInventoryBatch(UUID householdId, UUID batchId, UpdateInventoryBatchRequest body) { throw new NotImplemented(); }

    @Override public ResponseEntity<List<PlanSlot>> commitPlan(UUID householdId, UUID key, PlanCommitRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<PlanSlotPage> listPlanSlots(UUID householdId, String from, String to, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<PlanSlot> replacePlanSlot(UUID householdId, String date, PlanMealType mealType, ReplacePlanSlotRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<PlanSlot> setPlanSlotLock(UUID householdId, String date, PlanMealType mealType, SetPlanLockRequest body) { throw new NotImplemented(); }

    @Override public ResponseEntity<CookingEvent> confirmCooking(UUID householdId, UUID key, ConfirmCookingRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<MealEntry> createMealEntry(UUID key, CreateMealEntryRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<Void> deleteMealEntry(UUID entryId, Long version) { throw new NotImplemented(); }
    @Override public ResponseEntity<PeriodSummary> getMealSummary(String from, String to) { throw new NotImplemented(); }
    @Override public ResponseEntity<CookingEventPage> listCookingEvents(UUID householdId, UUID recipeId, String from, String to, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<MealEntryPage> listMealEntries(String from, String to, Integer page, Integer size) { throw new NotImplemented(); }
    @Override public ResponseEntity<CookingPreview> previewCooking(UUID householdId, CookingPreviewRequest body) { throw new NotImplemented(); }
    @Override public ResponseEntity<MealEntry> updateMealEntry(UUID entryId, UpdateMealEntryRequest body) { throw new NotImplemented(); }

    @Override public ResponseEntity<Resource> getMediaContent(UUID mediaId) { throw new NotImplemented(); }
    @Override public ResponseEntity<Media> uploadMedia(UUID key, MultipartFile file, MediaScope scope, UUID householdId) { throw new NotImplemented(); }

    static final class NotImplemented extends RuntimeException { }
}
