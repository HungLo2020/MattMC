package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.alexsmobs.entity.EntityRaccoon;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.phys.Vec3;

/**
 * Stub class for ModelRaccoon
 * Minimal implementation to satisfy BlueJay renderer requirements
 */
public class ModelRaccoon extends AdvancedEntityModel<EntityRaccoon> {
    
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
    public void setupAnim(EntityRaccoon entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
    
    public Vec3 getRidingPosition(Vec3 basePosition) {
        return basePosition;
    }
}
