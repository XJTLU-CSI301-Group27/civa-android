package edu.xjtlu.civa.server.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.PlanningApi;
import edu.xjtlu.civa.server.api.model.*;
import edu.xjtlu.civa.server.application.CanonicalRequestDigest;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PlanningController implements PlanningApi {
  private final PlanningService service;
  private final IdempotencyExecutor idempotency;
  private final CanonicalRequestDigest digest;
  private final ObjectMapper json;
  private final JsonNode schemas;

  PlanningController(
      PlanningService service,
      IdempotencyExecutor idempotency,
      CanonicalRequestDigest digest,
      ObjectMapper json,
      JsonNode schemas) {
    this.service = service;
    this.idempotency = idempotency;
    this.digest = digest;
    this.json = json;
    this.schemas = schemas;
  }

  @Override
  public ResponseEntity<PlanSlotPage> listPlanSlots(
      UUID home, String from, String to, Integer page, Integer size) {
    return ResponseEntity.ok(service.list(actor(), home, from, to, page, size));
  }

  @Override
  public ResponseEntity<PlanSlot> replacePlanSlot(
      UUID home, String date, PlanMealType meal, ReplacePlanSlotRequest body) {
    return ResponseEntity.ok(
        service.replace(actor(), home, date, meal, body.getItems(), body.getExpectedVersion()));
  }

  @Override
  public ResponseEntity<PlanSlot> setPlanSlotLock(
      UUID home, String date, PlanMealType meal, SetPlanLockRequest body) {
    return ResponseEntity.ok(
        service.lock(actor(), home, date, meal, body.getLocked(), body.getExpectedVersion()));
  }

  @Override
  public ResponseEntity<List<PlanSlot>> commitPlan(UUID home, UUID key, PlanCommitRequest body) {
    UUID actor = actor();
    try {
      byte[] hash =
          digest.digestJson(
              "POST",
              "/api/v1/households/" + home + "/plan-commits",
              json.writeValueAsString(body),
              schemas.get("PlanCommitRequest"));
      var result =
          idempotency.execute(
              actor,
              key,
              hash,
              () -> {
                List<PlanSlot> slots = service.commit(actor, home, body.getSlots());
                try {
                  return new IdempotencyExecutor.Result(200, json.writeValueAsString(slots));
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              },
              replay -> service.requireMember(actor, home, true));
      return ResponseEntity.status(result.status)
          .body(
              json.readValue(
                  result.body,
                  json.getTypeFactory().constructCollectionType(List.class, PlanSlot.class)));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid request");
    }
  }

  private static UUID actor() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
