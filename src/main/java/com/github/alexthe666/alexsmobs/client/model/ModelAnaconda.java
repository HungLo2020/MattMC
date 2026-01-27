package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.alexsmobs.client.render.AnacondaRenderState;
import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import com.github.alexthe666.alexsmobs.entity.util.Maths;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.Mth;

public class ModelAnaconda extends AdvancedEntityModel<AnacondaRenderState> {
    private final AdvancedModelBox root;
    private final AdvancedModelBox part;
    private AdvancedModelBox jaw;
    private final AnacondaPartIndex partIndex;

    public ModelAnaconda(AnacondaPartIndex index) {
        this.partIndex = index;
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
    public void setupAnim(AnacondaRenderState renderState) {
        this.resetToDefaultPose();
        float idleSpeed = 0.05F;
        float strangle = renderState.strangleProgress;
        
        if(jaw != null && partIndex == AnacondaPartIndex.HEAD){ // head
            progressPositionPrev(part, strangle, 0, 4, 0, 5F);
            progressPositionPrev(jaw, strangle, 0, 0, 1F, 5F);
            progressRotationPrev(part, strangle, Maths.rad(10), 0, 0, 5F);
            progressRotationPrev(jaw, strangle, Maths.rad(160), 0, 0, 5F);
            this.part.rotateAngleY += renderState.yRot / 57.295776F;
            this.part.rotateAngleX += Math.min(0, renderState.xRot / 57.295776F);
            this.part.rotationPointX += Mth.sin(renderState.walkAnimationPos) * 2.0F * renderState.walkAnimationSpeed;
            this.walk(part, 0.7F, 0.2F, false, 1F, 0.05F, renderState.ageInTicks, strangle * 0.2F);
            this.walk(jaw, 0.7F, 0.4F, true, 1F, -0.05F, renderState.ageInTicks, strangle * 0.2F);
        } else { // body parts
            // Body part animation - scale based on swellLerp if needed
            float f = 1.01F;
            float swell = 0.0F; // We don't have swellLerp in render state yet, could add if needed
            part.setScale(f + swell, f + swell, f);
        }
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return jaw == null ? ImmutableList.of(root, part) : ImmutableList.of(root, part, jaw);
    }
}