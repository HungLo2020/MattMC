package net.minecraft.client.auth;

/**
 * Stub exception for when authentication services are unavailable - not used in offline mode.
 */
public class AuthenticationUnavailableException extends AuthenticationException {
    public AuthenticationUnavailableException(String message) {
        super(message);
    }
}
