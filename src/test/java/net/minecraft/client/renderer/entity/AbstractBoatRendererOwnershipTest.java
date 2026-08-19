package net.minecraft.client.renderer.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractBoatRendererOwnershipTest {
	@Test
	void hullOwnershipIsIndependentOfEligibilityAndFailsClosed() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/AbstractBoatRenderer.java"));

		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(boatRenderState)"),
			"boat hulls must retain their canonical entity identity for Rust/source-pack semantics");
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible("));
		assertTrue(source.contains("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()"));
		assertTrue(source.contains("StandaloneModelRenderOwnershipPolicy.classify("));
		assertFalse(source.contains("currentModelMeshRoute(rustBoatHullEligible)"),
			"boat hull ownership must never be derived from current-state admission");

		int finalBoatTransform = source.indexOf("poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));");
		int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()", finalBoatTransform);
		int available = source.indexOf("Disposition.RUST_AVAILABLE", ownership);
		int animate = source.indexOf("this.model().setupAnim(boatRenderState);", available);
		int enqueue = source.indexOf("RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(", animate);
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", enqueue);
		int javaSubmit = source.indexOf("submitNodeCollector.submitModel(", unavailable);
		int additions = source.indexOf("this.submitTypeAdditions(", javaSubmit);

		assertTrue(finalBoatTransform >= 0 && ownership > finalBoatTransform,
			"ownership must be resolved only after the complete vanilla hull transform is available");
		assertTrue(available > ownership && animate > available && enqueue > animate,
			"the real animated boat model must populate the shared root before Rust copies its indexed mesh");
		assertTrue(unavailable > enqueue && javaSubmit > unavailable,
			"the explicit unavailable branch must structurally precede Java compatibility rendering");
		assertFalse(source.substring(unavailable, javaSubmit).contains("submitNodeCollector.submitModel("),
			"a Rust-owned unavailable hull must not escape through a Java Vulkan model submit");
		assertTrue(additions > javaSubmit,
			"type-specific additions must remain an independent producer after hull routing, matching vanilla ordering");
	}

	@Test
	void concreteBoatAndRaftAdaptersShareTheExactAnimatedRoot() throws Exception {
		String boat = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/BoatRenderer.java"));
		String raft = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/RaftRenderer.java"));
		String abstractModel = Files.readString(Path.of("src/main/java/net/minecraft/client/model/AbstractBoatModel.java"));
		String rustRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));

		assertTrue(boat.contains("this.model = new BoatModel(context.bakeLayer(modelLayerLocation));"));
		assertTrue(raft.contains("this.model = new RaftModel(context.bakeLayer(modelLayerLocation));"));
		for (String source : new String[] { boat, raft }) {
			assertTrue(source.contains("new Model.Simple(this.model.root(), this.model::renderType)"),
				"the semantic adapter must share the exact baked root and render-type semantics");
			assertTrue(source.contains("protected ResourceLocation textureLocation()"));
			assertTrue(source.contains("return this.texture;"),
				"Rust must receive the exact resource-pack texture identity selected by the concrete renderer");
		}
		assertTrue(abstractModel.contains("animatePaddle(boatRenderState.rowingTimeLeft, 0, this.leftPaddle)"));
		assertTrue(abstractModel.contains("animatePaddle(boatRenderState.rowingTimeRight, 1, this.rightPaddle)"));
		assertTrue(rustRenderer.contains("model instanceof Model.Simple"),
			"the semantic wrapper must remain an explicitly admitted existing Rust mesh model type");
	}

	@Test
	void boatWaterMaskIsExplicitlyUnavailableForRustWholeFrame() throws Exception {
		String boat = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/BoatRenderer.java"));
		String pipelines = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/RenderPipelines.java"));

		int waterPipeline = pipelines.indexOf("public static final RenderPipeline WATER_MASK");
		int nextPipeline = pipelines.indexOf("public static final RenderPipeline", waterPipeline + 1);
		String waterPipelineBody = pipelines.substring(waterPipeline, nextPipeline);
		assertTrue(waterPipelineBody.contains("withColorWrite(false)"),
			"vanilla boat water masking is a special color-write-disabled depth pass, not an ordinary textured mesh");

		int semanticCoverage = boat.indexOf("!submitNodeCollector.isSemanticCoverageOnly()");
		int rustOwner = boat.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute().usesRustWholeFrameVulkan()", semanticCoverage);
		int unavailableReturn = boat.indexOf("return;", rustOwner);
		int javaWaterMask = boat.indexOf("submitNodeCollector.submitModel(", unavailableReturn);
		assertTrue(semanticCoverage >= 0 && rustOwner > semanticCoverage && unavailableReturn > rustOwner && javaWaterMask > unavailableReturn,
			"Rust whole-frame ownership must stop before the Java water-mask submit while semantic coverage remains observational");
		assertTrue(boat.substring(rustOwner, javaWaterMask).contains("return;"));
		assertTrue(boat.contains("this.waterPatchModel.renderType(this.texture)"),
			"normal Java/OpenGL compatibility must retain the original water-mask submission");
	}

	@Test
	void sharedRenderersCoverEveryVanillaBoatAndRaftRegistration() throws Exception {
		String registrations = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java"));
		for (String type : new String[] {
			"ACACIA_BOAT", "ACACIA_CHEST_BOAT",
			"BIRCH_BOAT", "BIRCH_CHEST_BOAT",
			"CHERRY_BOAT", "CHERRY_CHEST_BOAT",
			"DARK_OAK_BOAT", "DARK_OAK_CHEST_BOAT",
			"JUNGLE_BOAT", "JUNGLE_CHEST_BOAT",
			"MANGROVE_BOAT", "MANGROVE_CHEST_BOAT",
			"OAK_BOAT", "OAK_CHEST_BOAT",
			"PALE_OAK_BOAT", "PALE_OAK_CHEST_BOAT",
			"SPRUCE_BOAT", "SPRUCE_CHEST_BOAT"
		}) {
			assertTrue(registrations.contains("EntityType." + type + ", context -> new BoatRenderer"),
				"expected vanilla boat hull to use shared BoatRenderer: " + type);
		}
		for (String type : new String[] { "BAMBOO_RAFT", "BAMBOO_CHEST_RAFT" }) {
			assertTrue(registrations.contains("EntityType." + type + ", context -> new RaftRenderer"),
				"expected vanilla raft hull to use shared RaftRenderer: " + type);
		}
	}

	@Test
	void allBoatMotionInputsRemainAtTheSemanticCallsite() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/AbstractBoatRenderer.java"));

		assertTrue(source.contains("180.0F - boatRenderState.yRot"));
		assertTrue(source.contains("Mth.sin(f) * f * boatRenderState.damageTime"));
		assertTrue(source.contains("boatRenderState.bubbleAngle"));
		assertTrue(source.contains("boatRenderState.rowingTimeLeft = abstractBoat.getRowingTime(0, f)"));
		assertTrue(source.contains("boatRenderState.rowingTimeRight = abstractBoat.getRowingTime(1, f)"));
		assertTrue(source.contains("poseStack.last()"),
			"Rust must receive the completed hull transform rather than reconstructing boat motion from renderer internals");
	}
}
