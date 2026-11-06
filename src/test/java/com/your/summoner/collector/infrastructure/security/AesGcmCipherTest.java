package com.your.summoner.collector.infrastructure.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class AesGcmCipherTest {
    @Test
    void roundTrip() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmCipher cipher = new AesGcmCipher(key);
        String original = "RGAPI-0000-segredo";
        String encrypted = cipher.encrypt(original);
        String decrypted = cipher.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void sameValueGivesDifferentCiphertext() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmCipher cipher = new AesGcmCipher(key);
        String original = "RGAPI-0000-segredo";
        String encrypted1 = cipher.encrypt(original);
        String encrypted2 = cipher.encrypt(original);
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void tamperedCiphertextFails() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmCipher cipher = new AesGcmCipher(key);
        String original = "RGAPI-0000-segredo";
        String encrypted = cipher.encrypt(original);
        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length / 2] ^= 0x01;
        String tamperedEncrypted = Base64.getEncoder().encodeToString(tampered);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tamperedEncrypted));
    }

    @Test
    void wrongKeyFails() {
        byte[] key1 = new byte[32];
        new SecureRandom().nextBytes(key1);
        byte[] key2 = new byte[32];
        new SecureRandom().nextBytes(key2);
        AesGcmCipher cipher1 = new AesGcmCipher(key1);
        AesGcmCipher cipher2 = new AesGcmCipher(key2);
        String original = "RGAPI-0000-segredo";
        String encrypted = cipher1.encrypt(original);
        assertThrows(IllegalStateException.class, () -> cipher2.decrypt(encrypted));
    }

    @Test
    void rejectsShortKey() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmCipher(new byte[16]));
    }

    @Test
    void exceptionMessageDoesNotLeak() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmCipher cipher = new AesGcmCipher(key);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> cipher.decrypt("lixo-RGAPI"));
        assertFalse(e.getMessage().contains("RGAPI"));
    }

    @Test
    void toStringHidesKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmCipher cipher = new AesGcmCipher(key);
        assertEquals("AesGcmCipher[***]", cipher.toString());
    }
}
