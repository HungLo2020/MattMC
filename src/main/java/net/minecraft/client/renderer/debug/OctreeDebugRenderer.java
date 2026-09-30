package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.mutable.MutableInt;

@Environment(EnvType.CLIENT)
public class OctreeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;

	public OctreeDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Traverses the same frustum-filtered octree and emits Rust semantic edges/text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text, Frustum frustum) {
		if (frustum == null) return;
		MutableInt sequence = new MutableInt(0);
		this.minecraft.levelRenderer.getSectionOcclusionGraph().getOctree().visitNodes(
			(node, visible, depth, inside) -> this.collectNode(node, camera, geometry, text, depth, visible, sequence, inside), frustum, 32);
	}

	private void collectNode(Octree.Node node, Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text,
		int depth, boolean visible, MutableInt sequence, boolean inside) {
		AABB box = node.getAABB();
		long size = Math.round(box.getXsize() / 16.0);
		if (size == 1L) {
			sequence.increment();
			PoseStack label = new PoseStack();
			label.translate(box.getCenter().x - camera.getPosition().x, box.getCenter().y - camera.getPosition().y, box.getCenter().z - camera.getPosition().z);
			label.mulPose(camera.rotation()); label.scale(0.3F, -0.3F, 0.3F);
			text.submitTextSemantic(0, label, 0, 0, Component.literal(String.valueOf(sequence.getValue())).getVisualOrderText(), true,
				Font.DisplayMode.SEE_THROUGH, inside ? -16711936 : -1, -1, 0, 0);
		}
		AABB trimmed = box.deflate(0.1 * depth);
		float r = getColorComponent(size + 5L, 0.3F), g = getColorComponent(size + 5L, 0.8F), b = getColorComponent(size + 5L, 0.5F);
		int color = ((visible ? 0x66 : 0xFF) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
		float x0 = (float)(trimmed.minX - camera.getPosition().x), y0 = (float)(trimmed.minY - camera.getPosition().y), z0 = (float)(trimmed.minZ - camera.getPosition().z);
		float x1 = (float)(trimmed.maxX - camera.getPosition().x), y1 = (float)(trimmed.maxY - camera.getPosition().y), z1 = (float)(trimmed.maxZ - camera.getPosition().z);
		float[] edges = {x0,y0,z0,x1,y0,z0, x1,y0,z0,x1,y1,z0, x1,y1,z0,x0,y1,z0, x0,y1,z0,x0,y0,z0,
			x0,y0,z1,x1,y0,z1, x1,y0,z1,x1,y1,z1, x1,y1,z1,x0,y1,z1, x0,y1,z1,x0,y0,z1,
			x0,y0,z0,x0,y0,z1, x1,y0,z0,x1,y0,z1, x1,y1,z0,x1,y1,z1, x0,y1,z0,x0,y1,z1};
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(new org.joml.Matrix4f(), edges, color, 1.0F)) {
			throw new IllegalStateException("Rust whole-frame octree route rejected semantic AABB edges");
		}
	}

	private static float getColorComponent(long l, float f) {
		float g = 0.1F;
		return Mth.frac(f * (float)l) * 0.9F + 0.1F;
	}
}
