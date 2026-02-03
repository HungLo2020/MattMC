package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelTerrapin;
import net.alexsmobs.client.render.state.TerrapinRenderState;
import net.alexsmobs.entity.EntityTerrapin;
import net.alexsmobs.entity.util.TerrapinTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderTerrapin extends MobRenderer<EntityTerrapin, TerrapinRenderState, ModelTerrapin> {

    public RenderTerrapin(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelTerrapin(), 0.3F);
    }

    @Override
    public TerrapinRenderState createRenderState() {
        return new TerrapinRenderState();
    }

    @Override
    public void extractRenderState(EntityTerrapin entity, TerrapinRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.turtleType = entity.getTurtleType();
        renderState.shellType = entity.getShellType();
        renderState.skinType = entity.getSkinType();
        renderState.turtleColor = entity.getTurtleColor();
        renderState.shellColor = entity.getShellColor();
        renderState.skinColor = entity.getSkinColor();
        renderState.isKoopa = entity.isKoopa();
        renderState.clientSpin = entity.clientSpin;
        renderState.spinCounter = entity.spinCounter;
        renderState.prevSwimProgress = entity.prevSwimProgress;
        renderState.swimProgress = entity.swimProgress;
        renderState.prevRetreatProgress = entity.prevRetreatProgress;
        renderState.retreatProgress = entity.retreatProgress;
        renderState.prevSpinProgress = entity.prevSpinProgress;
        renderState.spinProgress = entity.spinProgress;
        renderState.isSpinning = entity.isSpinning();
        renderState.hasRetreated = entity.hasRetreated();
        renderState.partialTick = partialTick;
    }

    public ResourceLocation getTextureLocation(TerrapinRenderState renderState) {
        if(renderState.isKoopa){
            return TerrapinTypes.KOOPA.getTexture();
        }
        return renderState.turtleType.getTexture();
    }
}
