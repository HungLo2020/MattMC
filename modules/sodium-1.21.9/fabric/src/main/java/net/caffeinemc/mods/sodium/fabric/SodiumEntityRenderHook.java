package net.caffeinemc.mods.sodium.fabric;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.hooks.EntityRenderHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.sodium.api.math.MatrixHelper;
import net.sodium.api.util.ColorABGR;
import net.sodium.api.util.NormI8;
import net.sodium.api.vertex.buffer.VertexBufferWriter;
import net.sodium.api.vertex.format.common.EntityVertex;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/**
 * Sodium implementation of EntityRenderHooks.
 * Provides optimized entity shadow rendering using direct vertex writing.
 */
public class SodiumEntityRenderHook implements EntityRenderHooks {
    private static final int DEFAULT_NORMAL = NormI8.pack(0.0f, 1.0f, 0.0f);
    private static final int SHADOW_COLOR = ColorABGR.pack(1.0f, 1.0f, 1.0f);
    private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    
    @Override
    public boolean onRenderEntityShadows(SubmitNodeCollection submitNodeCollection,
                                        MultiBufferSource.BufferSource bufferSource) {
        VertexConsumer vertices = bufferSource.getBuffer(SHADOW_RENDER_TYPE);
        var writer = VertexConsumerUtils.convertOrLog(vertices);
        
        if (writer == null) {
            return false; // Fall back to vanilla if conversion fails
        }
        
        // Render all shadow submits using optimized vertex writing
        for (SubmitNodeStorage.ShadowSubmit shadows : submitNodeCollection.getShadowSubmits()) {
            Matrix4f matrices = shadows.pose();
            
            for (int i = 0; i < shadows.pieces().size(); i++) {
                EntityRenderState.ShadowPiece shadowPiece = shadows.pieces().get(i);
                float alpha = shadowPiece.alpha();
                
                if (alpha >= 0.0F) {
                    if (alpha > 1.0F) {
                        alpha = 1.0F;
                    }
                    
                    AABB box = shadowPiece.shapeBelow().bounds();
                    float minX = (float) (shadowPiece.relativeX() + box.minX);
                    float maxX = (float) (shadowPiece.relativeX() + box.maxX);
                    float minY = (float) (shadowPiece.relativeY() + box.minY);
                    float minZ = (float) (shadowPiece.relativeZ() + box.minZ);
                    float maxZ = (float) (shadowPiece.relativeZ() + box.maxZ);
                    
                    renderShadowPart(matrices, writer, shadows.radius(), alpha, minX, maxX, minY, minZ, maxZ);
                }
            }
        }
        
        return true; // Cancel vanilla rendering
    }
    
    private static void renderShadowPart(Matrix4f matPosition, VertexBufferWriter writer, 
                                        float radius, float alpha, float minX, float maxX, 
                                        float minY, float minZ, float maxZ) {
        float size = 0.5F * (1.0F / radius);
        float u1 = (-minX * size) + 0.5F;
        float u2 = (-maxX * size) + 0.5F;
        float v1 = (-minZ * size) + 0.5F;
        float v2 = (-maxZ * size) + 0.5F;
        
        int color = ColorABGR.withAlpha(SHADOW_COLOR, alpha);
        int normal = DEFAULT_NORMAL;
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * EntityVertex.STRIDE);
            long ptr = buffer;
            
            writeShadowVertex(ptr, matPosition, minX, minY, minZ, u1, v1, color, normal);
            ptr += EntityVertex.STRIDE;
            
            writeShadowVertex(ptr, matPosition, minX, minY, maxZ, u1, v2, color, normal);
            ptr += EntityVertex.STRIDE;
            
            writeShadowVertex(ptr, matPosition, maxX, minY, maxZ, u2, v2, color, normal);
            ptr += EntityVertex.STRIDE;
            
            writeShadowVertex(ptr, matPosition, maxX, minY, minZ, u2, v1, color, normal);
            ptr += EntityVertex.STRIDE;
            
            writer.push(stack, buffer, 4, EntityVertex.FORMAT);
        }
    }
    
    private static void writeShadowVertex(long ptr, Matrix4f matPosition, float x, float y, float z,
                                         float u, float v, int color, int normal) {
        float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
        float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
        float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);
        
        EntityVertex.write(ptr, xt, yt, zt, color, u, v, LightTexture.FULL_BRIGHT, 
                          OverlayTexture.NO_OVERLAY, normal);
    }
}
