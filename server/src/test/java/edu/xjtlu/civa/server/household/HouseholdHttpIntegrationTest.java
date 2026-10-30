package edu.xjtlu.civa.server.household;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.CivaServer;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CivaServer.class)
@AutoConfigureMockMvc
class HouseholdHttpIntegrationTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    @Test void householdWritesReplayAndMembershipReadsEnforceAccess() throws Exception {
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), outsider = UUID.randomUUID();
        UUID[] actors = {owner, member, outsider};
        UUID home = null;
        try {
            String hash = passwords.encode("long-password");
            for (UUID actor : actors) jdbc.update("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?)",
                    actor, username(actor), "Test", hash, "UTC");
            String ownerToken = login(owner), memberToken = login(member), outsiderToken = login(outsider);
            UUID key = UUID.randomUUID();
            var created = http.perform(post("/api/v1/households").header("Authorization", bearer(ownerToken))
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Shared home \"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Shared home")).andReturn();
            home = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
            http.perform(post("/api/v1/households").header("Authorization", bearer(ownerToken))
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Shared home\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(home.toString()));
            http.perform(post("/api/v1/households").header("Authorization", bearer(ownerToken))
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Different\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
            http.perform(post("/api/v1/households").header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            http.perform(post("/api/v1/households").header("Authorization", bearer(ownerToken))
                            .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"name\":\"Y\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            http.perform(get("/api/v1/households/{id}", home).header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

            String invite = InvitationCodes.generate(new SecureRandom());
            jdbc.update("INSERT INTO invitations(id,household_id,created_by,code_hash,expires_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), home, owner, InvitationCodes.hash(invite), Timestamp.from(Instant.now().plusSeconds(3600)));
            UUID joinKey = UUID.randomUUID();
            var joined = http.perform(post("/api/v1/households/join").header("Authorization", bearer(memberToken))
                            .header("Idempotency-Key", joinKey).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("code", " " + invite.toLowerCase(Locale.ROOT) + " "))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.householdId").value(home.toString())).andReturn();
            String membershipId = json.readTree(joined.getResponse().getContentAsString()).path("id").asText();
            http.perform(post("/api/v1/households/join").header("Authorization", bearer(memberToken))
                            .header("Idempotency-Key", joinKey).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("code", invite))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(membershipId));
            http.perform(get("/api/v1/households/{id}/members", home).header("Authorization", bearer(memberToken)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
            http.perform(post("/api/v1/households/{id}/invitations", home).header("Authorization", bearer(ownerToken))
                            .header("Idempotency-Key", UUID.randomUUID()))
                    .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_IMPLEMENTED"));
            jdbc.update("UPDATE memberships SET active=false WHERE household_id=? AND user_id=?", home, member);
            http.perform(get("/api/v1/households/{id}", home).header("Authorization", bearer(memberToken)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HOUSEHOLD_ACCESS_REVOKED"));
            http.perform(post("/api/v1/households/join").header("Authorization", bearer(memberToken))
                            .header("Idempotency-Key", joinKey).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("code", invite))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HOUSEHOLD_ACCESS_REVOKED"));
        } finally {
            // Only delete fixtures owned by the three random user IDs created by this test.
            for (UUID actor : actors) {
                jdbc.update("DELETE FROM idempotency_requests WHERE actor_id=?", actor);
                jdbc.update("DELETE FROM invitations WHERE created_by=?", actor);
                jdbc.update("DELETE FROM sessions WHERE user_id=?", actor);
            }
            if (home == null) {
                var ids = jdbc.query("SELECT household_id FROM memberships WHERE user_id=?", (rs, n) -> rs.getObject(1, UUID.class), owner);
                if (!ids.isEmpty()) home = ids.get(0);
            }
            boolean owned = home != null && Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM memberships WHERE household_id=? AND user_id=? AND role='OWNER')", Boolean.class, home, owner));
            for (UUID actor : actors) jdbc.update("DELETE FROM memberships WHERE user_id=?", actor);
            if (owned) jdbc.update("DELETE FROM households WHERE id=?", home);
            for (UUID actor : actors) jdbc.update("DELETE FROM users WHERE id=?", actor);
        }
    }

    private String login(UUID id) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username(id), "password", "long-password"));
        var result = http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
    private static String username(UUID id) { return "u_" + id.toString().replace("-", "").substring(0, 20); }
    private static String bearer(String token) { return "Bearer " + token; }
}
