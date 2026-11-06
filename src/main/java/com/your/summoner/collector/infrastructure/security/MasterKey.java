package com.your.summoner.collector.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** The 32-byte master key (or its absence). Never prints its content. */
public final class MasterKey {
    private final byte[] bytes;

    private MasterKey(byte[] bytes) {
        this.bytes = bytes;
    }

    /** Blank means "not configured"; anything else must be Base64 of exactly 32 bytes. */
    public static MasterKey parse(String base64) {
        if (base64 == null || base64.isBlank()) {
            return new MasterKey(null);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("collector.master-key (YOURSUMMONER_MASTER_KEY) is not valid Base64");
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "collector.master-key (YOURSUMMONER_MASTER_KEY) must decode to exactly 32 bytes");
        }
        return new MasterKey(decoded);
    }

    public boolean present() {
        return bytes != null;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    /** HKDF-SHA256 (RFC 5869), zero salt, 32 bytes of output. */
    public byte[] derive(String info) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(bytes);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 1);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "MasterKey[***]";
    }
}
