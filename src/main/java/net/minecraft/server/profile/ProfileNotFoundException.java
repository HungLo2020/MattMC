package net.minecraft.server.profile;

/**
 * Stub exception for profile lookup failures - not used in offline mode.
 * Kept for compilation compatibility with legacy player data converters.
 */
public class ProfileNotFoundException extends Exception {
    public ProfileNotFoundException(String message) {
        super(message);
    }
}
