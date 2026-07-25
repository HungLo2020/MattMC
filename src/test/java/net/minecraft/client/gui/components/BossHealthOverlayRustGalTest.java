package net.minecraft.client.gui.components;

import net.minecraft.world.BossEvent.BossBarOverlay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossHealthOverlayRustGalTest {
	@Test
	void deterministicBossBarsPreserveCountProgressOverlayAndOrder() {
		BossHealthOverlay overlay = new BossHealthOverlay(null);

		overlay.replaceEventsForDeterministicCapture(3, new float[]{0.0F, 0.5F, 1.0F}, new BossBarOverlay[]{
			BossBarOverlay.PROGRESS,
			BossBarOverlay.NOTCHED_10,
			BossBarOverlay.NOTCHED_20
		});

		assertEquals(3, overlay.events.size());
		LerpingBossEvent[] events = overlay.events.values().toArray(LerpingBossEvent[]::new);
		assertEquals(0.0F, events[0].getProgress(), 0.0001F);
		assertEquals(BossBarOverlay.PROGRESS, events[0].getOverlay());
		assertEquals(0.5F, events[1].getProgress(), 0.0001F);
		assertEquals(BossBarOverlay.NOTCHED_10, events[1].getOverlay());
		assertEquals(1.0F, events[2].getProgress(), 0.0001F);
		assertEquals(BossBarOverlay.NOTCHED_20, events[2].getOverlay());
	}

	@Test
	void deterministicZeroBossBarsClearsOverlay() {
		BossHealthOverlay overlay = new BossHealthOverlay(null);

		overlay.replaceEventsForDeterministicCapture(2, new float[]{1.0F}, new BossBarOverlay[]{BossBarOverlay.NOTCHED_6});
		overlay.replaceEventsForDeterministicCapture(0, new float[]{1.0F}, new BossBarOverlay[]{BossBarOverlay.NOTCHED_6});

		assertEquals(0, overlay.events.size());
	}
}
