package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class KangarooRenderState extends LivingEntityRenderState {
    public float sitProgress;
    public float standProgress;
    public float pouchProgress;
    public float totalMovingProgress;
    public boolean isStanding;
    public boolean isSitting;
    public int visualFlag;
    public int pouchTick;
    public int helmetIndex;
    public int swordIndex;
    public int chestIndex;
    public Animation animation;
    public int animationTick;
    public float jumpCompletion;
    public boolean isLeftHanded;
    public boolean vehicleIsKangaroo;
    public boolean isPassenger;
}
