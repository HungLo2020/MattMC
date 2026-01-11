package net.minecraft.client.renderer.entity.subterranodon;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.subterranodon.SubterranodonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.SubterranodonRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.subterranodon.SubterranodonEntity;

/**
 * Renderer for Subterranodon entity.
 * Ported from AlexsCaves mod.
 */
@Environment(EnvType.CLIENT)
public class SubterranodonRenderer extends MobRenderer<SubterranodonEntity, SubterranodonRenderState, SubterranodonModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon/subterranodon.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon/subterranodon_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon/subterranodon_tectonic.png");
    
    public SubterranodonRenderer(EntityRendererProvider.Context context) {
        super(context, new SubterranodonModel(context.bakeLayer(ModelLayers.SUBTERRANODON)), 0.5F);
        // TODO: Add SubterranodonRiderLayer when rider positioning is fully implemented
        // this.addLayer(new SubterranodonRiderLayer(this));
    }
    
    @Override
    public ResourceLocation getTextureLocation(SubterranodonRenderState renderState) {
        // Use alternate skins if available
        if (renderState.altSkin == 1) {
            return TEXTURE_RETRO;
        } else if (renderState.altSkin == 2) {
            return TEXTURE_TECTONIC;
        }
        return TEXTURE;
    }
    
    @Override
    public SubterranodonRenderState createRenderState() {
        return new SubterranodonRenderState();
    }
    
    @Override
    public void extractRenderState(SubterranodonEntity entity, SubterranodonRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.isFlying = entity.isFlying();
        renderState.isHovering = entity.isHovering();
        renderState.flapAmount = entity.getFlyProgress(partialTick);
        renderState.altSkin = entity.getAltSkin();
    }
}
