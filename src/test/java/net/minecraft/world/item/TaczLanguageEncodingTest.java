package net.minecraft.world.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczLanguageEncodingTest {
	private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
	private static final Path LANG_FILE = PROJECT_ROOT.resolve("src/main/resources/assets/minecraft/lang/en_us.json");
	private static final List<String> MOJIBAKE_MARKERS = List.of("\u00c3", "\u00c2", "\ufffd");

	@Test
	void importedTaczLanguageValuesDoNotExposeMojibake() throws IOException {
		JsonObject lang = readLangFile();
		List<String> badValues = new ArrayList<>();

		for (Map.Entry<String, JsonElement> entry : lang.entrySet()) {
			if (isImportedTaczKey(entry.getKey())) {
				String value = entry.getValue().getAsString();
				if (containsMojibake(value)) {
					badValues.add(entry.getKey() + "=" + value);
				}
			}
		}

		assertTrue(badValues.isEmpty(), () -> "TaCZ language values contain mojibake: " + badValues);
	}

	@Test
	void registeredTaczAttachmentItemNamesDoNotExposeMojibake() throws IOException {
		JsonObject lang = readLangFile();
		List<String> badAttachmentNames = new ArrayList<>();

		for (TaczGunDefinitions.Attachment attachment : TaczGunDefinitions.ATTACHMENTS) {
			String key = "item.minecraft." + attachment.id();
			JsonElement value = lang.get(key);
			if (value == null || containsMojibake(value.getAsString())) {
				badAttachmentNames.add(key);
			}
		}

		assertTrue(badAttachmentNames.isEmpty(), () -> "TaCZ attachment item names are missing or mojibake-corrupt: " + badAttachmentNames);
	}

	private static JsonObject readLangFile() throws IOException {
		return JsonParser.parseString(Files.readString(LANG_FILE)).getAsJsonObject();
	}

	private static boolean isImportedTaczKey(String key) {
		return key.startsWith("tacz.") || key.startsWith("minecraft.") || key.startsWith("item.minecraft.");
	}

	private static boolean containsMojibake(String value) {
		for (String marker : MOJIBAKE_MARKERS) {
			if (value.contains(marker)) {
				return true;
			}
		}
		return false;
	}
}
