package edu.xjtlu.civa.server.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.InventoryApi;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.application.CanonicalRequestDigest;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InventoryController implements InventoryApi {
  private final InventoryService inventory;
  private final InventoryMatchingService matching;
  private final CanonicalRequestDigest digest;
  private final JsonNode schemas;
  private final ObjectMapper json;

  InventoryController(
      InventoryService inventory,
      InventoryMatchingService matching,
      CanonicalRequestDigest digest,
      JsonNode schemas,
      ObjectMapper json) {
    this.inventory = inventory;
    this.matching = matching;
    this.digest = digest;
    this.schemas = schemas;
    this.json = json;
  }

  @Override
  public ResponseEntity<IngredientPage> listIngredients(String q, Integer page, Integer size) {
    return ResponseEntity.ok(
        inventory.ingredients(q, page == null ? 0 : page, size == null ? 20 : size));
  }

  @Override
  public ResponseEntity<Ingredient> createIngredient(UUID key, CreateIngredientRequest body) {
    return response(
        inventory.createIngredient(
            actor(), key, hash("/api/v1/ingredients", body, "CreateIngredientRequest"), body),
        Ingredient.class);
  }

  @Override
  public ResponseEntity<InventoryBatchPage> listInventoryBatches(
      UUID home, UUID ingredient, ExpiryStatus status, Integer page, Integer size) {
    return ResponseEntity.ok(
        inventory.batches(
            actor(), home, ingredient, status, page == null ? 0 : page, size == null ? 20 : size));
  }

  @Override
  public ResponseEntity<InventoryMovementPage> listInventoryMovements(
      UUID home, UUID batch, Integer page, Integer size) {
    return ResponseEntity.ok(
        inventory.movements(
            actor(), home, batch, page == null ? 0 : page, size == null ? 20 : size));
  }

  @Override
  public ResponseEntity<InventoryBatch> createInventoryBatch(
      UUID home, UUID key, CreateInventoryBatchRequest body) {
    return response(
        inventory.create(
            actor(),
            home,
            key,
            hash(
                "/api/v1/households/" + home + "/inventory-batches",
                body,
                "CreateInventoryBatchRequest"),
            body),
        InventoryBatch.class);
  }

  @Override
  public ResponseEntity<InventoryBatch> updateInventoryBatch(
      UUID home, UUID id, UpdateInventoryBatchRequest body) {
    return ResponseEntity.ok(inventory.update(actor(), home, id, body));
  }

  @Override
  public ResponseEntity<InventoryMovement> changeInventory(
      UUID home, UUID id, UUID key, InventoryChangeRequest body) {
    return response(
        inventory.change(
            actor(),
            home,
            id,
            key,
            hash(
                "/api/v1/households/" + home + "/inventory-batches/" + id + "/movements",
                body,
                "InventoryChangeRequest"),
            body),
        InventoryMovement.class);
  }

  @Override
  public ResponseEntity<List<RecipeMatch>> matchRecipes(UUID home, MatchRequest body) {
    return ResponseEntity.ok(matching.match(actor(), home, body));
  }

  private byte[] hash(String path, Object body, String schema) {
    try {
      return digest.digestJson("POST", path, json.writeValueAsString(body), schemas.get(schema));
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Invalid request", error);
    }
  }

  private <T> ResponseEntity<T> response(IdempotencyExecutor.Result result, Class<T> type) {
    try {
      return ResponseEntity.status(result.status).body(json.readValue(result.body, type));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid stored response", error);
    }
  }

  private static UUID actor() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
