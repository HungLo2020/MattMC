package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class AnacondaPartRenderState extends LivingEntityRenderState {
    public int bodyIndex;
    public AnacondaPartIndex partType;
    public float swell;
    public float strangleProgress;
    public boolean isYellow;
    public boolean isShedding;
    public float scale;
}
