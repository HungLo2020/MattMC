package net.minecraft.client.gui.contextualbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceBarRustGalTest {
	@Test
	void filledWidthMatchesVanillaEmptyPartialAndFullProgress() {
		assertEquals(0, ExperienceBarRenderer.filledWidth(0.0F));
		assertEquals(91, ExperienceBarRenderer.filledWidth(0.5F));
		assertEquals(183, ExperienceBarRenderer.filledWidth(1.0F));
	}
}
