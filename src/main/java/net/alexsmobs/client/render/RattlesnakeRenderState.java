package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class RattlesnakeRenderState extends LivingEntityRenderState {
    public float curlProgress;
    public float prevCurlProgress;
    public int randomToungeTick;
    public boolean isRattling;
}
