package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelEmu;
import net.alexsmobs.entity.EntityEmu;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderEmu extends MobRenderer<EntityEmu, EmuRenderState, ModelEmu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/emu.png");
    private static final ResourceLocation TEXTURE_BABY = ResourceLocation.withDefaultNamespace("textures/entity/emu_baby.png");
    private static final ResourceLocation TEXTURE_BLONDE = ResourceLocation.withDefaultNamespace("textures/entity/emu_blonde.png");
    private static final ResourceLocation TEXTURE_BLONDE_BABY = ResourceLocation.withDefaultNamespace("textures/entity/emu_baby_blonde.png");
    private static final ResourceLocation TEXTURE_BLUE = ResourceLocation.withDefaultNamespace("textures/entity/emu_blue.png");

    public RenderEmu(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelEmu(), 0.45F);
    }

    @Override
    public EmuRenderState createRenderState() {
        return new EmuRenderState();
    }

    @Override
    public void extractRenderState(EntityEmu entity, EmuRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.getVariant();
        state.animationTick = entity.getAnimationTick();
        state.currentAnimation = entity.getAnimation();
    }

    @Override
    protected void scale(EmuRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.85F, 0.85F, 0.85F);
    }

    @Override
    public ResourceLocation getTextureLocation(EmuRenderState state) {
        if(state.variant == 2){
            return state.isBaby ? TEXTURE_BLONDE_BABY : TEXTURE_BLONDE;
        }
        if(state.variant == 1 && !state.isBaby){
            return  TEXTURE_BLUE;
        }
        return state.isBaby ? TEXTURE_BABY : TEXTURE;
    }
}
