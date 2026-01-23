package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.alexsmobs.entity.EntityAnteater;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Stub model for Leafcutter Ant - used for rendering ants on anteater tongue
 */
public class ModelLeafcutterAnt extends AdvancedEntityModel<EntityRenderState> {
    
    public final AdvancedModelBox root;
    
    public ModelLeafcutterAnt() {
        texWidth = 32;
        texHeight = 32;
        textureWidth = 32;
        textureHeight = 32;
        
        root = new AdvancedModelBox(this, "root");
        root.setRotationPoint(0.0F, 24.0F, 0.0F);
        // Simple box for ant body
        root.setTextureOffset(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.0F, false);
        this.updateDefaultPose();
    }
    
    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }
    
    @Override
    public void setupAnim(EntityRenderState renderState) {
        this.resetToDefaultPose();
    }
    
    public void animateAnteater(EntityAnteater anteater, float partialTick) {
        // Simple animation for the ant
        root.rotateAngleX = (float) Math.sin(anteater.tickCount * 0.2F) * 0.1F;
    }
    
    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(root);
    }
}
