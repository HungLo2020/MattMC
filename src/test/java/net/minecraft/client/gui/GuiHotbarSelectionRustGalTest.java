package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiHotbarSelectionRustGalTest {
	@Test
	void selectedHotbarHighlightPositionsCoverAllNineSlots() {
		int guiWidth = 320;
		int guiHeight = 180;

		for (int slot = 0; slot < 9; slot++) {
			assertEquals(68 + slot * 20, Gui.selectedHotbarHighlightX(guiWidth, slot));
		}
		assertEquals(157, Gui.selectedHotbarHighlightY(guiHeight));
	}
}
