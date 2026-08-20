package net.minecraft.client.renderer.entity.layers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TranslucentLivingFeatureOwnershipTest {
	private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
	private static final Path LAYERS = PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/renderer/entity/layers");

	@Test
	void horseMarkingsRetainExactProducerStateButGateInexactRustMaterialSemantics() throws Exception {
		String source = Files.readString(LAYERS.resolve("HorseMarkingLayer.java"));

		assertTrue(source.contains("new Model.Simple(parentModel.root(), parentModel::renderType)"),
			"horse markings must retain the exact parent-model root for the future Rust material route");
		assertTrue(source.contains("RenderType.entityTranslucent(texture)"));
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(horseRenderState)"));
		assertTrue(source.contains("IndexedMeshMaterialCapabilities.preservesAlphaCutout(renderType)"),
			"the current 0.1 entity alpha discard must be rejected before Rust ownership can render it approximately");
		assertTrue(source.contains("overlayCoords == OverlayTexture.NO_OVERLAY"));
		assertTrue(source.contains("horseRenderState.outlineColor"));
		assertTrue(source.contains("texture == INVISIBLE_TEXTURE || horseRenderState.isInvisible"));

		int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()");
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", ownership);
		int javaSubmit = source.indexOf("submitNodeCollector.order(1)", unavailable);
		assertTrue(ownership >= 0 && unavailable > ownership && javaSubmit > unavailable);
		assertFalse(source.substring(unavailable, javaSubmit).contains("submitModel("),
			"Rust-unavailable horse markings must not escape through Java");
	}

	@Test
	void slimeOuterShellKeepsOutlineDistinctAndGatesInexactTranslucentMaterial() throws Exception {
		String source = Files.readString(LAYERS.resolve("SlimeOuterLayer.java"));

		assertTrue(source.contains("new Model.Simple(this.model.root(), this.model::renderType)"));
		assertTrue(source.contains("RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION)"));
		assertTrue(source.contains("RenderType.outline(SlimeRenderer.SLIME_LOCATION)"));
		assertTrue(source.contains("boolean rustEligible = !invisibleGlowOutline"));
		assertTrue(source.contains("IndexedMeshMaterialCapabilities.preservesAlphaCutout(renderType)"));
		assertTrue(source.contains("overlayCoords == OverlayTexture.NO_OVERLAY"));
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(slimeRenderState)"));
		assertTrue(source.contains("slimeRenderState.isInvisible && !invisibleGlowOutline"));

		int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()");
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", ownership);
		int javaSubmit = source.indexOf("submitNodeCollector.order(1)", unavailable);
		assertTrue(ownership >= 0 && unavailable > ownership && javaSubmit > unavailable);
		assertFalse(source.substring(unavailable, javaSubmit).contains("submitModel("));
	}

	@Test
	void currentIndexedMeshMaterialGateDescribesTheActualCoarseMapping() throws Exception {
		String capability = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/IndexedMeshMaterialCapabilities.java"
		));
		String renderPipelines = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/RenderPipelines.java"
		));
		String rustProducer = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));

		assertTrue(capability.contains("getShaderDefines().values().get(\"ALPHA_CUTOUT\")"));
		assertTrue(capability.contains("float currentRustThreshold = blend.isPresent() ? 0.0F : 0.5F"));
		assertFalse(capability.contains("entityIdentity"));
		assertFalse(capability.contains("texture"));

		int entityTranslucent = renderPipelines.indexOf("public static final RenderPipeline ENTITY_TRANSLUCENT");
		int nextPipeline = renderPipelines.indexOf("public static final RenderPipeline", entityTranslucent + 1);
		String contract = renderPipelines.substring(entityTranslucent, nextPipeline);
		assertTrue(contract.contains("withShaderDefine(\"ALPHA_CUTOUT\", 0.1F)"),
			"vanilla entity translucent discards alpha below 0.1");
		assertTrue(contract.contains("withBlend(BlendFunction.TRANSLUCENT)"));
		assertFalse(contract.contains("withDepthWrite(false)"),
			"entity translucent retains the pipeline default depth-write policy");

		assertTrue(rustProducer.contains("MATERIAL_ID_TRANSLUCENT_TEXTURED"));
		assertTrue(rustProducer.contains("DEPTH_POLICY_TEST_NO_WRITE"),
			"the current generic mapper still demonstrates the depth-policy prerequisite this gate protects");
	}

	@Test
	void ownershipRemainsIndependentFromCurrentStateAdmission() throws Exception {
		for (String producer : new String[] { "HorseMarkingLayer.java", "SlimeOuterLayer.java" }) {
			String source = Files.readString(LAYERS.resolve(producer));
			int eligibility = source.indexOf("isStandaloneModelMeshEligible(");
			int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()", eligibility);
			int classification = source.indexOf("StandaloneModelRenderOwnershipPolicy.classify(", ownership);
			assertTrue(eligibility >= 0);
			assertTrue(ownership > eligibility, producer + " ownership must be resolved independently of admission");
			assertTrue(classification > ownership);
			assertFalse(source.contains("currentModelMeshRoute(rustEligible)"));
			assertTrue(source.contains("\"rust-vulkan-unavailable\""));
		}
	}

	@Test
	void javaCompatibilityRetainsOriginalOrderedFeatureSubmissions() throws Exception {
		String horse = Files.readString(LAYERS.resolve("HorseMarkingLayer.java"));
		String slime = Files.readString(LAYERS.resolve("SlimeOuterLayer.java"));

		for (String source : new String[] { horse, slime }) {
			assertTrue(source.contains("submitNodeCollector.order(1)"));
			assertTrue(source.contains(".submitModel("));
			assertTrue(source.contains("submitNodeCollector.isSemanticCoverageOnly()"),
				"semantic coverage must remain observational rather than entering runtime Rust ownership");
		}
	}
}
