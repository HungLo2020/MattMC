package net.minecraft.client.renderer.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractMinecartRendererOwnershipTest {
	@Test
	void minecartBodyUsesCopiedStandaloneMeshWithoutEligibilityDrivenJavaFallback() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/AbstractMinecartRenderer.java"));
		String rustWorld = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));

		assertTrue(source.contains("private final Model.Simple rustSemanticModel;"),
			"minecart body extraction should use an already-admitted semantic model type instead of widening generic EntityModel admission");
		assertTrue(source.contains("new Model.Simple(this.model.root(), this.model::renderType)"),
			"the Rust semantic model must share the exact baked vanilla minecart root and render-type semantics");
		assertTrue(rustWorld.contains("model instanceof Model.Simple"),
			"the semantic adapter must remain an explicitly admitted existing Rust model family rather than relying on accidental generic EntityModel support");
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.entityIdentity(minecartRenderState)"),
			"the copied mesh must retain the canonical entity identity for Rust/source-pack semantics");
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible("));
		assertTrue(rustWorld.contains("public static <S> boolean enqueueStandaloneModelMesh("));
		assertTrue(rustWorld.contains("return enqueueEligibleModelMesh("),
			"standalone minecart semantics must enter the existing copied indexed-mesh asset/instance path");
		assertTrue(source.contains("minecartRenderState.outlineColor"),
			"outline state must participate in admission instead of being silently dropped by the Rust body path");
		assertTrue(source.contains("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()"));
		assertTrue(source.contains("StandaloneModelRenderOwnershipPolicy.classify("));
		assertFalse(source.contains("currentModelMeshRoute(rustMinecartBodyEligible)"),
			"minecart ownership must never be derived from current-state eligibility");

		int contents = source.indexOf("this.submitMinecartContents(");
		int bodyTransform = source.indexOf("poseStack.scale(-1.0F, -1.0F, 1.0F);");
		int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()", bodyTransform);
		int available = source.indexOf("Disposition.RUST_AVAILABLE", ownership);
		int reset = source.indexOf("this.rustSemanticModel.resetPose();", available);
		int enqueue = source.indexOf("RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(", reset);
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", enqueue);
		int javaSubmit = source.indexOf("submitNodeCollector.submitModel(", unavailable);
		assertTrue(contents >= 0 && bodyTransform > contents,
			"minecart display-block semantics must remain an independent producer before the chassis body submission");
		assertTrue(ownership > bodyTransform && available > ownership && reset > available && enqueue > reset,
			"Rust ownership must be resolved after the exact vanilla body transform and before copied mesh submission");
		assertTrue(unavailable > enqueue && javaSubmit > unavailable,
			"the explicit unavailable branch must structurally precede the Java compatibility submit");
		String unavailableBranch = source.substring(unavailable, javaSubmit);
		assertFalse(unavailableBranch.contains("submitNodeCollector.submitModel("),
			"a Rust-owned unavailable minecart body must fail closed rather than escape through Java");
		assertTrue(source.contains("Unit.INSTANCE"),
			"the static semantic adapter must not invent or retain mutable minecart animation state");
		assertTrue(source.contains("MINECART_LOCATION"));
	}

	@Test
	void sharedRendererCoversTheVanillaMinecartChassisFamily() throws Exception {
		String ordinary = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/MinecartRenderer.java"));
		String tnt = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/TntMinecartRenderer.java"));
		String registrations = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java"));

		assertTrue(ordinary.contains("extends AbstractMinecartRenderer"));
		assertTrue(tnt.contains("extends AbstractMinecartRenderer"));
		for (String registration : new String[] {
			"EntityType.MINECART, context -> new MinecartRenderer",
			"EntityType.CHEST_MINECART, context -> new MinecartRenderer",
			"EntityType.COMMAND_BLOCK_MINECART, context -> new MinecartRenderer",
			"EntityType.FURNACE_MINECART, context -> new MinecartRenderer",
			"EntityType.HOPPER_MINECART, context -> new MinecartRenderer",
			"EntityType.SPAWNER_MINECART, context -> new MinecartRenderer",
			"EntityType.TNT_MINECART, TntMinecartRenderer::new"
		}) {
			assertTrue(registrations.contains(registration), "expected shared minecart chassis registration: " + registration);
		}
	}

	@Test
	void railDamageAndDisplayContentTransformsRemainOnTheSemanticCallsite() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/AbstractMinecartRenderer.java"));

		assertTrue(source.contains("newRender(minecartRenderState, poseStack)"));
		assertTrue(source.contains("oldRender(minecartRenderState, poseStack)"));
		assertTrue(source.contains("Mth.sin(i) * i * minecartRenderState.damageTime"));
		assertTrue(source.contains("poseStack.translate(-0.5F, (minecartRenderState.displayOffset - 8) / 16.0F, 0.5F)"));
		assertTrue(source.contains("this.submitMinecartContents(minecartRenderState, blockState, poseStack, submitNodeCollector, minecartRenderState.lightCoords)"));
		assertTrue(source.contains("poseStack.last()"),
			"Rust must receive the completed semantic chassis transform rather than reconstructing rail motion from Java renderer internals");
	}
}
