package net.minecraft.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StayTruePotatoesResourceTest {
	private static final Path ASSETS = Paths.get(System.getProperty("user.dir"), "src/main/resources/assets/minecraft");

	@Test
	void potatoesBlockstateUsesStayTrueMatureGrowthVariants() throws IOException {
		JsonObject variants = loadJson(ASSETS.resolve("blockstates/potatoes.json")).getAsJsonObject("variants");

		assertModel(variants, "age=0", "minecraft:block/potatoes_stage0");
		assertModel(variants, "age=1", "minecraft:block/potatoes_stage0");
		assertModel(variants, "age=2", "minecraft:block/potatoes_stage1");
		assertModel(variants, "age=3", "minecraft:block/potatoes_stage1");
		assertModel(variants, "age=4", "minecraft:block/potatoes_stage2");
		assertModel(variants, "age=5", "minecraft:block/potatoes_stage2");
		assertModel(variants, "age=6", "minecraft:block/potatoes_stage2");

		JsonElement mature = variants.get("age=7");
		assertTrue(mature != null && mature.isJsonArray(), "mature potatoes should use weighted Stay True model variants");
		assertEquals(7, mature.getAsJsonArray().size(), "mature potatoes should have seven Stay True variants");

		int totalWeight = 0;
		Set<String> models = new HashSet<>();
		for (JsonElement variant : mature.getAsJsonArray()) {
			JsonObject object = variant.getAsJsonObject();
			totalWeight += object.get("weight").getAsInt();
			models.add(object.get("model").getAsString());
		}
		assertEquals(24, totalWeight, "mature potato weights should match Stay True");
		assertTrue(models.contains("minecraft:block/potatoes_stage3_3"), "mature potatoes should include tall split variant 3");
		assertTrue(models.contains("minecraft:block/potatoes_stage3_4"), "mature potatoes should include tall split variant 4");
		assertTrue(models.contains("minecraft:block/potatoes_stage3_6"), "mature potatoes should include tall split variant 6");
	}

	@Test
	void stayTruePotatoModelsResolveAllTexturesAndKeepTallCropGeometry() throws IOException {
		Set<String> models = modelsReferencedByPotatoesBlockstate();
		boolean foundTallSplitModel = false;

		for (String model : models) {
			JsonObject modelJson = loadJson(modelPath(model));
			JsonObject textures = modelJson.getAsJsonObject("textures");
			assertTrue(textures != null, model + " must define textures");
			for (Map.Entry<String, JsonElement> texture : textures.entrySet()) {
				String textureName = texture.getValue().getAsString();
				if (!textureName.startsWith("#")) {
					assertResourceExists(texturePath(textureName), model + " references missing texture " + textureName);
				}
			}

			if (modelJson.has("elements")) {
				float maxY = Float.NEGATIVE_INFINITY;
				for (JsonElement element : modelJson.getAsJsonArray("elements")) {
					JsonObject object = element.getAsJsonObject();
					for (JsonElement coordinate : object.getAsJsonArray("from")) {
						float value = coordinate.getAsFloat();
						assertTrue(value >= -16.0F && value <= 32.0F, model + " has an out-of-range from coordinate: " + value);
					}
					for (JsonElement coordinate : object.getAsJsonArray("to")) {
						float value = coordinate.getAsFloat();
						assertTrue(value >= -16.0F && value <= 32.0F, model + " has an out-of-range to coordinate: " + value);
					}
					maxY = Math.max(maxY, object.getAsJsonArray("to").get(1).getAsFloat());
				}
				if (textures.has("bottom") && textures.has("top") && maxY == 32.0F) {
					foundTallSplitModel = true;
				}
			}
		}

		assertTrue(foundTallSplitModel, "Stay True potatoes need the two-block-tall split top/bottom mature models");
	}

	private static void assertModel(JsonObject variants, String age, String expectedModel) {
		JsonElement variant = variants.get(age);
		assertTrue(variant != null && variant.isJsonObject(), age + " should use a single potato model");
		assertEquals(expectedModel, variant.getAsJsonObject().get("model").getAsString());
	}

	private static Set<String> modelsReferencedByPotatoesBlockstate() throws IOException {
		JsonObject variants = loadJson(ASSETS.resolve("blockstates/potatoes.json")).getAsJsonObject("variants");
		Set<String> models = new HashSet<>();
		for (JsonElement variant : variants.asMap().values()) {
			if (variant.isJsonArray()) {
				for (JsonElement entry : variant.getAsJsonArray()) {
					models.add(entry.getAsJsonObject().get("model").getAsString());
				}
			} else {
				models.add(variant.getAsJsonObject().get("model").getAsString());
			}
		}
		return models;
	}

	private static Path modelPath(String model) {
		assertTrue(model.startsWith("minecraft:block/"), "potato model should be in the minecraft block namespace: " + model);
		return ASSETS.resolve("models/block/" + model.substring("minecraft:block/".length()) + ".json");
	}

	private static Path texturePath(String texture) {
		assertTrue(texture.startsWith("minecraft:block/"), "potato texture should be in the minecraft block namespace: " + texture);
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
