package net.vulkanic.gui;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuiItemMeshSemanticCollectorTest {
	@Test
	void copiedMeshSemanticsDoNotRetainMutableCallerArrays() {
		float[] transform = identityMatrix();
		float[] positions = new float[] {
			0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F
		};
		float[] uvs = new float[] {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
		float[] localUvs = new float[] {0.25F, 0.25F, 0.75F, 0.25F, 0.75F, 0.75F, 0.25F, 0.75F};
		GuiItemMeshSemanticCollector.GuiItemMeshQuad quad = new GuiItemMeshSemanticCollector.GuiItemMeshQuad(
			7L, "minecraft:block/stone", positions, uvs, localUvs, new int[] {1, 2, 3, 4}, new int[] {5, 6, 7, 8}, 2, true
		);
		GuiItemMeshSemanticCollector.GuiItemMeshLayer layer = new GuiItemMeshSemanticCollector.GuiItemMeshLayer(
			GuiItemMeshSemanticCollector.MaterialMode.OPAQUE, true, transform, List.of(quad)
		);

		transform[0] = 7.0F;
		positions[0] = 8.0F;
		float[] copiedTransform = layer.modelTransform();
		copiedTransform[5] = 9.0F;
		float[] copiedPositions = quad.positions();
		copiedPositions[3] = 10.0F;
		float[] copiedLocalUvs = quad.localUvs();
		copiedLocalUvs[0] = 11.0F;

		assertArrayEquals(identityMatrix(), layer.modelTransform());
		assertArrayEquals(new float[] {
			0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F
		}, quad.positions());
		assertArrayEquals(localUvs, quad.localUvs());
	}

	@Test
	void copiedMeshSemanticsRejectMalformedGeometry() {
		assertThrows(IllegalArgumentException.class, () -> new GuiItemMeshSemanticCollector.GuiItemMeshQuad(
			7L, "minecraft:block/stone", new float[11], new float[8], new float[8], new int[4], new int[4], 2, true
		));
		float[] nonFinite = new float[8];
		nonFinite[3] = Float.POSITIVE_INFINITY;
		assertThrows(IllegalArgumentException.class, () -> new GuiItemMeshSemanticCollector.GuiItemMeshQuad(
			7L, "minecraft:block/stone", new float[12], nonFinite, new float[8], new int[4], new int[4], 2, true
		));
	}

	@Test
	void copiedStandard3dTargetDoesNotRetainMutableTransforms() {
		float[] guiPose = new float[] {1.0F, 0.0F, 0.0F, 1.0F, 4.0F, 8.0F};
		float[] offscreen = identityMatrix();
		GuiItemMeshSemanticCollector.GuiItemMesh mesh = new GuiItemMeshSemanticCollector.GuiItemMesh(
			"minecraft:stone", 4, 8, 4, 8, 20, 24, guiPose, 34, 34, 1, offscreen, List.of()
		);
		guiPose[0] = 7.0F;
		offscreen[5] = 9.0F;
		float[] copied = mesh.offscreenModelTransform();
		copied[10] = 11.0F;

		assertArrayEquals(new float[] {1.0F, 0.0F, 0.0F, 1.0F, 4.0F, 8.0F}, mesh.guiPose());
		assertArrayEquals(identityMatrix(), mesh.offscreenModelTransform());
		assertThrows(IllegalArgumentException.class, () -> new GuiItemMeshSemanticCollector.GuiItemMesh(
			"minecraft:stone", 0, 0, 0, 0, 16, 16, new float[6], 2, 2, 1, identityMatrix(), List.of()
		));
	}

	private static float[] identityMatrix() {
		return new float[] {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F
		};
	}
}
