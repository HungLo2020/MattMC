package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

@Environment(EnvType.CLIENT)
public class EntityBlockIntersectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float PADDING = 0.02F;

	/** Copies subscribed entity/block intersections into Rust-owned semantic boxes. */
	public void collectRustSemantics(Minecraft minecraft, Camera camera, SubmitNodeStorage geometry) {
		DebugValueAccess access = minecraft.getConnection().createDebugValueAccess();
		PoseStack transform = new PoseStack();
		transform.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		float[] uvs = {0,0,1,0,1,1,0,1};
		access.forEachBlock(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS, (pos, intersection) -> {
			float x0 = pos.getX() - PADDING, y0 = pos.getY() - PADDING, z0 = pos.getZ() - PADDING;
			float x1 = pos.getX() + 1.0F + PADDING, y1 = pos.getY() + 1.0F + PADDING, z1 = pos.getZ() + 1.0F + PADDING;
			float[][] faces = {{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}};
			for (float[] face : faces) if (!geometry.submitColoredQuadsSemantic(transform, RenderType.debugFilledBox(), face, uvs, new int[]{intersection.color()}, 15728880)) {
				throw new IllegalStateException("Rust whole-frame entity/block intersection route rejected semantic box");
			}
		});
	}
}
