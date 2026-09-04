package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ParticleSemanticContractTest {
	@Test
	void quadParticleAdmissionRollsBackPartialRustMaterialStreams() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/state/QuadParticleRenderState.java"));
		int checkpoint = source.indexOf("markMaterialQuadBatch()");
		int rollback = source.indexOf("rollbackMaterialQuadBatch(checkpoint)", checkpoint);
		assertTrue(checkpoint >= 0 && rollback > checkpoint,
			"particle-group admission must roll back a partial semantic prefix on rejection");
	}

	@Test
	void quadParticleSnapshotRejectsMalformedValuesBeforeStorage() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/state/QuadParticleRenderState.java"));
		int add = source.indexOf("public void add(");
		int finite = source.indexOf("Rust whole-frame particle admission requires finite copied quad semantics", add);
		int store = source.indexOf("computeIfAbsent(layer", finite);
		assertTrue(add >= 0 && finite > add && store > finite,
			"Rust particle snapshots must reject malformed quad state before storing it");
	}

	@Test
	void individualParticleProducersCheckMaterialCapacityBeforeAppend() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int terrain = source.indexOf("public static boolean enqueueTerrainParticle(");
		int terrainCapacity = source.indexOf("PENDING_MATERIAL_QUADS.size() >= MAX_RUST_WORLD_MATERIAL_QUADS", terrain);
		int ordinary = source.indexOf("public static void enqueueParticleQuad(");
		int ordinaryCapacity = source.indexOf("PENDING_MATERIAL_QUADS.size() >= MAX_RUST_WORLD_MATERIAL_QUADS", ordinary);
		int atlas = source.indexOf("public static boolean enqueueParticleQuadForAtlas(");
		int atlasCapacity = source.indexOf("PENDING_MATERIAL_QUADS.size() >= MAX_RUST_WORLD_MATERIAL_QUADS", atlas);
		assertTrue(terrainCapacity > terrain && ordinaryCapacity > ordinary && atlasCapacity > atlas,
			"all individual particle producers must check the bounded Rust material stream");
	}

	@Test
	void terrainParticlesRetainTheExplicitParticleSourceIdentity() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int terrain = source.indexOf("public static boolean enqueueTerrainParticle(");
		int ordinary = source.indexOf("public static void enqueueParticleQuad(", terrain);
		int sourceProgram = source.indexOf("MATERIAL_SOURCE_PARTICLES", terrain);
		assertTrue(terrain >= 0 && sourceProgram > terrain && sourceProgram < ordinary,
			"terrain particles must be admitted to Rust's explicit particle source writer");
	}

	@Test
	void particleAtlasAdmissionValidatesViewportBeforePublishingAsset() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static boolean enqueueParticleQuadForAtlas(");
		int viewport = source.indexOf("ensureBoundedParticleViewportLocked()", method);
		int asset = source.indexOf("ensureParticleAtlasAssetLocked(atlasLocation, textureId)", method);
		assertTrue(method >= 0 && viewport > method && asset > viewport,
			"particle atlas payloads must not publish before frame-local viewport admission");
	}

	@Test
	void particleQuadAdmissionRejectsInvertedOrUnboundedUvSpans() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int helper = source.indexOf("private static void validateParticleQuadSemantics(");
		int invalidSpan = source.indexOf("Rust particle quad UV span is invalid", helper);
		assertTrue(helper >= 0 && invalidSpan > helper
			&& source.substring(helper, invalidSpan).contains("localU1 < localU0")
			&& source.substring(helper, invalidSpan).contains("localV1 < localV0"),
			"particle semantic admission must reject inverted or unbounded UV spans before vertex publication");
	}

	@Test
	void terrainParticleControlsAreAppliedAtTheSharedRustAdmissionBoundary() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static boolean shouldRouteTerrainParticle(");
		int disabled = source.indexOf("rustGalWorldMaterial.terrainParticle.disabled", method);
		int legacy = source.indexOf("rustGalWorldMaterial.terrainParticle.legacyControl", disabled);
		int unavailable = source.indexOf("Rust whole-frame terrain particle route is unavailable under", legacy);
		int texture = source.indexOf("terrainParticleTextureId(blockState)", unavailable);
		assertTrue(method >= 0 && disabled > method && legacy > disabled && unavailable > legacy && texture > unavailable,
			"direct whole-frame terrain collection must honor particle controls before texture admission");
	}

	@Test
	void reloadableParticleTexturesUseCopiedSemanticImagesInsteadOfJavaGpuState() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int eligibility = source.indexOf("public static boolean canUseParticleAtlas");
		int snapshot = source.indexOf("RustGalGuiRawImageAssets.semanticSnapshotUnstaged(atlasLocation)", eligibility);
		int fallback = source.indexOf("if (!(texture instanceof TextureAtlas candidate))", eligibility);
		int encoded = source.indexOf("encodeSemanticImageSnapshot(snapshot)", fallback);
		int registered = source.indexOf("registerWorldMeshTexture(", encoded);
		assertTrue(eligibility >= 0 && snapshot > eligibility,
			"reloadable particle textures must be admitted from a bounded semantic CPU snapshot");
		assertTrue(fallback >= 0 && encoded > fallback && registered > encoded,
			"reloadable particle snapshots must be encoded and registered as Rust-owned assets");
		assertTrue(source.substring(eligibility, fallback).contains("return snapshot != null"),
			"particle eligibility must not claim a reloadable texture without copied pixels");
	}

	@Test
	void noRenderParticleStateKeepsAnExplicitRustSemanticNoOp() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/particle/NoRenderParticleGroup.java"));
		int state = source.indexOf("EMPTY_RENDER_STATE = new ParticleGroupRenderState()");
		int submit = source.indexOf("public void submit(", state);
		int semantic = source.indexOf("public void submitSemantic(", submit);
		assertTrue(state >= 0 && submit > state && semantic > submit,
			"the intentionally empty particle state must not inherit the Java-only semantic fallback");
	}
}
