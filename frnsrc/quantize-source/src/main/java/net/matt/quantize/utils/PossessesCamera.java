package net.matt.quantize.utils;

import net.minecraft.world.entity.Entity;

public interface PossessesCamera {

    float getPossessionStrength(float f);

    boolean instant();

    boolean isPossessionBreakable();

    void onPossessionKeyPacket(Entity keyPresser, int type);
}
