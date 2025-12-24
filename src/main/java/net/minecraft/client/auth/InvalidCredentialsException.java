package net.minecraft.client.auth;

/**
 * Stub exception for invalid credentials - not used in offline mode.
 */
public class InvalidCredentialsException extends AuthenticationException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
