package edu.xjtlu.civa.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.CivaServer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CivaServer.class, properties = "civa.auth.failure-limit=3")
@AutoConfigureMockMvc
@Transactional
class SecurityFoundationIntegrationTest {
    @Autowired MockMvc http;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test void bearerSessionProtectsRoutesAndRevocationTakesEffect() throws Exception {
        assertTrue(context.containsBean("testMigrationStrategy"));
        assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty());
        var denied = http.perform(get("/api/v1/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
        var error = json.readTree(denied.getResponse().getContentAsString());
        assertEquals("UNAUTHENTICATED", error.path("code").asText());
        assertTrue(error.path("fieldErrors").isArray());
        assertEquals(0, error.path("fieldErrors").size());
        assertNotNull(UUID.fromString(error.path("traceId").asText()));
        http.perform(get("/api/v1/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());

        String name = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String register = json.writeValueAsString(Map.of("username", name.toUpperCase(), "displayName", "😀".repeat(120), "password", "long-password", "zoneId", "UTC"));
        var registered = http.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.householdId").doesNotExist()).andReturn();
        String userId = json.readTree(registered.getResponse().getContentAsString()).path("id").asText();
        assertTrue(json.readTree(registered.getResponse().getContentAsString()).has("householdId"));
        http.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        http.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(register.replaceFirst("\\{", "{\"unexpected\":true,")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String loginBody = json.writeValueAsString(Map.of("username", name, "password", "long-password"));
        var loggedIn = http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(userId)).andReturn();
        String token = json.readTree(loggedIn.getResponse().getContentAsString()).path("token").asText();
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId));
        http.perform(get("/api/v1/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_IMPLEMENTED"));
        http.perform(get("/api/v1/ingredients").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_IMPLEMENTED"));
        http.perform(get("/api/v1/media/{id}/content", UUID.randomUUID()).header("Authorization", "Bearer " + token)
                        .accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotImplemented()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_IMPLEMENTED"));
        String otherName = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        AuthService.User other = auth.register(otherName, "Other", "long-password", "UTC");
        AuthService.Login otherLogin = auth.login(otherName, "long-password");
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + otherLogin.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(other.id.toString()));
        http.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        http.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + otherLogin.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(other.id.toString()));
        jdbc.update("UPDATE sessions SET expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE user_id=?", other.id);
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + otherLogin.token)).andExpect(status().isUnauthorized());
        for (String attemptedName : new String[]{name, "missing_user"}) {
            String invalid = json.writeValueAsString(Map.of("username", attemptedName, "password", "wrong-password"));
            http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
        String invalid = json.writeValueAsString(Map.of("username", name, "password", "wrong-password"));
        http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnauthorized());
        var limited = http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertTrue(Long.parseLong(limited.getResponse().getHeader("Retry-After")) > 0);
    }

}
