package net.alexsmobs.entity;

public interface ISemiAquatic {

    boolean shouldEnterWater();

    boolean shouldLeaveWater();

    boolean shouldStopMoving();

    int getWaterSearchRange();
}
