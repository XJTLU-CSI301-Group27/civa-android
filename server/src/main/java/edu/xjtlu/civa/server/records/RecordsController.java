package edu.xjtlu.civa.server.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.RecordsApi;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.application.CanonicalRequestDigest;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import edu.xjtlu.civa.server.planning.BdRules;
import edu.xjtlu.civa.server.planning.PlanningService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RecordsController implements RecordsApi {
  private final RecordsService service;
  private final PlanningService access;
  private final IdempotencyExecutor idempotency;
  private final CanonicalRequestDigest digest;
  private final ObjectMapper json;
  private final JsonNode schemas;

  RecordsController(
      RecordsService service,
      PlanningService access,
      IdempotencyExecutor idempotency,
      CanonicalRequestDigest digest,
      ObjectMapper json,
      JsonNode schemas) {
    this.service = service;
    this.access = access;
    this.idempotency = idempotency;
    this.digest = digest;
    this.json = json;
    this.schemas = schemas;
  }

  @Override
  public ResponseEntity<MealEntryPage> listMealEntries(
      String from, String to, Integer page, Integer size) {
    return ResponseEntity.ok(service.list(actor(), from, to, page, size));
  }

  @Override
  public ResponseEntity<MealEntry> createMealEntry(UUID key, CreateMealEntryRequest body) {
    UUID actor = actor();
    try {
      byte[] hash =
          digest.digestJson(
              "POST",
              "/api/v1/me/meal-entries",
              json.writeValueAsString(body),
              schemas.get("CreateMealEntryRequest"));
      var result =
          idempotency.execute(
              actor,
              key,
              hash,
              () -> {
                MealEntry entry = service.create(actor, body);
                try {
                  return new IdempotencyExecutor.Result(201, json.writeValueAsString(entry));
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              },
              replay -> {
                /* actor-scoped personal result, independent of current household */
              });
      return ResponseEntity.status(result.status)
          .body(json.readValue(result.body, MealEntry.class));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid request");
    }
  }

  @Override
  public ResponseEntity<MealEntry> updateMealEntry(UUID id, UpdateMealEntryRequest body) {
    return ResponseEntity.ok(service.update(actor(), id, body));
  }

  @Override
  public ResponseEntity<Void> deleteMealEntry(UUID id, Long version) {
    service.delete(actor(), id, version);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<PeriodSummary> getMealSummary(String from, String to) {
    return ResponseEntity.ok(service.summary(actor(), from, to));
  }

  @Override
  public ResponseEntity<CookingEventPage> listCookingEvents(
      UUID home, UUID recipe, String from, String to, Integer page, Integer size) {
    return ResponseEntity.ok(service.cooking(actor(), home, recipe, from, to, page, size));
  }

  // AC inventory service and EF CookingCoordinator do not exist in the downloaded main.
  // Keep the declared contract and a truthful error; never write a cooking event without stock
  // transaction.
  @Override
  public ResponseEntity<CookingPreview> previewCooking(UUID home, CookingPreviewRequest body) {
    access.requireMember(actor(), home, false);
    throw new BdRules.Failure("CONTRACT_NOT_IMPLEMENTED");
  }

  @Override
  public ResponseEntity<CookingEvent> confirmCooking(
      UUID home, UUID key, ConfirmCookingRequest body) {
    access.requireMember(actor(), home, false);
    throw new BdRules.Failure("CONTRACT_NOT_IMPLEMENTED");
  }

  private static UUID actor() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
