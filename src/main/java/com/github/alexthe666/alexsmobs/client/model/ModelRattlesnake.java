package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.alexsmobs.client.render.RattlesnakeRenderState;
import com.github.alexthe666.alexsmobs.entity.util.Maths;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;

public class ModelRattlesnake extends AdvancedEntityModel<RattlesnakeRenderState> {
    private final AdvancedModelBox body;
    private final AdvancedModelBox tail1;
    private final AdvancedModelBox tail2;
    private final AdvancedModelBox neck1;
    private final AdvancedModelBox neck2;
    private final AdvancedModelBox head;
    private final AdvancedModelBox tongue;

    public ModelRattlesnake() {
        texWidth = 64;
        texHeight = 64;

        body = new AdvancedModelBox(this, "body");
        body.setPos(0.0F, 24.0F, 0.0F);
        body.setTextureOffset(0, 0).addBox(-2.0F, -3.0F, -4.0F, 4.0F, 3.0F, 7.0F, 0.0F, false);

        tail1 = new AdvancedModelBox(this, "tail1");
        tail1.setPos(0.0F, -1.75F, 2.95F);
        body.addChild(tail1);
        tail1.setTextureOffset(0, 11).addBox(-1.5F, -1.25F, 0.05F, 3.0F, 3.0F, 7.0F, 0.0F, false);

        tail2 = new AdvancedModelBox(this, "tail2");
        tail2.setPos(0.0F, 0.45F, 7.05F);
        tail1.addChild(tail2);
        tail2.setTextureOffset(15, 16).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F, 0.0F, false);

        neck1 = new AdvancedModelBox(this, "neck1");
        neck1.setPos(0.0F, -1.5F, -4.0F);
        body.addChild(neck1);
        neck1.setTextureOffset(18, 6).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 5.0F, 0.0F, false);

        neck2 = new AdvancedModelBox(this, "neck2");
        neck2.setPos(0.0F, 0.0F, -4.9F);
        neck1.addChild(neck2);
        neck2.setTextureOffset(12, 25).addBox(-1.0F, -1.5F, -5.1F, 2.0F, 3.0F, 5.0F, 0.0F, false);

        head = new AdvancedModelBox(this, "head");
        head.setPos(0.0F, 0.0F, -5.0F);
        neck2.addChild(head);
        head.setTextureOffset(0, 22).addBox(-2.0F, -1.0F, -3.8F, 4.0F, 2.0F, 4.0F, 0.0F, false);

        tongue = new AdvancedModelBox(this, "tongue");
        tongue.setPos(0.0F, 0.0F, -3.8F);
        head.addChild(tongue);
        tongue.setTextureOffset(0, 0).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, 0.0F, false);
        this.updateDefaultPose();
    }

    @Override
    public void setupAnim(RattlesnakeRenderState state) {
        this.resetToDefaultPose();
        float walkSpeed = 1.0F;
        float walkDegree = 0.4F;
        AdvancedModelBox[] bodyParts = new AdvancedModelBox[]{neck1, neck2, body, tail1, tail2};
        float curlProgress = state.prevCurlProgress + (state.curlProgress - state.prevCurlProgress) * state.ageInTicks;
        
        // Handle baby scaling
        if (state.isBaby) {
            float f = 1.75F;
            head.setScale(f, f, f);
            head.setShouldScaleChildren(true);
        } else {
            head.setScale(1, 1, 1);
            head.setShouldScaleChildren(false);
        }
        
        progressPositionPrev(body, curlProgress, 0, 0, 3, 5F);
        progressRotationPrev(body, curlProgress, 0, Maths.rad(-90), 0, 5F);
        progressRotationPrev(tail1, curlProgress, Maths.rad(-10), Maths.rad(-70), 0, 5F);
        progressRotationPrev(neck1, curlProgress, Maths.rad(-20), Maths.rad(60), 0, 5F);
        progressRotationPrev(neck2, curlProgress, Maths.rad(-20), Maths.rad(60), 0, 5F);
        progressRotationPrev(head, curlProgress, Maths.rad(20), Maths.rad(-30), Maths.rad(10), 5F);
        if (state.randomToungeTick > 0) {
            tongue.showModel = true;
        }else{
			tongue.showModel = false;
		}
        this.walk(tongue, 1, 0.5F, false, 1F, 0f, state.ageInTicks, 1);
        if (state.isRattling) {
            progressRotationPrev(tail2, curlProgress, Maths.rad(70), Maths.rad(-60), 0, 5F);
            this.walk(tail2, 18, 0.1F, false, 1F, 0.2F, state.ageInTicks, 1);
            this.swing(tail2, 18, 0.1F, false, 0f, 0f, state.ageInTicks, 1);
        } else {
            progressRotationPrev(tail2, curlProgress, Maths.rad(10), Maths.rad(-90), 0, 5F);
        }
        this.faceTarget(state.yRot, state.xRot, 2, neck2, head);
        this.chainSwing(bodyParts, walkSpeed, walkDegree, -5, state.walkAnimationPos, state.walkAnimationSpeed);
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(body);
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(body, tail1, tail2, neck1, neck2, head, tongue);
    }

    public void setRotationAngle(AdvancedModelBox AdvancedModelBox, float x, float y, float z) {
        AdvancedModelBox.rotateAngleX = x;
        AdvancedModelBox.rotateAngleY = y;
        AdvancedModelBox.rotateAngleZ = z;
    }
}