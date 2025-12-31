package net.minecraft.client.auth;

/**
 * Stub exception for user bans - not used in offline mode.
 */
public class UserBannedException extends AuthenticationException {
    public UserBannedException(String message) {
        super(message);
    }
}
