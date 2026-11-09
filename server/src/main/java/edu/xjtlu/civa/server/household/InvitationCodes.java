package edu.xjtlu.civa.server.household;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

final class InvitationCodes {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private InvitationCodes() {}

    static String generate(SecureRandom random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return encode(bytes);
    }

    static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("Invalid invitation");
        StringBuilder compact = new StringBuilder();
        input.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).forEach(compact::appendCodePoint);
        String code = compact.toString().toUpperCase(Locale.ROOT);
        if (code.length() != 26) throw new IllegalArgumentException("Invalid invitation");
        byte[] bytes = new byte[16];
        int bits = 0, buffer = 0, offset = 0;
        for (int i = 0; i < code.length(); i++) {
            int value = ALPHABET.indexOf(code.charAt(i));
            if (value < 0) throw new IllegalArgumentException("Invalid invitation");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                if (offset == bytes.length) throw new IllegalArgumentException("Invalid invitation");
                bytes[offset++] = (byte) (buffer >> bits);
            }
        }
        if (offset != bytes.length || !encode(bytes).equals(code)) throw new IllegalArgumentException("Invalid invitation");
        return code;
    }

    static byte[] hash(String input) {
        try { return MessageDigest.getInstance("SHA-256").digest(normalize(input).getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    static String encode(byte[] bytes) {
        if (bytes.length != 16) throw new IllegalArgumentException("Invitation requires 128 bits");
        StringBuilder code = new StringBuilder(26);
        int bits = 0, buffer = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                code.append(ALPHABET.charAt((buffer >> bits) & 31));
            }
        }
        if (bits > 0) code.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return code.toString();
    }
}
