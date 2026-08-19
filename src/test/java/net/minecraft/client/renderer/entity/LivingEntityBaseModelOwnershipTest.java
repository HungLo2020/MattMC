package net.minecraft.client.renderer.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LivingEntityBaseModelOwnershipTest {
	@Test
	void migratedBaseModelFamilyResolvesOwnershipBeforeAdmission() throws Exception {
		String source = Files.readString(Path.of(
			System.getProperty("user.dir"),
			"src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java"
		));

		int family = source.indexOf("boolean rustLivingModelFamily =");
		int eligibility = source.indexOf("boolean rustLivingModelEligible =", family);
		int ownership = source.indexOf("LivingEntityBaseModelOwnershipPolicy.currentOwnershipRoute(rustLivingModelFamily)", eligibility);
		int classification = source.indexOf("LivingEntityBaseModelOwnershipPolicy.classify(", ownership);
		int available = source.indexOf("Disposition.RUST_AVAILABLE", classification);
		int enqueue = source.indexOf("RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(", available);
		int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", enqueue);
		int javaSubmit = source.indexOf("submitNodeCollector.submitModel(", unavailable);
		int layerLoop = source.indexOf("if (this.shouldRenderLayers", javaSubmit);

		assertTrue(family >= 0, "living renderer must identify the bounded migrated base-model family independently");
		assertTrue(eligibility > family, "per-state eligibility must remain separate from family ownership");
		assertTrue(ownership > eligibility, "base-model ownership must be resolved independently of eligibility result");
		assertTrue(classification > ownership, "availability must be classified against resolved ownership");
		assertTrue(available > classification, "admitted Rust base-model state must be explicit");
		assertTrue(enqueue > available, "only admitted migrated base models may enqueue Rust mesh work");
		assertTrue(unavailable > enqueue, "unsupported Rust-owned base-model state must be explicit");
		assertTrue(javaSubmit > unavailable, "Java base-model submit must remain outside the Rust-unavailable branch");
		assertTrue(layerLoop > javaSubmit, "feature layers must remain a distinct later migration surface");
		assertTrue(source.contains("\"rust-vulkan-unavailable\""), "unavailable migrated base-model state must be observable");
		assertFalse(
			source.substring(unavailable, javaSubmit).contains("submitNodeCollector.submitModel("),
			"Rust-unavailable migrated base models must not fall through to Java base-model submission"
		);
		assertFalse(
			source.contains("WorldRenderRoutePolicy.currentModelMeshRoute(rustLivingModelEligible)"),
			"migrated living base-model ownership must not be derived from per-state eligibility"
		);
	}

	@Test
	void familyBoundaryNamesOnlyAlreadyMigratedVanillaModels() throws Exception {
		String source = Files.readString(Path.of(
			System.getProperty("user.dir"),
			"src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java"
		));
		int family = source.indexOf("boolean rustLivingModelFamily =");
		int eligibility = source.indexOf("boolean rustLivingModelEligible =", family);
		String familyRegion = source.substring(family, eligibility);

		assertTrue(familyRegion.contains("ChickenModel.class"));
		assertTrue(familyRegion.contains("CowModel"));
		assertTrue(familyRegion.contains("PigModel.class"));
		assertTrue(familyRegion.contains("ZombieModel.class"));
		assertTrue(familyRegion.contains("RabbitModel.class"));
		assertFalse(familyRegion.contains("HumanoidModel"), "generic humanoids are not admitted by this milestone");
	}
}
