package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelMudskipper;
import net.alexsmobs.client.render.state.MudskipperRenderState;
import net.alexsmobs.entity.EntityMudskipper;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderMudskipper extends MobRenderer<EntityMudskipper, MudskipperRenderState, ModelMudskipper> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/mudskipper.png");
    private static final ResourceLocation TEXTURE_SPIT = ResourceLocation.withDefaultNamespace("textures/entity/mudskipper_spit.png");

    public RenderMudskipper(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMudskipper(), 0.25F);
    }

    @Override
    public MudskipperRenderState createRenderState() {
        return new MudskipperRenderState();
    }

    @Override
    public void extractRenderState(EntityMudskipper entity, MudskipperRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.mouthOpen = entity.isMouthOpen();
        renderState.sitProgress = entity.sitProgress;
        renderState.swimProgress = entity.swimProgress;
        renderState.displayProgress = entity.displayProgress;
        renderState.mudProgress = entity.mudProgress;
        renderState.prevSitProgress = entity.prevSitProgress;
        renderState.prevSwimProgress = entity.prevSwimProgress;
        renderState.prevDisplayProgress = entity.prevDisplayProgress;
        renderState.prevMudProgress = entity.prevMudProgress;
    }

    protected void scale(MudskipperRenderState renderState, PoseStack matrixStackIn) {

    }

    public ResourceLocation getTextureLocation(MudskipperRenderState renderState) {
        return renderState.mouthOpen ? TEXTURE_SPIT : TEXTURE;
    }
}
