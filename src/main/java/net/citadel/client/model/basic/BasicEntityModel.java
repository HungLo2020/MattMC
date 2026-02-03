package net.citadel.client.model.basic;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public abstract class BasicEntityModel<T extends EntityRenderState> extends EntityModel<T> {
    public int textureWidth = 64;
    public int textureHeight = 32;
    private final RenderingProxyModelPart proxyRoot;

    protected BasicEntityModel() {
        this(RenderType::entityCutoutNoCull);
    }

    protected BasicEntityModel(Function<ResourceLocation, RenderType> renderType) {
        // Create a special ModelPart that will proxy rendering to our BasicModelPart parts
        super(new RenderingProxyModelPart(), renderType);
        this.proxyRoot = (RenderingProxyModelPart) root();
        this.proxyRoot.setOwner(this);
    }

    public abstract Iterable<BasicModelPart> parts();

    @Override
    public abstract void setupAnim(T renderState);
    
    // Special ModelPart that proxies rendering to BasicModelPart children
    public static class RenderingProxyModelPart extends ModelPart {
        private BasicEntityModel<?> owner;
        
        public RenderingProxyModelPart() {
            super(java.util.Collections.emptyList(), java.util.Collections.emptyMap());
        }
        
        public void setOwner(BasicEntityModel<?> owner) {
            this.owner = owner;
        }
        
        @Override
        public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
            if (owner != null) {
                // Render all BasicModelPart children instead of vanilla ModelPart children
                // Pass the color parameter to ensure proper texture rendering
                for (BasicModelPart part : owner.parts()) {
                    part.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
                }
            }
        }
    }
}