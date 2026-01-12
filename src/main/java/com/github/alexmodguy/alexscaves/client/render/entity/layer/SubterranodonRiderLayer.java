package com.github.alexmodguy.alexscaves.client.render.entity.layer;

import com.github.alexmodguy.alexscaves.client.model.SubterranodonModel;
import com.github.alexmodguy.alexscaves.client.render.entity.SubterranodonRenderer;
import com.github.alexmodguy.alexscaves.client.render.entity.SubterranodonRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// TODO AC-TODO.md: Implement rider rendering with 1.21 render state architecture
// In 1.21, RenderLayer uses EntityRenderState instead of Entity, so rider data must be extracted
// to SubterranodonRenderState during extractRenderState() and rendered here without entity reference
public class SubterranodonRiderLayer extends RenderLayer<SubterranodonRenderState, SubterranodonModel> {

    public SubterranodonRiderLayer(SubterranodonRenderer render) {
        super(render);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, SubterranodonRenderState renderState, float p_225628_5_, float p_225628_6_) {
        // TODO: Implement rider rendering using data from renderState
        // Need to store passenger data in SubterranodonRenderState during extractRenderState()
        // For now, no rider rendering (mob still rideable, just riders won't visually show)
    }
}
