package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiAttackIndicatorRustGalTest {
	@Test
	void crosshairAttackIndicatorFilledWidthMatchesVanilla() {
		assertEquals(0, Gui.crosshairAttackIndicatorFilledWidth(0.0F));
		assertEquals(8, Gui.crosshairAttackIndicatorFilledWidth(0.5F));
		assertEquals(16, Gui.crosshairAttackIndicatorFilledWidth(0.99F));
	}

	@Test
	void hotbarAttackIndicatorFilledHeightMatchesVanilla() {
		assertEquals(0, Gui.hotbarAttackIndicatorFilledHeight(0.0F));
		assertEquals(9, Gui.hotbarAttackIndicatorFilledHeight(0.5F));
		assertEquals(18, Gui.hotbarAttackIndicatorFilledHeight(0.99F));
	}
}
