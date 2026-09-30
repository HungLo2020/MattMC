package net.minecraft.client.renderer.item;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStackRenderStateSemanticLayerTest {
	@org.junit.jupiter.api.BeforeAll
	static void bootstrap() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@Test
	void semanticLayerDefensivelyCopiesItsModelTransform() {
		float[] source = identityMatrix();
		ItemStackRenderState.SemanticLayer layer = new ItemStackRenderState.SemanticLayer(
			List.of(), new int[0], null, ItemStackRenderState.FoilType.NONE,
			false, false, true, source, identityNormal(), true
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
			false, false, true, new float[15], identityNormal(), true
		));
		float[] nonFinite = identityMatrix();
		nonFinite[3] = Float.NaN;
		assertThrows(IllegalArgumentException.class, () -> new ItemStackRenderState.SemanticLayer(
			List.of(), new int[0], null, ItemStackRenderState.FoilType.NONE,
			false, false, true, nonFinite, identityNormal(), true
		));
	}

	@Test
	void semanticLayerPreservesActualNormalPoseForBothHandsAndNonuniformScale() {
		for (var context : List.of(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
				net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
				net.minecraft.world.item.ItemDisplayContext.GROUND)) {
			for (var scale : List.of(new org.joml.Vector3f(2, 3, 4), new org.joml.Vector3f(-2, 3, 4),
					new org.joml.Vector3f(-2, -2, -2))) {
				var state = new ItemStackRenderState();
				state.displayContext = context;
				var transform = new net.minecraft.client.renderer.block.model.ItemTransform(
					new org.joml.Vector3f(23, -37, 61), new org.joml.Vector3f(0.2F, -0.4F, 0.7F), scale);
				state.newLayer().transform = transform;
				var expected = new net.blaze3d.vertex.PoseStack.Pose();
				transform.apply(context.leftHand(), expected);
				var snapshots = new java.util.ArrayList<ItemStackRenderState.SemanticLayer>();
				state.forEachSemanticLayer(snapshots::add);
				var snapshot = snapshots.getFirst();
				assertArrayEquals(expected.pose().get(new float[16]), snapshot.modelTransform());
				assertArrayEquals(expected.normal().get(new float[9]), snapshot.normalTransform());
				org.junit.jupiter.api.Assertions.assertEquals(expected.trustedNormals, snapshot.trustedNormals());
				state.clear();
				assertArrayEquals(expected.normal().get(new float[9]), snapshot.normalTransform());
			}
		}
	}

	@Test
	void semanticLayerCopiesIndependentNormalPoseAndRejectsMalformedPayloads() {
		float[] normals = new float[] {2, 0, 0, 0, 3, 0, 0, 0, -4};
		var layer = new ItemStackRenderState.SemanticLayer(List.of(), new int[0], null,
			ItemStackRenderState.FoilType.SPECIAL, false, false, false, identityMatrix(), normals, false);
		normals[0] = 99;
		layer.normalTransform()[4] = 99;
		assertArrayEquals(new float[] {2, 0, 0, 0, 3, 0, 0, 0, -4}, layer.normalTransform());
		for (float[] invalid : List.of(new float[8], new float[10],
				new float[] {1, 0, 0, 0, Float.NaN, 0, 0, 0, 1},
				new float[] {1, 0, 0, 0, 1, 0, 0, 0, Float.POSITIVE_INFINITY})) {
			assertThrows(IllegalArgumentException.class, () -> new ItemStackRenderState.SemanticLayer(
				List.of(), new int[0], null, ItemStackRenderState.FoilType.SPECIAL,
				false, false, false, identityMatrix(), invalid, false));
		}
	}

	private static float[] identityNormal() {
		return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
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
