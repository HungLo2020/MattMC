package net.matt.quantize.entities.models;

import net.matt.quantize.entities.mobs.TephraEntity;
import net.matt.quantize.modules.entities.AdvancedEntityModel;
import net.matt.quantize.modules.entities.AdvancedModelBox;
import net.matt.quantize.modules.entities.BasicModelPart;
import com.google.common.collect.ImmutableList;

public class TephraModel extends AdvancedEntityModel<TephraEntity> {
    private final AdvancedModelBox main;

    public TephraModel() {
        texWidth = 64;
        texHeight = 64;

        main = new AdvancedModelBox(this);
        main.setRotationPoint(0.0F, 0.0F, 0.0F);
        main.setTextureOffset(0, 0).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, 0.0F, false);
        this.updateDefaultPose();
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(main);
    }

    @Override
    public void setupAnim(TephraEntity tephraEntity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.resetToDefaultPose();
        main.rotateAngleZ += ageInTicks * 0.2F;
        main.rotateAngleX += ageInTicks * 0.4F;
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(main);
    }

}
