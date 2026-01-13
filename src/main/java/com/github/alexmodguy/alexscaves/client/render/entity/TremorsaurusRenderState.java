package com.github.alexmodguy.alexscaves.client.render.entity;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class TremorsaurusRenderState extends LivingEntityRenderState {
    public int altSkin;
    public boolean running;
    public int heldMobId;
    public float meterAmount;
    public Animation animation;
    public int animationTick;
    public String customName;
}
