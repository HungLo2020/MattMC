package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelAnteater;
import net.alexsmobs.client.render.layer.LayerAnteaterTongueItem;
import net.alexsmobs.entity.EntityAnteater;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderAnteater extends MobRenderer<EntityAnteater, AnteaterRenderState, ModelAnteater> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/anteater.png");
    private static final ResourceLocation TEXTURE_PETER = ResourceLocation.withDefaultNamespace("textures/entity/anteater_peter.png");

    public RenderAnteater(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAnteater(), 0.45F);
        this.addLayer(new LayerAnteaterTongueItem(this));
    }

    @Override
    public AnteaterRenderState createRenderState() {
        return new AnteaterRenderState();
    }

    @Override
    public void extractRenderState(EntityAnteater anteater, AnteaterRenderState renderState, float partialTick) {
        super.extractRenderState(anteater, renderState, partialTick);
        renderState.standProgress = anteater.standProgress;
        renderState.prevStandProgress = anteater.prevStandProgress;
        renderState.tongueProgress = anteater.tongueProgress;
        renderState.prevTongueProgress = anteater.prevTongueProgress;
        renderState.leaningProgress = anteater.leaningProgress;
        renderState.prevLeaningProgress = anteater.prevLeaningProgress;
        renderState.tickCount = anteater.tickCount;
        renderState.isBaby = anteater.isBaby();
        renderState.isPassenger = anteater.isPassenger();
        renderState.animationTick = anteater.getAnimationTick();
        renderState.currentAnimation = anteater.getAnimation();
        this.itemModelResolver.updateForLiving(renderState.tongueItem, anteater.getMainHandItem(),
            net.minecraft.world.item.ItemDisplayContext.GROUND, anteater);
    }

    public boolean shouldRender(EntityAnteater anteater, Frustum p_225626_2_, double p_225626_3_, double p_225626_5_, double p_225626_7_) {
        if(anteater.isBaby() && anteater.isPassenger() && anteater.getVehicle() instanceof EntityAnteater){
            return false;
        }
        return super.shouldRender(anteater, p_225626_2_, p_225626_3_, p_225626_5_, p_225626_7_);
    }

    public ResourceLocation getTextureLocation(AnteaterRenderState renderState) {
        // Note: We can't access isPeter() from render state, so we'll just use the default texture
        // If needed, we can add a boolean field to AnteaterRenderState
        return TEXTURE;
    }
}
