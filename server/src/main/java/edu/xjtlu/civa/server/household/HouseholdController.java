package edu.xjtlu.civa.server.household;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.api.HouseholdApi;
import edu.xjtlu.civa.server.api.model.CreateHouseholdRequest;
import edu.xjtlu.civa.server.api.model.Household;
import edu.xjtlu.civa.server.api.model.Invitation;
import edu.xjtlu.civa.server.api.model.JoinHouseholdRequest;
import edu.xjtlu.civa.server.api.model.Membership;
import edu.xjtlu.civa.server.api.model.MembershipPage;
import edu.xjtlu.civa.server.api.model.Role;
import edu.xjtlu.civa.server.application.CanonicalRequestDigest;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HouseholdController implements HouseholdApi {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final HouseholdReadService read;
    private final HouseholdWriteService write;
    private final CanonicalRequestDigest digest;
    private final JsonNode schemas;
    private final ObjectMapper json;

    HouseholdController(HouseholdReadService read, HouseholdWriteService write, CanonicalRequestDigest digest, JsonNode schemas, ObjectMapper json) {
        this.read = read; this.write = write; this.digest = digest; this.schemas = schemas; this.json = json;
    }

    @Override public ResponseEntity<Household> createHousehold(UUID key, CreateHouseholdRequest input) {
        String name = input.getName().trim();
        byte[] hash = hash("/api/v1/households", new CreateHouseholdRequest(name), "CreateHouseholdRequest");
        return response(write.createHousehold(actor(), key, hash, name), Household.class);
    }

    @Override public ResponseEntity<Membership> joinHousehold(UUID key, JoinHouseholdRequest input) {
        String code;
        try { code = InvitationCodes.normalize(input.getCode()); }
        catch (IllegalArgumentException ex) { throw new HouseholdWriteService.Failure("INVALID_INVITATION"); }
        byte[] hash = hash("/api/v1/households/join", new JoinHouseholdRequest(code), "JoinHouseholdRequest");
        return response(write.joinHousehold(actor(), key, hash, code), Membership.class);
    }

    @Override public ResponseEntity<Household> getHousehold(UUID id) {
        HouseholdReadService.Household home = read.getHousehold(actor(), id);
        return ResponseEntity.ok(new Household(home.id, time(home.createdAt), time(home.updatedAt), home.name, home.zoneId, home.version));
    }

    @Override public ResponseEntity<MembershipPage> listMembers(UUID id, Integer page, Integer size) {
        HouseholdReadService.MemberPage result = read.listMembers(actor(), id, page, size);
        var members = result.items.stream().map(m -> new Membership(m.id, time(m.createdAt), time(m.updatedAt),
                m.householdId, m.userId, Role.valueOf(m.role), m.active, m.version)).toList();
        return ResponseEntity.ok(new MembershipPage(members, result.page, result.size, result.total));
    }

    @Override public ResponseEntity<Invitation> createInvitation(UUID id, UUID key) { throw new NotImplemented(); }
    @Override public ResponseEntity<Void> removeMember(UUID id, UUID userId, Long version) { throw new NotImplemented(); }

    private byte[] hash(String path, Object input, String schema) {
        try { return digest.digestJson("POST", path, json.writeValueAsString(input), schemas.get(schema)); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid request"); }
    }
    private <T> ResponseEntity<T> response(IdempotencyExecutor.Result result, Class<T> type) {
        try { return ResponseEntity.status(result.status).body(json.readValue(result.body, type)); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored response"); }
    }
    private static UUID actor() { return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal(); }
    private static String time(Instant instant) { return TIME.format(instant); }
    static final class NotImplemented extends RuntimeException { }
}
