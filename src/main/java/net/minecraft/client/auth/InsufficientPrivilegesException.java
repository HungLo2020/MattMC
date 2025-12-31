package net.minecraft.client.auth;

/**
 * Stub exception for insufficient privileges - not used in offline mode.
 */
public class InsufficientPrivilegesException extends AuthenticationException {
    public InsufficientPrivilegesException(String message) {
        super(message);
    }
}
