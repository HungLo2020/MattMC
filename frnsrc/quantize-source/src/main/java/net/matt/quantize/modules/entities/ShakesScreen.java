package net.matt.quantize.modules.entities;

import net.minecraft.world.entity.Entity;

public interface ShakesScreen {

    default boolean canFeelShake(Entity player) {
        return player.onGround();
    }

    float getScreenShakeAmount(float partialTicks);

    default double getShakeDistance() {
        return 20F;
    }

}
