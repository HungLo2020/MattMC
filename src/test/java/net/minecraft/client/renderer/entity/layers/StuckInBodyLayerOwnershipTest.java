package net.minecraft.client.renderer.entity.layers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StuckInBodyLayerOwnershipTest {
	private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
	private static final Path LAYERS = PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/renderer/entity/layers");

	@Test
	void onlyAuditedVanillaStuckProducersCanEnterTheRustRoute() throws Exception {
		String source = Files.readString(LAYERS.resolve("StuckInBodyLayer.java"));
		String arrowLayer = Files.readString(LAYERS.resolve("ArrowLayer.java"));
		String stingerLayer = Files.readString(LAYERS.resolve("BeeStingerLayer.java"));
		String arrowModel = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/model/ArrowModel.java"));
		String stingerModel = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/model/BeeStingerModel.java"));
		String rustRenderer = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));

		assertTrue(source.contains("this.model.getClass().equals(ArrowModel.class)"));
		assertTrue(source.contains("this.model.getClass().equals(BeeStingerModel.class)"));
		assertTrue(arrowLayer.contains("new ArrowModel(context.bakeLayer(ModelLayers.ARROW))"));
		assertTrue(stingerLayer.contains("new BeeStingerModel(context.bakeLayer(ModelLayers.BEE_STINGER))"));
		assertTrue(arrowModel.contains("super(modelPart, RenderType::entityCutout)"),
			"embedded arrows must retain the exact vanilla entityCutout material contract");
		assertTrue(stingerModel.contains("super(modelPart, RenderType::entityCutout)"),
			"embedded bee stingers must retain the exact vanilla entityCutout material contract");
		assertTrue(rustRenderer.contains("model instanceof Model.Simple"),
			"the semantic wrapper must remain an explicitly admitted existing Rust mesh model type");

		try (Stream<Path> files = Files.list(LAYERS)) {
			Set<String> subclasses = files
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.filter(path -> {
					try {
						return Files.readString(path).contains("extends StuckInBodyLayer");
					} catch (Exception exception) {
						throw new RuntimeException(exception);
					}
				})
				.map(path -> path.getFileName().toString())
				.collect(Collectors.toSet());
			assertEquals(Set.of("ArrowLayer.java", "BeeStingerLayer.java"), subclasses,
				"new StuckInBodyLayer producers require an explicit Rust semantic audit before admission");
		}
	}

	@Test
	void ownershipIsIndependentOfEligibilityAndUnavailableFailsClosed() throws Exception {
		String source = Files.readString(LAYERS.resolve("StuckInBodyLayer.java"));

		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(avatarRenderState)"),
			"feature meshes must inherit the parent avatar entity identity for source/shader semantics");
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible("));
		assertTrue(source.contains("avatarRenderState.outlineColor"),
			"outline state must be part of admission rather than silently dropped");
		assertTrue(source.contains("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()"));
		assertTrue(source.contains("StandaloneModelRenderOwnershipPolicy.classify("));
		assertFalse(source.contains("currentModelMeshRoute(rustEligible)"),
			"current-state eligibility must not choose ownership");

		int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()");
		int classification = source.indexOf("StandaloneModelRenderOwnershipPolicy.classify(", ownership);
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", classification);
		int unavailableReturn = source.indexOf("return;", unavailable);
		int randomTraversal = source.indexOf("RandomSource.create(avatarRenderState.id)", unavailableReturn);
		int javaSubmit = source.indexOf("submitNodeCollector.submitModel(");
		assertTrue(ownership >= 0 && classification > ownership && unavailable > classification);
		assertTrue(unavailableReturn > unavailable && randomTraversal > unavailableReturn,
			"Rust-unavailable ownership must stop before placement traversal and any draw submission");
		assertFalse(source.substring(unavailable, unavailableReturn).contains("submitNodeCollector.submitModel("));
		assertTrue(javaSubmit >= 0,
			"normal Java/OpenGL compatibility must retain the original model submission");
		assertTrue(source.contains("\"rust-vulkan-unavailable\""));
	}

	@Test
	void rustUsesTheExactResolvedPlacementAndSharedModelRoot() throws Exception {
		String source = Files.readString(LAYERS.resolve("StuckInBodyLayer.java"));

		assertTrue(source.contains("new Model.Simple(model.root(), model::renderType)"),
			"Rust must copy the exact model root used by the vanilla feature layer");
		assertTrue(source.contains("this.model.setupAnim(this.modelState);"),
			"the real model state must be applied before the no-op semantic wrapper is copied");
		assertTrue(source.contains("RandomSource.create(avatarRenderState.id)"));
		assertTrue(source.contains("this.getParentModel().getRandomBodyPart(randomSource)"));
		assertTrue(source.contains("modelPart.getRandomCube(randomSource)"));
		assertTrue(source.contains("snapToFace(x)"));
		assertTrue(source.contains("snapToFace(y)"));
		assertTrue(source.contains("snapToFace(z)"));
		assertTrue(source.contains("Mth.lerp(x, cube.minX, cube.maxX) / 16.0F"));
		assertTrue(source.contains("Mth.lerp(y, cube.minY, cube.maxY) / 16.0F"));
		assertTrue(source.contains("Mth.lerp(z, cube.minZ, cube.maxZ) / 16.0F"));
		assertTrue(source.contains("Math.atan2(xDirection, zDirection)"));
		assertTrue(source.contains("Math.atan2(yDirection, horizontal)"));

		int orientation = source.indexOf("poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));");
		int enqueue = source.indexOf("RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(", orientation);
		assertTrue(orientation >= 0 && enqueue > orientation,
			"Rust must receive the completed vanilla placement/orientation pose");
		String enqueueCall = source.substring(enqueue, source.indexOf(")) {", enqueue) + 2);
		assertTrue(enqueueCall.contains("this.rustSemanticModel"));
		assertTrue(enqueueCall.contains("poseStack.last()"));
		assertTrue(enqueueCall.contains("this.texture"));
		assertTrue(enqueueCall.contains("entityIdentity"));
		assertTrue(enqueueCall.contains("packedLight"));
		assertTrue(enqueueCall.contains("OverlayTexture.NO_OVERLAY"));
	}

	@Test
	void vanillaArrowAndStingerPlacementStylesRemainDistinct() throws Exception {
		String arrowLayer = Files.readString(LAYERS.resolve("ArrowLayer.java"));
		String stingerLayer = Files.readString(LAYERS.resolve("BeeStingerLayer.java"));

		assertTrue(arrowLayer.contains("StuckInBodyLayer.PlacementStyle.IN_CUBE"),
			"embedded arrows must keep vanilla in-cube placement");
		assertTrue(stingerLayer.contains("StuckInBodyLayer.PlacementStyle.ON_SURFACE"),
			"bee stingers must keep vanilla surface placement");
		assertTrue(arrowLayer.contains("TippableArrowRenderer.NORMAL_ARROW_LOCATION"));
		assertTrue(stingerLayer.contains("textures/entity/bee/bee_stinger.png"));
	}
}
