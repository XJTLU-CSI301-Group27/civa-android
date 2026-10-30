package edu.xjtlu.civa.server.household;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvitationCodesTest {
    @Test void validatesCanonicalBase32AndHashesNormalizedValue() throws Exception {
        byte[] zeros = new byte[16];
        byte[] ones = new byte[16];
        Arrays.fill(ones, (byte) 0xff);
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAA", InvitationCodes.encode(zeros));
        assertEquals("77777777777777777777777774", InvitationCodes.encode(ones));
        byte[] sequence = new byte[16];
        for (int i = 0; i < sequence.length; i++) sequence[i] = (byte) i;
        String canonical = "AAAQEAYEAUDAOCAJBIFQYDIOB4";
        assertEquals(canonical, InvitationCodes.encode(sequence));
        assertEquals(canonical, InvitationCodes.normalize("aaaqeayeaudaocaj\u00a0bifqydiob4\n"));
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.US_ASCII)), InvitationCodes.hash(canonical.toLowerCase()));
        assertThrows(IllegalArgumentException.class, () -> InvitationCodes.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> InvitationCodes.normalize(canonical + "="));
        assertThrows(IllegalArgumentException.class, () -> InvitationCodes.normalize(canonical.substring(0, 25) + "5"));
        assertThrows(IllegalArgumentException.class, () -> InvitationCodes.normalize(canonical.substring(0, 25) + "0"));
        assertEquals(26, InvitationCodes.generate(new SecureRandom()).length());
    }
}
