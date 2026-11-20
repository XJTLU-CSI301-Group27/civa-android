package edu.xjtlu.civa.server.web;

import edu.xjtlu.civa.server.api.InventoryApi;
import edu.xjtlu.civa.server.api.MediaApi;
import edu.xjtlu.civa.server.api.model.*;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Explicit 501 placeholders for the generated contracts that do not yet have implementations. */
@RestController
class UnimplementedController implements InventoryApi, MediaApi {

  @Override
  public ResponseEntity<InventoryMovement> changeInventory(
      UUID householdId, UUID batchId, UUID key, InventoryChangeRequest body) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<InventoryBatch> createInventoryBatch(
      UUID householdId, UUID key, CreateInventoryBatchRequest body) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<IngredientPage> listIngredients(String q, Integer page, Integer size) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<InventoryBatchPage> listInventoryBatches(
      UUID householdId, UUID ingredientId, ExpiryStatus status, Integer page, Integer size) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<InventoryMovementPage> listInventoryMovements(
      UUID householdId, UUID batchId, Integer page, Integer size) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<List<RecipeMatch>> matchRecipes(UUID householdId, MatchRequest body) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<InventoryBatch> updateInventoryBatch(
      UUID householdId, UUID batchId, UpdateInventoryBatchRequest body) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<Resource> getMediaContent(UUID mediaId) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<Media> uploadMedia(
      UUID key, MultipartFile file, MediaScope scope, UUID householdId) {
    throw new NotImplemented();
  }

  static final class NotImplemented extends RuntimeException {}
}
