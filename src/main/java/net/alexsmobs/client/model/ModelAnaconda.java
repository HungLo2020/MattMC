package net.alexsmobs.client.model;

import net.alexsmobs.client.render.AnacondaPartRenderState;
import net.alexsmobs.client.render.AnacondaRenderState;
import net.alexsmobs.entity.util.AnacondaPartIndex;
import net.alexsmobs.entity.util.Maths;
import net.citadel.client.model.AdvancedEntityModel;
import net.citadel.client.model.AdvancedModelBox;
import net.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public class ModelAnaconda extends AdvancedEntityModel<EntityRenderState> {
    private final AdvancedModelBox root;
    private final AdvancedModelBox part;
    private AdvancedModelBox jaw;

    public ModelAnaconda(AnacondaPartIndex index) {
        texWidth = 128;
        texHeight = 128;
        part = new AdvancedModelBox(this, "part");
        root = new AdvancedModelBox(this, "root");
        root.setRotationPoint(0.0F, 21.0F, 0);
        switch (index) {
            case HEAD -> {
                part.setRotationPoint(0.0F, 0, 0);
                part.setTextureOffset(62, 32).addBox(-3.5F, -3.0F, -9.0F, 7.0F, 3.0F, 10.0F, 0.0F, false);
                part.setTextureOffset(67, 0).addBox(-3.5F, -1.0F, -9.0F, 7.0F, 0.0F, 10.0F, 0.0F, false);
                jaw = new AdvancedModelBox(this, "        jaw");
                jaw.setRotationPoint(0, 0, 0);
                jaw.setTextureOffset(52, 55).addBox(-3.5F, -1.0F, -9, 7.0F, 4.0F, 10.0F, 0.0F, false);
                jaw.setTextureOffset(66, 11).addBox(-3.5F, 0.0F, -9, 7.0F, 0.0F, 10.0F, 0.0F, false);
                part.addChild(jaw);
            }
            case NECK -> {
                part.setRotationPoint(0.0F, 0, 0.0F);
                part.setTextureOffset(33, 32).addBox(-3.0F, -3.0F, -8, 6.0F, 6.0F, 16.0F, 0.0F, false);
            }
            case BODY -> {
                part.setRotationPoint(0.0F, 0, -8.0F);
                part.setTextureOffset(33, 8).addBox(-4.0F, -4.0F, 0, 8.0F, 7.0F, 16.0F, 0.0F, false);
            }
            case TAIL -> {
                part.setRotationPoint(0.0F, 0, -7.0F);
                part.setTextureOffset(29, 55).addBox(-1.5F, -2.0F, 0, 3.0F, 4.0F, 16.0F, 0.0F, false);
            }
        }
        root.addChild(part);

        this.updateDefaultPose();
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

    @Override
    public void setupAnim(EntityRenderState renderState) {
        this.resetToDefaultPose();
        
        // Get base animation values from EntityRenderState
        float ageInTicks = renderState.ageInTicks;
        
        // For LivingEntityRenderState, we have these fields
        float limbSwing = 0;
        float limbSwingAmount = 0;
        float netHeadYaw = 0;
        float headPitch = 0;
        
        if (renderState instanceof AnacondaRenderState anacondaState) {
            // Use LivingEntityRenderState fields for anaconda head
            limbSwing = anacondaState.walkAnimationPos;
            limbSwingAmount = anacondaState.walkAnimationSpeed;
            netHeadYaw = anacondaState.yRot;
            headPitch = anacondaState.xRot;
            
            float strangle = anacondaState.strangleProgress;
            progressPositionPrev(part, strangle, 0, 4, 0, 5F);
            progressPositionPrev(jaw, strangle, 0, 0, 1F, 5F);
            progressRotationPrev(part, strangle, Maths.rad(10), 0, 0, 5F);
            progressRotationPrev(jaw, strangle, Maths.rad(160), 0, 0, 5F);
            this.part.rotateAngleY += netHeadYaw / 57.295776F;
            this.part.rotateAngleX += Math.min(0, headPitch / 57.295776F);
            this.part.rotationPointX += Mth.sin(limbSwing) * 2.0F * limbSwingAmount;
            this.walk(part, 0.7F, 0.2F, false, 1F, 0.05F, ageInTicks, strangle * 0.2F);
            this.walk(jaw, 0.7F, 0.4F, true, 1F, -0.05F, ageInTicks, strangle * 0.2F);
        } else if (renderState instanceof AnacondaPartRenderState partState) {
            // body part
            float f = 1.01F;
            if (partState.bodyIndex % 2 == 1) {
                f = 1.0F;
            }
            float swell = partState.swell * 0.15F;
            part.setScale(f + swell, f + swell, f);
        }
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return jaw == null ? ImmutableList.of(root, part) : ImmutableList.of(root, part, jaw);
    }
}