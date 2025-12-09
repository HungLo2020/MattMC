package net.minecraft.client.auth;

/**
 * Stub exception for authentication errors - not used in offline mode.
 */
public class AuthenticationException extends Exception {
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
