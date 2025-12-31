package net.minecraft.server.profile;

/**
 * Stub for ProfileLookupCallback - not used in offline mode.
 * Kept for compilation compatibility with legacy code.
 */
public interface ProfileLookupCallback {
    void onProfileLookupSucceeded(PlayerProfile playerProfile);
    void onProfileLookupFailed(String username, Exception exception);
}
