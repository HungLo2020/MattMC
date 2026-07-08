package net.minecraft.world.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AnvilMenuCostTest {
	private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

	@Test
	void repairCostClampNeverExceedsFortyLevels() {
		assertEquals(0, AnvilMenu.clampRepairCost(-1L));
		assertEquals(1, AnvilMenu.clampRepairCost(1L));
		assertEquals(39, AnvilMenu.clampRepairCost(39L));
		assertEquals(40, AnvilMenu.clampRepairCost(40L));
		assertEquals(40, AnvilMenu.clampRepairCost(41L));
		assertEquals(40, AnvilMenu.clampRepairCost(Integer.MAX_VALUE + 1L));
	}

	@Test
	void tooExpensiveAnvilSurfaceIsRemoved() throws IOException {
		String anvilMenu = read("src/main/java/net/minecraft/world/inventory/AnvilMenu.java");
		String anvilScreen = read("src/main/java/net/minecraft/client/gui/screens/inventory/AnvilScreen.java");
		String englishLanguage = read("src/main/resources/assets/minecraft/lang/en_us.json");

		assertTrue(anvilMenu.contains("clampRepairCost(l + i)"), "anvil cost should be capped through the repair cost clamp");
		assertFalse(anvilMenu.contains("this.cost.get() >= 40"), "anvil output should not be removed at level 40");
		assertFalse(anvilScreen.contains("TOO_EXPENSIVE_TEXT"), "client should not keep a Too Expensive label constant");
		assertFalse(anvilScreen.contains("container.repair.expensive"), "client should not reference the removed translation key");
		assertFalse(englishLanguage.contains("container.repair.expensive"), "language file should not define the removed translation key");
		assertFalse(englishLanguage.contains("Too Expensive!"), "language file should not define the removed Too Expensive text");
	}

	private static String read(String path) throws IOException {
		return Files.readString(PROJECT_ROOT.resolve(path));
	}
}
