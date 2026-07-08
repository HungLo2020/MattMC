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

class StayTrueWheatResourceTest {
	private static final Path ASSETS = Paths.get(System.getProperty("user.dir"), "src/main/resources/assets/minecraft");

	@Test
	void wheatBlockstateUsesStayTrueLateGrowthVariants() throws IOException {
		JsonObject variants = loadJson(ASSETS.resolve("blockstates/wheat.json")).getAsJsonObject("variants");

		for (int age = 0; age <= 7; age++) {
			assertTrue(variants.has("age=" + age), "wheat blockstate must define age " + age);
		}

		assertWeightedVariants(variants, 5);
		assertWeightedVariants(variants, 6);
		assertWeightedVariants(variants, 7);
	}

	@Test
	void stayTrueWheatModelsResolveAllTexturesAndKeepTallCropGeometry() throws IOException {
		Set<String> models = modelsReferencedByWheatBlockstate();
		assertTrue(models.contains("minecraft:block/wheat_stage5_1"), "age 5 should include the tall split wheat variant");
		assertTrue(models.contains("minecraft:block/wheat_stage6_4"), "age 6 should include all Stay True weighted variants");
		assertTrue(models.contains("minecraft:block/wheat_stage7_4"), "age 7 should include all Stay True weighted variants");

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

		assertTrue(foundTallSplitModel, "Stay True wheat needs the two-block-tall split top/bottom crop models");
	}

	private static void assertWeightedVariants(JsonObject variants, int age) {
		JsonElement entry = variants.get("age=" + age);
		assertTrue(entry.isJsonArray(), "age " + age + " should use weighted Stay True model variants");
		assertEquals(5, entry.getAsJsonArray().size(), "age " + age + " should have five Stay True variants");

		int weight = 0;
		for (JsonElement variant : entry.getAsJsonArray()) {
			JsonObject object = variant.getAsJsonObject();
			assertTrue(object.has("weight"), "age " + age + " variant should define a weight");
			weight += object.get("weight").getAsInt();
		}
		assertEquals(20, weight, "age " + age + " weights should match Stay True");
	}

	private static Set<String> modelsReferencedByWheatBlockstate() throws IOException {
		JsonObject variants = loadJson(ASSETS.resolve("blockstates/wheat.json")).getAsJsonObject("variants");
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
		assertTrue(model.startsWith("minecraft:block/"), "wheat model should be in the minecraft block namespace: " + model);
		return ASSETS.resolve("models/block/" + model.substring("minecraft:block/".length()) + ".json");
	}

	private static Path texturePath(String texture) {
		assertTrue(texture.startsWith("minecraft:block/"), "wheat texture should be in the minecraft block namespace: " + texture);
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
