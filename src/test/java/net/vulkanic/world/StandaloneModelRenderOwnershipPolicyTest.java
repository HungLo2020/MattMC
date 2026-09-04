package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.vulkanic.world.StandaloneModelRenderOwnershipPolicy.Disposition.JAVA_COMPATIBILITY;
import static net.vulkanic.world.StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE;
import static net.vulkanic.world.StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StandaloneModelRenderOwnershipPolicyTest {
	private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
	@Test
	void rustOwnershipSeparatesAdmissionFromAvailability() {
		assertEquals(
			RUST_AVAILABLE,
			StandaloneModelRenderOwnershipPolicy.classify(false, true, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			StandaloneModelRenderOwnershipPolicy.classify(false, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
	}

	@Test
	void compatibilityAndDisabledOwnersRemainExplicitlyUnavailableOnRust() {
		assertEquals(
			JAVA_COMPATIBILITY,
			StandaloneModelRenderOwnershipPolicy.classify(false, false, WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			StandaloneModelRenderOwnershipPolicy.classify(false, false, WorldRenderRoutePolicy.Route.DISABLED)
		);
	}

	@Test
	void semanticCoverageNeverEnqueuesRuntimeRustWork() {
		assertEquals(
			JAVA_COMPATIBILITY,
			StandaloneModelRenderOwnershipPolicy.classify(true, true, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			StandaloneModelRenderOwnershipPolicy.classify(true, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
	}

	@Test
	void standaloneModelUvsRemainPreNormalizedWhenCopiedToRust() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int extraction = source.indexOf("private static BlockMeshExtraction extractModelPartMesh(");
		int u = source.indexOf("sprite == null ? vertex.u() : sprite.getU(vertex.u())", extraction);
		int v = source.indexOf("sprite == null ? vertex.v() : sprite.getV(vertex.v())", u);
		assertTrue(extraction >= 0 && u > extraction && v > u,
			"pre-normalized standalone ModelPart UVs must enter Rust local-texture sampling unchanged");
	}

	@Test
	void dynamicModelTexturesWinOverSameNamedPackPlaceholders() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("private static byte[] readModelTexturePayload(");
		int dynamic = source.indexOf("byte[] dynamicPayload = readDynamicTexturePayload(textureIdentity);", method);
		int resource = source.indexOf("byte[] resourcePayload = readTexturePayloadForResource(textureIdentity);", dynamic);
		assertTrue(method >= 0 && dynamic > method && resource > dynamic,
			"live DynamicTexture pixels must be selected before same-named resource-pack bytes");
	}

	@Test
	void playerHandCallsitePublishesSkinAssetBeforeModelSubmission() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/entity/player/AvatarRenderer.java"));
		int publish = source.indexOf("ensureStandaloneModelTextureAsset(resourceLocation)");
		int enqueue = source.indexOf("enqueueStandaloneTranslucentModelMesh(", publish);
		assertTrue(publish >= 0 && enqueue > publish,
			"player-hand semantic callsite must publish the copied skin asset before enqueueing arm geometry");
	}

	@Test
	void playerHandCallsiteCannotFallThroughToJavaWhenRustOwnsFrame() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/entity/player/AvatarRenderer.java"));
		int method = source.indexOf("private void renderHand(");
		int ownership = source.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		int guard = source.indexOf("Java hand geometry is not a fallback", ownership);
		int javaSubmit = source.indexOf("submitNodeCollector.submitModelPart(", method);
		assertTrue(method >= 0 && ownership > method && guard > ownership && javaSubmit > guard,
			"Rust-owned hand frames must reject an unadmitted route instead of reaching Java ModelPart submission");
	}

	@Test
	void directModelEnqueueUsesFirstPersonStreamDuringHandFrame() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("private static <S> boolean enqueueEligibleModelMesh(");
		int destination = source.indexOf("List<VulkanicGalBridge.WorldMeshInstanceRecord> destination", method);
		int firstPerson = source.indexOf("? PENDING_FIRST_PERSON_MESH_INSTANCES", destination);
		int append = source.indexOf("destination.add(new VulkanicGalBridge.WorldMeshInstanceRecord(", firstPerson);
		assertTrue(method >= 0 && destination > method && firstPerson > destination && append > firstPerson,
			"direct-texture hand models must enter the dedicated first-person semantic stream");
	}
}
