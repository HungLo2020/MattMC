package net.alexsmobs.client.model;

import net.citadel.client.model.AdvancedEntityModel;
import net.citadel.client.model.AdvancedModelBox;
import net.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.alexsmobs.client.render.TarantulaHawkRenderState;

public class ModelTarantulaHawkBaby extends AdvancedEntityModel<TarantulaHawkRenderState> {
    private final AdvancedModelBox root;
    private final AdvancedModelBox body;
    private final AdvancedModelBox head;

    public ModelTarantulaHawkBaby() {
        texWidth = 64;
        texHeight = 64;

        root = new AdvancedModelBox(this, "root");
        root.setPos(0.0F, 24.0F, 0.0F);


        body = new AdvancedModelBox(this, "body");
        body.setPos(0.0F, -3.0F, -7.0F);
        root.addChild(body);
        body.setTextureOffset(0, 0).addBox(-4.0F, -3.0F, 0.0F, 8.0F, 6.0F, 15.0F, 0.0F, false);

        head = new AdvancedModelBox(this, "head");
        head.setPos(0.0F, 0.9F, 0.0F);
        body.addChild(head);
        head.setTextureOffset(0, 22).addBox(-3.5F, -3.0F, -3.0F, 7.0F, 5.0F, 3.0F, 0.0F, false);
        this.updateDefaultPose();
    }

    @Override
    public void setupAnim(TarantulaHawkRenderState renderState) {
        this.resetToDefaultPose();
        float walkSpeed = 1;
        float walkDegree = 0.75F;
        float limbSwing = renderState.walkAnimationPos;
        float limbSwingAmount = renderState.walkAnimationSpeed;
        float ageInTicks = renderState.ageInTicks;
        float stretch = (float) (Math.sin(limbSwing * 0.25F) * limbSwingAmount) + limbSwingAmount;
        body.setScale(1, (1 - stretch * 0.05F), (1 + stretch * 0.5F));
        body.rotationPointZ -= stretch * 4;
        this.walk(head, 0.25F, 0.075F, false, -1, 0F, ageInTicks, 1);
        this.walk(head, walkSpeed, walkDegree * 0.1F, false, -1, 0F, limbSwing, limbSwingAmount);

    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(root, body, head);
    }

    public void setRotationAngle(AdvancedModelBox advancedModelBox, float x, float y, float z) {
        advancedModelBox.rotateAngleX = x;
        advancedModelBox.rotateAngleY = y;
        advancedModelBox.rotateAngleZ = z;
    }
}
