package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Stub class for ModelRaccoon
 * Minimal implementation to satisfy BlueJay renderer requirements
 */
public class ModelRaccoon extends AdvancedEntityModel<EntityRenderState> {
    
    public ModelRaccoon() {
        texWidth = 64;
        texHeight = 64;
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of();
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of();
    }

    @Override
    public void setupAnim(EntityRenderState renderState) {
    }
    
    public Vec3 getRidingPosition(Vec3 basePosition) {
        return basePosition;
    }
}
