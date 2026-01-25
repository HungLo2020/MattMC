package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public class UnderminerRenderState extends HumanoidRenderState {
    public boolean isDwarf;
    public int variant;
    public float hidingProgress;
    public float prevHidingProgress;
    public boolean isFullyHidden;
    @Nullable
    public BlockPos miningPos;
    public float miningProgress;
}
