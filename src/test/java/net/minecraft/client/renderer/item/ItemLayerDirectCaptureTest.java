package net.minecraft.client.renderer.item;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.TridentSpecialRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A direct GUI capture (the inventory entity preview) must receive held item
 * layers itself: plain items through submitItem, special renderers through
 * their model callbacks, with no world-owned Rust queueing or receipt check.
 */
class ItemLayerDirectCaptureTest {
	@org.junit.jupiter.api.BeforeAll static void bootstrap() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@Test void directCaptureReceivesPlainAndSpecialItemLayers() {
		String property = "mattmc.dev.rustGalVulkanWholeFrame";
		String previous = System.getProperty(property);
		System.setProperty(property, "true");
		try {
			List<String> calls = new ArrayList<>();
			var collector = (SubmitNodeCollector) Proxy.newProxyInstance(
				SubmitNodeCollector.class.getClassLoader(), new Class<?>[]{SubmitNodeCollector.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "capturesItemLayersDirectly" -> true;
					case "isSemanticCoverageOnly" -> false;
					case "submitItemSemantic", "submitModelSemanticTexture", "submitModelPartSemantic" -> {
						calls.add(method.getName());
						yield null;
					}
					default -> throw new AssertionError("unexpected submission: " + method.getName());
				});

			var state = new ItemStackRenderState();
			state.displayContext = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
			var plain = state.newLayer();
			plain.setRenderType(RenderType.cutoutMipped());
			var special = state.newLayer();
			special.setupSpecialModel(new TridentSpecialRenderer(new TridentModel(TridentModel.createLayer().bakeRoot())), null);

			var pose = new PoseStack();
			state.submit(pose, collector, 15728880, 0, 0);

			assertEquals("submitItemSemantic", calls.getFirst(), "plain layer goes to the capture's item callback");
			assertTrue(calls.size() >= 2 && calls.subList(1, calls.size()).stream().allMatch(name -> name.startsWith("submitModel")),
				"special renderer submits its model to the capture: " + calls);
			assertTrue(pose.isEmpty());
		} finally {
			if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
		}
	}

	@Test void collectorsDefaultToWorldOwnedItemLayers() {
		var collector = (SubmitNodeCollector) Proxy.newProxyInstance(
			SubmitNodeCollector.class.getClassLoader(), new Class<?>[]{SubmitNodeCollector.class},
			(proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
		assertFalse(collector.capturesItemLayersDirectly());
	}
}
