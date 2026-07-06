package net.minecraft.client.tacz;

import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class TaczMuzzleFlashData {
	private static final Map<String, TaczMuzzleFlashData> CACHE = new ConcurrentHashMap<>();
	private final ResourceLocation texture;
	private final float scale;

	private TaczMuzzleFlashData(ResourceLocation texture, float scale) {
		this.texture = texture;
		this.scale = scale;
	}

	public static TaczMuzzleFlashData get(String gunId) {
		return CACHE.computeIfAbsent(gunId, TaczMuzzleFlashData::load);
	}

	private static TaczMuzzleFlashData load(String gunId) {
		ResourceLocation displayLocation = ResourceLocation.withDefaultNamespace("display/guns/" + gunId + "_display.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(displayLocation)) {
			String json = stripLineComments(readAll(reader));
			JsonObject display = GsonHelper.parse(new StringReader(json));
			if (!display.has("muzzle_flash") || !display.get("muzzle_flash").isJsonObject()) {
				return null;
			}
			JsonObject muzzleFlash = display.getAsJsonObject("muzzle_flash");
			ResourceLocation texture = textureLocation(ResourceLocation.parse(GsonHelper.getAsString(muzzleFlash, "texture")));
			float scale = GsonHelper.getAsFloat(muzzleFlash, "scale", 1.0F);
			return new TaczMuzzleFlashData(texture, scale);
		} catch (Exception exception) {
			return null;
		}
	}

	private static ResourceLocation textureLocation(ResourceLocation location) {
		String path = location.getPath();
		if (path.startsWith("textures/") && path.endsWith(".png")) {
			return location;
		}
		return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "textures/" + path + ".png");
	}

	private static String readAll(Reader reader) throws java.io.IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[2048];
		int read;
		while ((read = reader.read(buffer)) >= 0) {
			builder.append(buffer, 0, read);
		}
		return builder.toString();
	}

	private static String stripLineComments(String value) {
		StringBuilder builder = new StringBuilder(value.length());
		boolean inString = false;
		boolean escaped = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (escaped) {
				builder.append(c);
				escaped = false;
				continue;
			}
			if (c == '\\' && inString) {
				builder.append(c);
				escaped = true;
				continue;
			}
			if (c == '"') {
				inString = !inString;
				builder.append(c);
				continue;
			}
			if (!inString && c == '/' && i + 1 < value.length() && value.charAt(i + 1) == '/') {
				while (i < value.length() && value.charAt(i) != '\n') {
					i++;
				}
				if (i < value.length()) {
					builder.append('\n');
				}
				continue;
			}
			builder.append(c);
		}
		return builder.toString();
	}

	public ResourceLocation texture() {
		return this.texture;
	}

	public float scale() {
		return this.scale;
	}
}
