package net.alexscaves.server.entity.util;

import net.minecraft.world.entity.Entity;

public interface KeybindUsingMount {
    void onKeyPacket(Entity keyPresser, int type);
}
