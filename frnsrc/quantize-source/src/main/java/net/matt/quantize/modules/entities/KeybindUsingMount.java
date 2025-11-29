package net.matt.quantize.modules.entities;

import net.minecraft.world.entity.Entity;

public interface KeybindUsingMount {
    void onKeyPacket(Entity keyPresser, int type);
}
