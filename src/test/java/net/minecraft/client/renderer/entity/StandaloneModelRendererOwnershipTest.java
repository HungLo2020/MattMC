package net.minecraft.client.renderer.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StandaloneModelRendererOwnershipTest {
	private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
	private static final List<String> PRODUCERS = List.of(
		"LlamaSpitRenderer.java",
		"EvokerFangsRenderer.java",
		"WitherSkullRenderer.java"
	);

	@Test
	void dedicatedStandaloneModelProducersResolveOwnershipBeforeAdmission() throws Exception {
		for (String producer : PRODUCERS) {
			String source = Files.readString(PROJECT_ROOT.resolve(
				"src/main/java/net/minecraft/client/renderer/entity/" + producer
			));
			int eligibility = source.indexOf("isStandaloneModelMeshEligible(");
			int ownership = source.indexOf("StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute()", eligibility);
			int classification = source.indexOf("StandaloneModelRenderOwnershipPolicy.classify(", ownership);
			int unavailable = source.indexOf("Disposition.RUST_UNAVAILABLE", classification);
			int javaSubmit = source.indexOf("submitNodeCollector.submitModel(", unavailable);

			assertTrue(eligibility >= 0, producer + " must retain bounded semantic eligibility");
			assertTrue(ownership > eligibility, producer + " must resolve callsite ownership independently of eligibility");
			assertTrue(classification > ownership, producer + " must classify availability against ownership");
			assertTrue(unavailable > classification, producer + " must explicitly handle Rust-unavailable state");
			assertTrue(javaSubmit > unavailable, producer + " Java submit must remain outside the Rust-unavailable branch");
			assertTrue(source.contains("\"rust-vulkan-unavailable\""), producer + " must record unavailable Rust state");
			assertFalse(
				source.substring(unavailable, javaSubmit).contains("enqueueStandaloneModelMesh("),
				producer + " unavailable branch must not enqueue an admitted Rust mesh"
			);
			assertFalse(
				source.contains("WorldRenderRoutePolicy.currentModelMeshRoute(eligible)"),
				producer + " ownership must not be derived from per-state admission"
			);
		}
	}
}
