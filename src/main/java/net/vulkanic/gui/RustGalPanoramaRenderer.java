package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.CubeMap;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;

/**
 * Semantic main-menu panorama producer. Java supplies only the copied six-face
 * image and camera parameters; the existing Rust GUI-mesh frontend owns every
 * texture, offscreen target, raster draw, composition, and presentation step.
 */
public final class RustGalPanoramaRenderer {
	private static final int GRID = 64;
	private static final int NORMAL_POSITIVE_Z = 0x007F0000;
	private static final float FOV_RADIANS = (float)Math.toRadians(85.0);

	private RustGalPanoramaRenderer() {
	}

	public static boolean enqueue(CubeMap cubeMap, float pitchDegrees, float yawDegrees, int guiWidth, int guiHeight) {
		if (!RustGalGuiRenderer.isWholeFrameVulkanEnabled()
			|| guiWidth <= 0 || guiHeight <= 0
			|| guiWidth > Integer.MAX_VALUE - 2 || guiHeight > Integer.MAX_VALUE - 2
			|| !Float.isFinite(pitchDegrees) || !Float.isFinite(yawDegrees)) {
			// Returning false is intentional: CubeMap.render converts an unavailable
			// semantic panorama into a hard failure on the Rust route, never a Java
			// texture/presenter fallback.
			return false;
		}
		RustGalGuiRawImageAssets.Asset image = RustGalGuiRawImageAssets.resolveCubeMap(cubeMap.semanticTextureLocation());
		if (image == null) return false;
		int guard = 1;
		int renderWidth = guiWidth + guard * 2;
		int renderHeight = guiHeight + guard * 2;
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>((GRID + 1) * (GRID + 1));
		List<Integer> indices = new ArrayList<>(GRID * GRID * 6);
		Matrix4f modelView = new Matrix4f().rotationX((float)Math.PI)
			.rotateX((float)Math.toRadians(pitchDegrees)).rotateY((float)Math.toRadians(yawDegrees));
		float cot = 1.0F / (float)Math.tan(FOV_RADIANS * 0.5F);
		float projectionX = cot / ((float)guiWidth / guiHeight);
		for (int y = 0; y <= GRID; y++) {
			for (int x = 0; x <= GRID; x++) {
				float sx = (float)x / GRID;
				float sy = (float)y / GRID;
				float[] uv = cubeUv(modelView, sx * 2.0F - 1.0F, 1.0F - sy * 2.0F, projectionX, cot);
				vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(
					new float[] {guard + sx * guiWidth, guard + sy * guiHeight, 0.0F}, uv, uv, 0xFFFFFFFF, NORMAL_POSITIVE_Z
				));
			}
		}
		for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
			int a = y * (GRID + 1) + x;
			int b = a + 1;
			int c = a + GRID + 2;
			int d = a + GRID + 1;
			indices.add(a); indices.add(b); indices.add(c);
			indices.add(c); indices.add(d); indices.add(a);
		}
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			GuiRenderStratum.GUI_PANORAMA.order(), 0, 1, 1, image.assetId(), 0L, 0.0F,
			identity(), new float[] {1, 0, 0, 1, 0, 0}, 0, 0, guiWidth, guiHeight,
			guiWidth, guiHeight, renderWidth, renderHeight, guard, vertices, indices
		);
		RustGalFrameCoordinator.enqueueGuiMeshItemRequest(List.of(batch), GuiRenderStratum.GUI_PANORAMA, System.nanoTime());
		// Stage only after the bounded mesh request is accepted. This keeps a
		// rejected panorama request from retaining a Rust-owned cube-map asset.
		RustGalGuiRawImageAssets.stageCubeMap(image);
		return true;
	}

	static float[] cubeUv(Matrix4f matrix, float clipX, float clipY, float projectionX, float projectionY) {
		float x = clipX / projectionX, y = clipY / projectionY, z = -1.0F;
		// JOML stores matrices column-major; transform the clip-space ray by
		// columns (m00,m10,m20), (m01,m11,m21), (m02,m12,m22). The previous
		// row-indexed multiplication mirrored rotations and selected the wrong
		// cube-map face for non-zero panorama yaw/pitch.
		float dx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z;
		float dy = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z;
		float dz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z;
		float ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
		float u, v, face;
		if (ax >= ay && ax >= az) {
			float s = 0.5F / ax;
			face = dx > 0.0F ? 0.0F : 1.0F;
			u = (dx > 0.0F ? -dz : dz) * s + 0.5F; v = -dy * s + 0.5F;
		} else if (ay >= az) {
			float s = 0.5F / ay;
			face = dy > 0.0F ? 2.0F : 3.0F;
			u = dx * s + 0.5F; v = (dy > 0.0F ? dz : -dz) * s + 0.5F;
		} else {
			float s = 0.5F / az;
			face = dz > 0.0F ? 4.0F : 5.0F;
			u = (dz > 0.0F ? dx : -dx) * s + 0.5F; v = -dy * s + 0.5F;
		}
		return new float[] {Math.clamp(u, 0.0F, 1.0F), (face + Math.clamp(v, 0.0F, 1.0F)) / 6.0F};
	}

	private static float[] identity() { return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
}
