package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelEmu;
import net.matt.quantize.entities.mobs.EntityEmu;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderEmu extends MobRenderer<EntityEmu, ModelEmu> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/emu/emu.png");
    private static final ResourceLocation TEXTURE_BABY = new ResourceIdentifier("textures/model/entity/emu/emu_baby.png");
    private static final ResourceLocation TEXTURE_BLONDE = new ResourceIdentifier("textures/model/entity/emu/emu_blonde.png");
    private static final ResourceLocation TEXTURE_BLONDE_BABY = new ResourceIdentifier("textures/model/entity/emu/emu_baby_blonde.png");
    private static final ResourceLocation TEXTURE_BLUE = new ResourceIdentifier("textures/model/entity/emu/emu_blue.png");

    public RenderEmu(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelEmu(), 0.45F);
    }

    protected void scale(EntityEmu entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.85F, 0.85F, 0.85F);
    }


    public ResourceLocation getTextureLocation(EntityEmu entity) {
        if(entity.getVariant() == 2){
            return entity.isBaby() ? TEXTURE_BLONDE_BABY : TEXTURE_BLONDE;
        }
        if(entity.getVariant() == 1 && !entity.isBaby()){
            return  TEXTURE_BLUE;
        }
        return entity.isBaby() ? TEXTURE_BABY : TEXTURE;
    }
}
