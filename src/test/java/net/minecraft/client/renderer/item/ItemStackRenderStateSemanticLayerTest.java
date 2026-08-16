package net.minecraft.client.renderer.item;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStackRenderStateSemanticLayerTest {
	@Test
	void semanticLayerDefensivelyCopiesItsModelTransform() {
		float[] source = identityMatrix();
		ItemStackRenderState.SemanticLayer layer = new ItemStackRenderState.SemanticLayer(
			List.of(), new int[0], null, ItemStackRenderState.FoilType.NONE,
			false, false, true, source
		);

		source[0] = 7.0F;
		float[] extracted = layer.modelTransform();
		extracted[5] = 9.0F;

		assertArrayEquals(identityMatrix(), layer.modelTransform());
	}

	@Test
	void semanticLayerRejectsMalformedModelTransforms() {
		assertThrows(IllegalArgumentException.class, () -> new ItemStackRenderState.SemanticLayer(
			List.of(), new int[0], null, ItemStackRenderState.FoilType.NONE,
			false, false, true, new float[15]
		));
		float[] nonFinite = identityMatrix();
		nonFinite[3] = Float.NaN;
		assertThrows(IllegalArgumentException.class, () -> new ItemStackRenderState.SemanticLayer(
			List.of(), new int[0], null, ItemStackRenderState.FoilType.NONE,
			false, false, true, nonFinite
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
