package net.minecraft.world.entity.animal.subterranodon;

import net.minecraft.world.entity.Entity;

/**
 * Interface for mounts that respond to custom keybinds.
 * Based on AlexsCaves KeybindUsingMount interface.
 */
public interface KeybindUsingMount {
    
    /**
     * Called when a player presses a custom key while riding this mount.
     * @param keyPresser The entity pressing the key (usually the player)
     * @param type The type of key pressed (0 = up, 1 = down, etc.)
     */
    void onKeyPacket(Entity keyPresser, int type);
}
