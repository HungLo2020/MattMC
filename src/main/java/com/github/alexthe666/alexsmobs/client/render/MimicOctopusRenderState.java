package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.entity.EntityMimicOctopus;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;

public class MimicOctopusRenderState extends LivingEntityRenderState {
    public float transProgress;
    public float prevTransProgress;
    public float colorShiftProgress;
    public float prevColorShiftProgress;
    public float groundProgress;
    public float prevGroundProgress;
    public float sitProgress;
    public float prevSitProgress;
    public EntityMimicOctopus.MimicState mimicState;
    public EntityMimicOctopus.MimicState prevMimicState;
    public BlockState mimickedBlock;
    public BlockState prevMimickedBlock;
    public boolean hasGuardianLaser;
    public int guardianLaserId;
    public float scale;
}
