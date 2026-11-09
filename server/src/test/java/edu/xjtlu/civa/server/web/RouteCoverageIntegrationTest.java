package edu.xjtlu.civa.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.CivaServer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CivaServer.class)
class RouteCoverageIntegrationTest {
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired ObjectMapper json;

    @Test void registeredRoutesExactlyMatchCandidateContract() throws Exception {
        Set<String> expected = new HashSet<>();
        var paths = json.readTree(new ClassPathResource("contracts/openapi.json").getInputStream()).path("paths");
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (Set.of("get", "post", "put", "delete", "patch").contains(method.getKey())) {
                assertTrue(expected.add(method.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey()));
            }
        }));
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            for (String path : mapping.getPatternValues()) {
                if (path.equals("/error")) continue;
                assertFalse(mapping.getMethodsCondition().getMethods().isEmpty(), "Unrestricted route: " + path);
                mapping.getMethodsCondition().getMethods().forEach(method -> {
                    if (!method.name().equals("HEAD") && !method.name().equals("OPTIONS")) {
                        assertTrue(actual.add(method.name() + " " + path), "Duplicate route: " + path);
                    }
                });
            }
        });
        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
    }
}
