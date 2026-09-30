package net.sodium.fabric;

import net.blaze3d.vertex.VertexConsumer;
import net.sodium.client.render.vertex.VertexConsumerUtils;
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
		throw new IllegalStateException("Java Sodium entity-shadow hook is unavailable while Rust owns whole-frame presentation");
         // Cancel vanilla rendering
    }
    
    
}
