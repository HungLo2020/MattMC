package net.minecraft.client.auth;

/**
 * Stub exception for insecure public keys - not used in offline mode.
 */
public class InsecurePublicKeyException extends Exception {
    public InsecurePublicKeyException(String message) {
        super(message);
    }
    
    public static class MissingException extends InsecurePublicKeyException {
        public MissingException(String message) {
            super(message);
        }
    }
}
