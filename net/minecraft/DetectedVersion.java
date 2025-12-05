package net.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.DataVersion;
import org.slf4j.Logger;

public class DetectedVersion {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final WorldVersion BUILT_IN = createBuiltIn(UUID.randomUUID().toString().replaceAll("-", ""), "Development Version");

	public static WorldVersion createBuiltIn(String string, String string2) {
		return createBuiltIn(string, string2, true);
	}

	public static WorldVersion createBuiltIn(String string, String string2, boolean bl) {
		return new WorldVersion.Simple(
			string, string2, new DataVersion(4556, "main"), SharedConstants.getProtocolVersion(), PackFormat.of(69, 0), PackFormat.of(88, 0), new Date(), bl
		);
	}

	private static WorldVersion createFromJson(JsonObject jsonObject) {
		JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "pack_version");
		return new WorldVersion.Simple(
			GsonHelper.getAsString(jsonObject, "id"),
			GsonHelper.getAsString(jsonObject, "name"),
			new DataVersion(GsonHelper.getAsInt(jsonObject, "world_version"), GsonHelper.getAsString(jsonObject, "series_id", "main")),
			GsonHelper.getAsInt(jsonObject, "protocol_version"),
			PackFormat.of(GsonHelper.getAsInt(jsonObject2, "resource_major"), GsonHelper.getAsInt(jsonObject2, "resource_minor")),
			PackFormat.of(GsonHelper.getAsInt(jsonObject2, "data_major"), GsonHelper.getAsInt(jsonObject2, "data_minor")),
			Date.from(ZonedDateTime.parse(GsonHelper.getAsString(jsonObject, "build_time")).toInstant()),
			GsonHelper.getAsBoolean(jsonObject, "stable")
		);
	}

	public static WorldVersion tryDetectVersion() {
		try {
			InputStream inputStream = DetectedVersion.class.getResourceAsStream("/version.json");

			WorldVersion var9;
			label63: {
				WorldVersion var2;
				try {
					if (inputStream == null) {
						LOGGER.warn("Missing version information!");
						var9 = BUILT_IN;
						break label63;
					}

					InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

					try {
						var2 = createFromJson(GsonHelper.parse(inputStreamReader));
					} catch (Throwable var6) {
						try {
							inputStreamReader.close();
						} catch (Throwable var5) {
							var6.addSuppressed(var5);
						}

						throw var6;
					}

					inputStreamReader.close();
				} catch (Throwable var7) {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (Throwable var4) {
							var7.addSuppressed(var4);
						}
					}

					throw var7;
				}

				if (inputStream != null) {
					inputStream.close();
				}

				return var2;
			}

			if (inputStream != null) {
				inputStream.close();
			}

			return var9;
		} catch (JsonParseException | IOException var8) {
			throw new IllegalStateException("Game version information is corrupt", var8);
		}
	}
}
