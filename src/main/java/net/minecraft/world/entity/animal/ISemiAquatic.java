package net.minecraft.world.entity.animal;

public interface ISemiAquatic {

    boolean shouldEnterWater();

    boolean shouldLeaveWater();

    boolean shouldStopMoving();

    int getWaterSearchRange();
}
