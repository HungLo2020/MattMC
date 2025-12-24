package net.minecraft.client.auth;

import java.security.PublicKey;
import java.time.Instant;

/**
 * Stub for key pair response from Yggdrasil services.
 * Not used in offline mode.
 */
public record KeyPairResponse(KeyPair keyPair, PublicKey publicKeySignature, byte[] publicKeySignatureV2, Instant expiresAt, Instant refreshedAfter) {
    
    public record KeyPair(String privateKey, String publicKey) {
    }
}
