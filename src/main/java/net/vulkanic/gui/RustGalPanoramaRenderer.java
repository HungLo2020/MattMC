package net.vulkanic.gui;

import java.util.List;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.CubeMap;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;

/**
 * Semantic main-menu panorama producer. Java supplies only the copied six-face
 * image and camera parameters; the existing Rust GUI-mesh frontend owns every
 * texture, offscreen target, raster draw, composition, and presentation step.
 */
public final class RustGalPanoramaRenderer {
	private static final int NORMAL_POSITIVE_Z = 0x007F0000;
	private static final float FOV_RADIANS = (float)Math.toRadians(85.0);

	private RustGalPanoramaRenderer() {
	}

	/**
	 * Admits the panorama as one semantic GUI element in the current frame.
	 *
	 * <p>The scheduler token must be present in {@code renderState}: whole-frame
	 * Vulkan consumes requests only from those tokens.  Keeping the attachment at
	 * admission prevents an animated title screen from retaining one unconsumed
	 * mesh per frame.</p>
	 */
	public static boolean enqueue(
		CubeMap cubeMap, float pitchDegrees, float yawDegrees, int guiWidth, int guiHeight,
		VulkanicGalBridge.GuiProjectionRecord projection, GuiRenderState renderState
	) {
		if (!RustGalGuiRenderer.isWholeFrameVulkanEnabled()
			|| renderState == null
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
		projection.validateLayout(guiWidth, guiHeight);
		// This background is drawn directly into the acquired frame, not an
		// item PIP texture. No offscreen guard band belongs in its geometry.
		int guard = 0;
		int renderWidth = guiWidth + guard * 2;
		int renderHeight = guiHeight + guard * 2;
		// A panorama is one continuous camera projection, not a curved GUI mesh.
		// Carry Frozen's three oversized fullscreen-triangle view rays and let the
		// Rust-owned panorama fragment program choose the cube face per pixel.
		// This is exact at face boundaries and avoids allocating 4,225 Java vertex
		// records (and their defensive FFI copies) every title-screen frame.
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = panoramaVertices(pitchDegrees, yawDegrees, projection);
		List<Integer> indices = List.of(0, 1, 2);
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			GuiRenderStratum.GUI_PANORAMA.order(), 0, VulkanicGalBridge.GUI_MESH_MATERIAL_PANORAMA, 1, image.assetId(), 0L, 0.0F,
			identity(), new float[] {1, 0, 0, 1, 0, 0}, 0, 0, guiWidth, guiHeight,
			guiWidth, guiHeight, renderWidth, renderHeight, guard, vertices, indices
		);
		var token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), GuiRenderStratum.GUI_PANORAMA, System.nanoTime());
		renderState.submitGuiElement(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_PANORAMA, "minecraft.gui.panorama", -1, -1.0F,
			GuiFillDirection.NONE, 0, 0, guiWidth, guiHeight, guiWidth, guiHeight
		));
		// Stage only after both the bounded mesh request and its render-state
		// ownership have been admitted.  This keeps a rejected panorama request
		// from retaining a Rust-owned cube-map asset.
		RustGalGuiRawImageAssets.stageCubeMap(image);
		return true;
	}

	static List<VulkanicGalBridge.GuiMeshVertexRecord> panoramaVertices(
		float pitchDegrees, float yawDegrees, VulkanicGalBridge.GuiProjectionRecord projection
	) {
		Matrix4f modelView = new Matrix4f().rotationX((float)Math.PI)
			.rotateX((float)Math.toRadians(pitchDegrees)).rotateY((float)Math.toRadians(yawDegrees));
		float cot = 1.0F / (float)Math.tan(FOV_RADIANS * 0.5F);
		float projectionX = cot / (projection.width() / projection.height());
		return List.of(
			panoramaVertex(modelView, 0.0F, 1.0F, projection.width(), projection.height(), projectionX, cot),
			panoramaVertex(modelView, 2.0F, 1.0F, projection.width(), projection.height(), projectionX, cot),
			panoramaVertex(modelView, 0.0F, -1.0F, projection.width(), projection.height(), projectionX, cot)
		);
	}

	private static VulkanicGalBridge.GuiMeshVertexRecord panoramaVertex(
		Matrix4f matrix, float screenX, float screenY, float width, float height, float projectionX, float projectionY
	) {
		float[] ray = cubeRay(matrix, screenX * 2.0F - 1.0F, 1.0F - screenY * 2.0F, projectionX, projectionY);
		// Panorama's dedicated Rust shader interprets the two UV pairs as an
		// interpolated camera ray. This is semantic data, not a Java GPU handle.
		return new VulkanicGalBridge.GuiMeshVertexRecord(
			new float[] {screenX * width, screenY * height, ray[0]},
			new float[] {0.0F, 0.0F}, new float[] {ray[1], ray[2]}, 0xFFFFFFFF, NORMAL_POSITIVE_Z
		);
	}

	static float[] cubeUv(Matrix4f matrix, float clipX, float clipY, float projectionX, float projectionY) {
		float[] ray = cubeRay(matrix, clipX, clipY, projectionX, projectionY);
		float dx = ray[0], dy = ray[1], dz = ray[2];
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

	static float[] cubeRay(Matrix4f matrix, float clipX, float clipY, float projectionX, float projectionY) {
		float x = clipX / projectionX, y = clipY / projectionY, z = -1.0F;
		// Frozen's panorama vertex shader uses
		// transpose(mat3(ModelViewMat)) * viewDirection. JOML exposes columns
		// as m00/m10/m20 etc., so this transpose multiplication takes the
		// corresponding row terms. Using the direct matrix multiplication here
		// reverses the semantic panorama camera rotation.
		float dx = matrix.m00() * x + matrix.m01() * y + matrix.m02() * z;
		float dy = matrix.m10() * x + matrix.m11() * y + matrix.m12() * z;
		float dz = matrix.m20() * x + matrix.m21() * y + matrix.m22() * z;
		return new float[] {dx, dy, dz};
	}

	private static float[] identity() { return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
}
