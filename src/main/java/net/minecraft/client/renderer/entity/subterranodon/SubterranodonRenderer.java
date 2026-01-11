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
 * 
 * NOTE: This uses a simplified SubterranodonModel.
 * TODO: Port the full SubterranodonModel.java from AlexsCaves mod which includes:
 *  - Custom bone structure
 *  - Wing animations
 *  - Flying/hovering animations
 *  - Tail movements
 *  - Rider positioning
 *  - Baby scaling
 * 
 * The original AlexsCaves SubterranodonModel is 18,480 characters of complex animation code.
 */
@Environment(EnvType.CLIENT)
public class SubterranodonRenderer extends MobRenderer<SubterranodonEntity, SubterranodonRenderState, SubterranodonModel> {
    private static final ResourceLocation SUBTERRANODON_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon/subterranodon.png");
    
    public SubterranodonRenderer(EntityRendererProvider.Context context) {
        super(context, new SubterranodonModel(context.bakeLayer(ModelLayers.SUBTERRANODON)), 0.5F);
    }
    
    @Override
    public ResourceLocation getTextureLocation(SubterranodonRenderState renderState) {
        return SUBTERRANODON_LOCATION;
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
        // TODO: Extract additional animation state when proper model is implemented
    }
}
