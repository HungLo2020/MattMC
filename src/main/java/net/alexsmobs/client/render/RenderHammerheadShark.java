package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelHammerheadShark;
import net.alexsmobs.entity.EntityHammerheadShark;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderHammerheadShark extends MobRenderer<EntityHammerheadShark, HammerheadSharkRenderState, ModelHammerheadShark> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/hammerhead_shark.png");

    public RenderHammerheadShark(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelHammerheadShark(), 0.8F);
    }

    @Override
    public HammerheadSharkRenderState createRenderState() {
        return new HammerheadSharkRenderState();
    }

    @Override
    public void extractRenderState(EntityHammerheadShark entity, HammerheadSharkRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    public ResourceLocation getTextureLocation(HammerheadSharkRenderState state) {
        return TEXTURE;
    }
}
