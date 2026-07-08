package net.minecraft.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StayTrueLeavesResourceTest {
	private static final Path ASSETS = Paths.get(System.getProperty("user.dir"), "src/main/resources/assets/minecraft");
	private static final Map<String, Integer> EXPECTED_VARIANTS = Map.of(
		"acacia_leaves", 2,
		"azalea_leaves", 2,
		"birch_leaves", 2,
		"cherry_leaves", 2,
		"dark_oak_leaves", 3,
		"flowering_azalea_leaves", 2,
		"jungle_leaves", 2,
		"mangrove_leaves", 2,
		"oak_leaves", 3,
		"spruce_leaves", 2
	);

	@Test
	void stayTrueLeafBlockstatesUseDistanceVariantsAndBushyModels() throws IOException {
		Set<String> seenModels = new HashSet<>();
		for (Map.Entry<String, Integer> leaf : EXPECTED_VARIANTS.entrySet()) {
			JsonObject variants = loadJson(ASSETS.resolve("blockstates/" + leaf.getKey() + ".json")).getAsJsonObject("variants");
			for (int distance = 1; distance <= 7; distance++) {
				JsonElement variant = variants.get("distance=" + distance);
				assertTrue(variant != null && variant.isJsonArray(), leaf.getKey() + " distance " + distance + " should use Stay True random variants");
				assertEquals(leaf.getValue(), variant.getAsJsonArray().size(), leaf.getKey() + " distance " + distance + " variant count");
				for (JsonElement model : variant.getAsJsonArray()) {
					String modelName = model.getAsJsonObject().get("model").getAsString();
					assertTrue(modelName.startsWith("minecraft:block/"), leaf.getKey() + " should use explicit minecraft model ids");
					seenModels.add(modelName);
				}
			}
		}

		for (String model : seenModels) {
			assertBushyModelResolves(model);
		}
	}

	@Test
	void birchLeavesUseStayTrueColoredUntintedBushyVariants() throws IOException {
		JsonObject birch = loadJson(ASSETS.resolve("models/block/birch_leaves.json"));
		JsonObject birchAlternate = loadJson(ASSETS.resolve("models/block/birch_leaves1.json"));
		JsonObject birchCross = loadJson(ASSETS.resolve("models/block/leaves_cross_birch.json"));

		assertEquals("minecraft:block/leaves_cross_birch", birch.get("parent").getAsString());
		assertEquals("minecraft:block/leaves_cross_birch", birchAlternate.get("parent").getAsString());
		assertEquals("minecraft:block/birch_leaves", birch.getAsJsonObject("textures").get("all").getAsString());
		assertEquals("minecraft:block/birch_leaves_bushy", birch.getAsJsonObject("textures").get("bushy").getAsString());
		assertEquals("minecraft:block/birch_leaves_bushy1", birchAlternate.getAsJsonObject("textures").get("bushy").getAsString());
		assertFalse(birchCross.toString().contains("tintindex"),
			"Stay True birch leaves are pre-colored textures and should not use biome tint indexes");
	}

	@Test
	void stayTrueLeafInventoryOverridesArePreservedInCurrentItemModelFormat() throws IOException {
		assertItemModel("acacia_leaves", "minecraft:block/acacia_leaves_inventory");
		assertItemModel("mangrove_leaves", "minecraft:block/mangrove_leaves_inventory");
		assertBushyModelResolves("minecraft:block/acacia_leaves_inventory");
		assertBushyModelResolves("minecraft:block/mangrove_leaves_inventory");
	}

	private static void assertBushyModelResolves(String modelName) throws IOException {
		JsonObject model = loadJson(modelPath(modelName));
		JsonObject parent = model.has("parent") ? loadJson(modelPath(model.get("parent").getAsString())) : null;
		Map<String, String> textures = new HashMap<>();
		if (parent != null && parent.has("textures")) {
			parent.getAsJsonObject("textures").asMap().forEach((key, value) -> textures.put(key, value.getAsString()));
		}
		if (model.has("textures")) {
			model.getAsJsonObject("textures").asMap().forEach((key, value) -> textures.put(key, value.getAsString()));
		}

		assertTrue(textures.containsKey("all"), modelName + " should provide the base leaf cube texture");
		assertTrue(textures.containsKey("bushy"), modelName + " should provide the bushy leaf overlay texture");
		for (String texture : textures.values()) {
			if (!texture.startsWith("#")) {
				assertResourceExists(texturePath(texture), modelName + " references missing texture " + texture);
			}
		}

		JsonArray elements = parent != null && parent.has("elements") ? parent.getAsJsonArray("elements") : model.getAsJsonArray("elements");
		assertTrue(hasBushyElement(elements), modelName + " should inherit or define extra bushy leaf planes");
	}

	private static boolean hasBushyElement(JsonArray elements) {
		for (JsonElement element : elements) {
			JsonObject object = element.getAsJsonObject();
			boolean outsideUnitCube = false;
			for (JsonElement coordinate : object.getAsJsonArray("from")) {
				float value = coordinate.getAsFloat();
				assertTrue(value >= -16.0F && value <= 32.0F, "leaf model from coordinate escaped vanilla bounds: " + value);
				outsideUnitCube |= value < 0.0F || value > 16.0F;
			}
			for (JsonElement coordinate : object.getAsJsonArray("to")) {
				float value = coordinate.getAsFloat();
				assertTrue(value >= -16.0F && value <= 32.0F, "leaf model to coordinate escaped vanilla bounds: " + value);
				outsideUnitCube |= value < 0.0F || value > 16.0F;
			}
			if (outsideUnitCube && object.getAsJsonObject("faces").toString().contains("\"#bushy\"")) {
				return true;
			}
		}
		return false;
	}

	private static void assertItemModel(String item, String expectedModel) throws IOException {
		JsonObject root = loadJson(ASSETS.resolve("items/" + item + ".json"));
		assertEquals(expectedModel, root.getAsJsonObject("model").get("model").getAsString());
	}

	private static Path modelPath(String model) {
		assertTrue(model.startsWith("minecraft:block/"), "model should be in the minecraft block namespace: " + model);
		return ASSETS.resolve("models/block/" + model.substring("minecraft:block/".length()) + ".json");
	}

	private static Path texturePath(String texture) {
		assertTrue(texture.startsWith("minecraft:block/"), "texture should be in the minecraft block namespace: " + texture);
		return ASSETS.resolve("textures/block/" + texture.substring("minecraft:block/".length()) + ".png");
	}

	private static JsonObject loadJson(Path path) throws IOException {
		assertResourceExists(path, path + " should exist");
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static void assertResourceExists(Path path, String message) {
		assertTrue(Files.isRegularFile(path), message);
	}
}
