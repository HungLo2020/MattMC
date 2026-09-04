package net.vulkanic.world;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistantHorizonsFaceMaterialResolverTest {
	private static DistantHorizonsFaceMaterialResolver.FaceMaterial material(String sprite) {
		return new DistantHorizonsFaceMaterialResolver.FaceMaterial(
			"minecraft:textures/atlas/blocks.png", sprite, 0.25F, 0.5F, 0.3125F, 0.5625F
		);
	}

	@Test
	void exactFacesRemainPureSemanticAtlasRecords() {
		var resolution = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/grass_block_top"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.NORTH, material("minecraft:block/grass_block_side"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.SOUTH, material("minecraft:block/grass_block_side"), false, false)
		));

		assertTrue(resolution.isComplete());
		assertEquals(DistantHorizonsFaceMaterialResolver.Status.COMPLETE, resolution.status());
		assertEquals("minecraft:block/grass_block_top", resolution.faces().get(Direction.UP).spriteIdentity());
		assertEquals("minecraft:block/grass_block_side", resolution.faces().get(Direction.NORTH).spriteIdentity());
	}

	@Test
	void coplanarFaceSpritesPreserveOrderedSemanticLayers() {
		var resolution = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/grass_block_top"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/dirt"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/grass_block_side_overlay"), false, false)
		));

		assertTrue(resolution.isComplete());
		assertEquals(DistantHorizonsFaceMaterialResolver.Status.COMPLETE, resolution.status());
		assertEquals(List.of("minecraft:block/grass_block_top", "minecraft:block/dirt", "minecraft:block/grass_block_side_overlay"),
			resolution.faceLayers().get(Direction.UP).stream().map(DistantHorizonsFaceMaterialResolver.FaceMaterial::spriteIdentity).toList());
		assertEquals(List.of(0, 1, 2), resolution.faceLayers().get(Direction.UP).stream().map(DistantHorizonsFaceMaterialResolver.FaceMaterial::layer).toList());
	}

	@Test
	void animatedAtlasRegionsRemainAvailableWhileUnculledQuadsStayUnavailable() {
		var animated = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/water_still"), true, false)
		));
		var unculled = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/grass_block_top"), false, true)
		));

		assertEquals(DistantHorizonsFaceMaterialResolver.Status.COMPLETE, animated.status());
		assertTrue(animated.isComplete());
		assertEquals(DistantHorizonsFaceMaterialResolver.Status.UNCULLED_QUAD, unculled.status());
	}

	@Test
	void partialFaceMapsNeverQualifyForExactAtlasProvenance() {
		var partial = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/stone"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.NORTH, material("minecraft:block/stone"), false, true)
		));

		assertEquals(DistantHorizonsFaceMaterialResolver.Status.PARTIAL_FACE_MAPPING, partial.status());
		assertTrue(partial.hasResolvedFaces());
		assertFalse(partial.isExactAtlasAdmissible());
	}

	@Test
	void grassSideBaseAndOverlayRemainAvailableTogether() {
		var overlay = new DistantHorizonsFaceMaterialResolver.FaceMaterial(
			"minecraft:textures/atlas/blocks.png", "minecraft:block/grass_block_side_overlay",
			0.25F, 0.5F, 0.3125F, 0.5625F,
			DistantHorizonsFaceMaterialResolver.FaceMaterial.CANONICAL_UV_CORNER_ORDER, 0, true, 0xff4fa13c
		);
		var resolution = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/grass_block_top"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.NORTH, material("minecraft:block/grass_block_side"), false, false),
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.NORTH, overlay, false, false)
		));

		assertEquals(DistantHorizonsFaceMaterialResolver.Status.COMPLETE, resolution.status());
		assertTrue(resolution.isComplete());
		assertEquals("minecraft:block/grass_block_top", resolution.faces().get(Direction.UP).spriteIdentity());
		assertEquals(List.of("minecraft:block/grass_block_side", "minecraft:block/grass_block_side_overlay"),
			resolution.faceLayers().get(Direction.NORTH).stream().map(DistantHorizonsFaceMaterialResolver.FaceMaterial::spriteIdentity).toList());
		assertEquals(List.of(false, true),
			resolution.faceLayers().get(Direction.NORTH).stream().map(DistantHorizonsFaceMaterialResolver.FaceMaterial::tinted).toList());
	}

	@Test
	void usesStableSemanticFaceIds() {
		assertEquals(0, DistantHorizonsFaceMaterialResolver.faceId(Direction.DOWN));
		assertEquals(1, DistantHorizonsFaceMaterialResolver.faceId(Direction.UP));
		assertEquals(2, DistantHorizonsFaceMaterialResolver.faceId(Direction.NORTH));
		assertEquals(3, DistantHorizonsFaceMaterialResolver.faceId(Direction.SOUTH));
		assertEquals(4, DistantHorizonsFaceMaterialResolver.faceId(Direction.WEST));
		assertEquals(5, DistantHorizonsFaceMaterialResolver.faceId(Direction.EAST));
	}

	@Test
	void rejectsNonPermutationUvCornerOrders() {
		assertThrows(IllegalArgumentException.class, () ->
			new DistantHorizonsFaceMaterialResolver.FaceMaterial(
				"minecraft:textures/atlas/blocks.png", "minecraft:block/grass_block_top",
				0.25F, 0.5F, 0.3125F, 0.5625F, 0
			)
		);
	}

	@Test
	void copiedStateResolutionCacheIsBoundedAndCanBeInvalidated() {
		DistantHorizonsFaceMaterialResolver.clearCachedStateResolutions();
		var resolution = DistantHorizonsFaceMaterialResolver.resolveCandidates(List.of(
			new DistantHorizonsFaceMaterialResolver.FaceCandidate(Direction.UP, material("minecraft:block/stone"), false, false)
		));
		for (int index = 0; index < 2_049; index++) {
			DistantHorizonsFaceMaterialResolver.cacheStateResolutionForTest("minecraft:state_" + index, resolution);
		}

		assertEquals(2_048, DistantHorizonsFaceMaterialResolver.cachedStateResolutionCountForTest());
		DistantHorizonsFaceMaterialResolver.clearCachedStateResolutions();
		assertEquals(0, DistantHorizonsFaceMaterialResolver.cachedStateResolutionCountForTest());
	}
}
