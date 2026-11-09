package edu.xjtlu.civa.server.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class Credentials {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,32}");

    private Credentials() {}

    static String normalizeUsername(String username) {
        if (username == null) throw new IllegalArgumentException("Invalid username");
        String normalized = username.toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) throw new IllegalArgumentException("Invalid username");
        return normalized;
    }

    static void validateNewPassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 10
                || !StandardCharsets.UTF_8.newEncoder().canEncode(password)
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Invalid password");
        }
    }
}
