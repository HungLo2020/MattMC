package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class SolidFaceRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;

	public SolidFaceRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Copies sturdy block faces into Rust's explicit colored-quad stream. */
	public void collectRustSemantics(SubmitNodeStorage geometry, Camera camera) {
		if (camera == null || !camera.isInitialized() || minecraft.player == null) return;
		BlockGetter blockGetter = this.minecraft.player.level();
		Vec3 cameraPosition = camera.getPosition();
		PoseStack semanticPose = new PoseStack();
		semanticPose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
		BlockPos center = BlockPos.containing(cameraPosition.x, cameraPosition.y, cameraPosition.z);
		float[] uvs = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
		int[] colors = {0x80FFFFFF};
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -6, -6), center.offset(6, 6, 6))) {
			BlockState state = blockGetter.getBlockState(pos);
			if (state.is(Blocks.AIR)) continue;
			for (AABB box : state.getShape(blockGetter, pos).toAabbs()) {
				AABB world = box.move(pos).inflate(0.002);
				float x0 = (float)world.minX, y0 = (float)world.minY, z0 = (float)world.minZ;
				float x1 = (float)world.maxX, y1 = (float)world.maxY, z1 = (float)world.maxZ;
				for (Direction face : Direction.values()) {
					if (!state.isFaceSturdy(blockGetter, pos, face)) continue;
					float[] vertices = faceVertices(face, x0, y0, z0, x1, y1, z1);
					if (!geometry.submitColoredQuadsSemantic(semanticPose, RenderType.debugFilledBox(), vertices, uvs, colors, 15728880)) {
						throw new IllegalStateException("Rust whole-frame solid-face debug route rejected semantic face");
					}
				}
			}
		}
	}

	private static float[] faceVertices(Direction face, float x0, float y0, float z0, float x1, float y1, float z1) {
		return switch (face) {
			case WEST -> new float[] {x0,y0,z0, x0,y0,z1, x0,y1,z0, x0,y1,z1};
			case SOUTH -> new float[] {x0,y1,z1, x0,y0,z1, x1,y1,z1, x1,y0,z1};
			case EAST -> new float[] {x1,y0,z1, x1,y0,z0, x1,y1,z1, x1,y1,z0};
			case NORTH -> new float[] {x1,y1,z0, x1,y0,z0, x0,y1,z0, x0,y0,z0};
			case DOWN -> new float[] {x0,y0,z0, x1,y0,z0, x0,y0,z1, x1,y0,z1};
			case UP -> new float[] {x0,y1,z0, x0,y1,z1, x1,y1,z0, x1,y1,z1};
		};
	}
}
