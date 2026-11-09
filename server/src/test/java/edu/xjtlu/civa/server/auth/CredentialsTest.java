package edu.xjtlu.civa.server.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialsTest {
    @Test void validatesAndNormalizesCredentials() {
        assertEquals("alice_01", Credentials.normalizeUsername("Alice_01"));
        assertEquals("key", Credentials.normalizeUsername("\u212Aey"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.normalizeUsername("ab"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.normalizeUsername(" bad "));
        assertThrows(IllegalArgumentException.class, () -> Credentials.normalizeUsername(null));
        assertDoesNotThrow(() -> Credentials.validateNewPassword("x".repeat(72)));
        assertDoesNotThrow(() -> Credentials.validateNewPassword("😀".repeat(10)));
        assertDoesNotThrow(() -> Credentials.validateNewPassword(" 12345678 "));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validateNewPassword("x".repeat(9)));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validateNewPassword("x".repeat(73)));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validateNewPassword("😀".repeat(19)));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validateNewPassword("123456789\uD800"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.validateNewPassword(null));
    }
}
