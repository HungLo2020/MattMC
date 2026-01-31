package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public class UnderminerRenderState extends HumanoidRenderState {
    public boolean isDwarf;
    public int variant;
    public float hidingProgress;
    public float prevHidingProgress;
    public boolean isFullyHidden;
    public float alpha = 1.0F;
    @Nullable
    public BlockPos miningPos;
    public float miningProgress;
}
