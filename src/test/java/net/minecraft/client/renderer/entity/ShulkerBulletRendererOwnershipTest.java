package net.minecraft.client.renderer.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShulkerBulletRendererOwnershipTest {
	private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));

	@Test
	void rustRouteCopiesBothVanillaPassesFromOneAnimatedRoot() throws Exception {
		String renderer = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/entity/ShulkerBulletRenderer.java"
		));
		String model = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/model/ShulkerBulletModel.java"
		));
		String entityModel = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/model/EntityModel.java"
		));
		String worldRenderer = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));

		assertTrue(renderer.contains("new Model.Simple(this.model.root(), this.model::renderType)"),
			"Rust must copy the exact baked Shulker Bullet root rather than a reconstructed model");
		assertTrue(model.contains("this.main.yRot = shulkerBulletRenderState.yRot"));
		assertTrue(model.contains("this.main.xRot = shulkerBulletRenderState.xRot"));
		assertTrue(entityModel.contains("this(modelPart, RenderType::entityCutoutNoCull)"),
			"the base Shulker Bullet pass must remain the vanilla cutout-no-cull contract");
		assertTrue(renderer.contains("RenderType.entityTranslucent(TEXTURE_LOCATION)"),
			"the glow shell must retain the vanilla translucent contract");
		assertTrue(renderer.contains("private static final int GLOW_TINT = 654311423;"));
		assertTrue(worldRenderer.contains("model instanceof Model.Simple"),
			"the semantic wrapper must remain an explicitly admitted existing Rust mesh model type");

		int available = renderer.indexOf("Disposition.RUST_AVAILABLE");
		int animate = renderer.indexOf("this.model.setupAnim(shulkerBulletRenderState);", available);
		int firstEnqueue = renderer.indexOf("enqueueStandaloneModelMesh(", animate);
		int shellScale = renderer.indexOf("poseStack.scale(1.5F, 1.5F, 1.5F);", firstEnqueue);
		int secondEnqueue = renderer.indexOf("enqueueStandaloneModelMesh(", firstEnqueue + 1);
		assertTrue(animate > available && firstEnqueue > animate,
			"the real model animation must populate the shared root before Rust extraction");
		assertTrue(shellScale > firstEnqueue && secondEnqueue > shellScale,
			"Rust must preserve the original base-pass then 1.5x translucent-shell ordering");
		assertEquals(2, occurrences(renderer.substring(available, renderer.indexOf("Disposition.RUST_UNAVAILABLE", secondEnqueue)),
			"enqueueStandaloneModelMesh("),
			"the admitted Rust route must enqueue exactly the base and glow passes");
	}

	@Test
	void ownershipAndAdmissionFailClosedWithoutLosingJavaCompatibility() throws Exception {
		String renderer = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/entity/ShulkerBulletRenderer.java"
		));

		assertTrue(renderer.contains("RustGalWorldPrimitiveRenderer.entityIdentity(shulkerBulletRenderState)"),
			"Rust source semantics must retain the actual Shulker Bullet entity identity");
		assertEquals(2, occurrences(renderer, "RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible("),
			"both the cutout base and translucent shell must be admitted explicitly");
		assertTrue(renderer.contains("shulkerBulletRenderState.outlineColor"),
			"outline state must participate in admission instead of being silently discarded");
		assertTrue(renderer.contains("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()"));
		assertTrue(renderer.contains("StandaloneModelRenderOwnershipPolicy.classify("));
		assertFalse(renderer.contains("currentModelMeshRoute(rustShulkerBulletEligible)"),
			"current-state eligibility must not choose the owner");

		int unavailable = renderer.indexOf("Disposition.RUST_UNAVAILABLE");
		int javaBase = renderer.indexOf("submitNodeCollector.submitModel(", unavailable);
		int javaShell = renderer.indexOf("submitNodeCollector.order(1)", javaBase);
		assertTrue(unavailable >= 0 && javaBase > unavailable && javaShell > javaBase);
		assertFalse(renderer.substring(unavailable, javaBase).contains("submitNodeCollector.submitModel("),
			"a Rust-owned unavailable Shulker Bullet must not escape through the Java base pass");
		assertFalse(renderer.substring(unavailable, javaBase).contains("submitNodeCollector.order(1)"),
			"a Rust-owned unavailable Shulker Bullet must not escape through the Java glow pass");
		assertTrue(renderer.contains("\"rust-vulkan-unavailable\""));
	}

	@Test
	void outerSpinScaleLightAndGlowTintRemainSemanticInputs() throws Exception {
		String renderer = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/entity/ShulkerBulletRenderer.java"
		));

		assertTrue(renderer.contains("Mth.sin(f * 0.1F) * 180.0F"));
		assertTrue(renderer.contains("Mth.cos(f * 0.1F) * 180.0F"));
		assertTrue(renderer.contains("Mth.sin(f * 0.15F) * 360.0F"));
		assertTrue(renderer.contains("poseStack.scale(-0.5F, -0.5F, 0.5F);"));
		assertTrue(renderer.contains("shulkerBulletRenderState.lightCoords"));
		assertTrue(renderer.contains("GLOW_TINT"));
		assertTrue(renderer.contains("poseStack.last()"),
			"Rust must receive the completed Java semantic transform rather than reconstructing renderer motion");
	}

	private static int occurrences(String source, String needle) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
