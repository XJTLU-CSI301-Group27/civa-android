package edu.xjtlu.civa.server.testing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestDatabaseGuardUnitTest {
    @Test void onlyAcceptsExplicitPostgresTestDatabaseNames() {
        assertEquals("civa_test", TestDatabaseGuard.databaseFromUrl("jdbc:postgresql://localhost:5432/civa_test?sslmode=disable"));
        assertThrows(IllegalArgumentException.class, () -> TestDatabaseGuard.databaseFromUrl(null));
        assertThrows(IllegalArgumentException.class, () -> TestDatabaseGuard.databaseFromUrl("jdbc:postgresql://localhost/civa"));
        assertThrows(IllegalArgumentException.class, () -> TestDatabaseGuard.databaseFromUrl("jdbc:postgresql://localhost/civa_test/other_test"));
        assertThrows(IllegalArgumentException.class, () -> TestDatabaseGuard.databaseFromUrl("jdbc:h2:mem:civa_test"));
        assertThrows(IllegalArgumentException.class, () -> TestDatabaseGuard.databaseFromUrl("jdbc:postgresql:///civa_test"));
    }
}
