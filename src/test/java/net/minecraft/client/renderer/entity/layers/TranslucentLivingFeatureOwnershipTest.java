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
	void horseMarkingsUseTheAnimatedParentModelAndExactTranslucentContract() throws Exception {
		String source = Files.readString(LAYERS.resolve("HorseMarkingLayer.java"));

		assertTrue(source.contains("new Model.Simple(parentModel.root(), parentModel::renderType)"),
			"horse markings must copy the exact parent-model root rather than a reconstructed mesh");
		assertTrue(source.contains("RenderType.entityTranslucent(texture)"));
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(horseRenderState)"),
			"the feature mesh must inherit the horse entity identity for source/shader semantics");
		assertTrue(source.contains("overlayCoords == OverlayTexture.NO_OVERLAY"),
			"hurt/red overlay semantics must remain outside the admitted Rust slice");
		assertTrue(source.contains("horseRenderState.outlineColor"),
			"outline state must participate in admission rather than being silently dropped");
		assertTrue(source.contains("texture == INVISIBLE_TEXTURE || horseRenderState.isInvisible"),
			"no-marking and invisible horses must retain vanilla no-draw behavior");

		int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()");
		int available = source.indexOf("Disposition.RUST_AVAILABLE", ownership);
		int animate = source.indexOf("this.getParentModel().setupAnim(horseRenderState);", available);
		int enqueue = source.indexOf("enqueueStandaloneModelMesh(", animate);
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", enqueue);
		int javaSubmit = source.indexOf("submitNodeCollector.order(1)", unavailable);
		assertTrue(ownership >= 0 && available > ownership && animate > available && enqueue > animate);
		assertTrue(unavailable > enqueue && javaSubmit > unavailable);
		assertFalse(source.substring(unavailable, javaSubmit).contains("submitModel("),
			"Rust-unavailable horse markings must not escape through Java");
	}

	@Test
	void slimeOuterShellAdmitsOnlyTheOrdinaryTranslucentState() throws Exception {
		String source = Files.readString(LAYERS.resolve("SlimeOuterLayer.java"));

		assertTrue(source.contains("new Model.Simple(this.model.root(), this.model::renderType)"));
		assertTrue(source.contains("RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION)"));
		assertTrue(source.contains("RenderType.outline(SlimeRenderer.SLIME_LOCATION)"),
			"the special invisible-glow outline path must remain represented distinctly");
		assertTrue(source.contains("boolean rustEligible = !invisibleGlowOutline"),
			"the outline-only slime shell must never be admitted as ordinary translucent work");
		assertTrue(source.contains("overlayCoords == OverlayTexture.NO_OVERLAY"));
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(slimeRenderState)"));
		assertTrue(source.contains("slimeRenderState.isInvisible && !invisibleGlowOutline"),
			"fully invisible non-glowing slimes must retain vanilla no-draw behavior");

		int available = source.indexOf("Disposition.RUST_AVAILABLE");
		int animate = source.indexOf("this.model.setupAnim(slimeRenderState);", available);
		int enqueue = source.indexOf("enqueueStandaloneModelMesh(", animate);
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", enqueue);
		int javaSubmit = source.indexOf("submitNodeCollector.order(1)", unavailable);
		assertTrue(available >= 0 && animate > available && enqueue > animate);
		assertTrue(unavailable > enqueue && javaSubmit > unavailable);
		assertFalse(source.substring(unavailable, javaSubmit).contains("submitModel("));
	}

	@Test
	void ownershipIsIndependentFromCurrentStateAdmission() throws Exception {
		for (String producer : new String[] { "HorseMarkingLayer.java", "SlimeOuterLayer.java" }) {
			String source = Files.readString(LAYERS.resolve(producer));
			int eligibility = source.indexOf("isStandaloneModelMeshEligible(");
			int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()", eligibility);
			int classification = source.indexOf("StandaloneModelRenderOwnershipPolicy.classify(", ownership);
			assertTrue(eligibility >= 0, producer + " must retain bounded current-state admission");
			assertTrue(ownership > eligibility, producer + " ownership must be resolved independently of admission");
			assertTrue(classification > ownership);
			assertFalse(source.contains("currentModelMeshRoute(rustEligible)"));
			assertTrue(source.contains("\"rust-vulkan-unavailable\""));
		}
	}

	@Test
	void sharedMeshFrontendPreservesTranslucentBlendAndDepthSemantics() throws Exception {
		String rustProducer = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String renderPipelines = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/minecraft/client/renderer/RenderPipelines.java"));

		assertTrue(rustProducer.contains("BlendFunction.TRANSLUCENT.equals(blend.get())"));
		assertTrue(rustProducer.contains("MATERIAL_ID_TRANSLUCENT_TEXTURED"));
		assertTrue(rustProducer.contains("MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(rustProducer.contains("DEPTH_POLICY_TEST_NO_WRITE"),
			"the copied mesh contract must retain vanilla translucent depth-write semantics");

		int entityTranslucent = renderPipelines.indexOf("public static final RenderPipeline ENTITY_TRANSLUCENT");
		int nextPipeline = renderPipelines.indexOf("public static final RenderPipeline", entityTranslucent + 1);
		String contract = renderPipelines.substring(entityTranslucent, nextPipeline);
		assertTrue(contract.contains("withBlend(BlendFunction.TRANSLUCENT)"));
		assertTrue(contract.contains("withDepthWrite(false)"));
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
