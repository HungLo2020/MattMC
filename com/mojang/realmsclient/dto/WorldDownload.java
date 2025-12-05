package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class WorldDownload extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	public String downloadLink;
	public String resourcePackUrl;
	public String resourcePackHash;

	public static WorldDownload parse(String string) {
		JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
		WorldDownload worldDownload = new WorldDownload();

		try {
			worldDownload.downloadLink = JsonUtils.getStringOr("downloadLink", jsonObject, "");
			worldDownload.resourcePackUrl = JsonUtils.getStringOr("resourcePackUrl", jsonObject, "");
			worldDownload.resourcePackHash = JsonUtils.getStringOr("resourcePackHash", jsonObject, "");
		} catch (Exception var4) {
			LOGGER.error("Could not parse WorldDownload: {}", var4.getMessage());
		}

		return worldDownload;
	}
}
