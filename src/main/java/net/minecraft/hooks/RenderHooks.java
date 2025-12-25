package net.minecraft.hooks;

/**
 * Hook interface for rendering system events.
 * Mods can implement this interface to hook into rendering system lifecycle without using mixins.
 */
public interface RenderHooks {
    /**
     * Called during flipFrame to allow mods to skip or replace the first pollEvents() call.
     * Return true to skip the default poll, false to allow it.
     * 
     * This is used by Sodium to work around a bug where Minecraft polls events twice.
     * 
     * @return true to skip the poll, false to allow it
     */
    default boolean shouldSkipFirstPollEvents() {
        return false;
    }
}
