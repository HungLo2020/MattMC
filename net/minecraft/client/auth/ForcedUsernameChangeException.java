package net.minecraft.client.auth;

/**
 * Stub exception for forced username changes - not used in offline mode.
 */
public class ForcedUsernameChangeException extends AuthenticationException {
    public ForcedUsernameChangeException(String message) {
        super(message);
    }
}
