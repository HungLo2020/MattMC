package net.minecraft.client.renderer.feature;

import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ShadowFeatureRenderer {
	private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));

	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		// Rust owns the whole Vulkan frame; do not let a direct Java feature
		// invocation become an invisible fallback draw.
		return;
	}

	private static void shadowVertex(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, float j, float k) {
		Vector3f vector3f = matrix4f.transformPosition(f, g, h, new Vector3f());
		vertexConsumer.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), i, j, k, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
	}
}
