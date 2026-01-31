package net.alexsmobs.client.render;

import net.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.DyeColor;

public class RaccoonRenderState extends LivingEntityRenderState {
    public float begProgress;
    public float standProgress;
    public float sitProgress;
    public float washProgress;
    public boolean isRigby;
    public boolean hasColor;
    public DyeColor color;
    public int tickCount;
    public String customName;
    public int animationTick;
    public Animation currentAnimation;
    public int id;
}
