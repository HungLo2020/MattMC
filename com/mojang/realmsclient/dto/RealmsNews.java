package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsNews extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Nullable
	public String newsLink;

	public static RealmsNews parse(String string) {
		RealmsNews realmsNews = new RealmsNews();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			realmsNews.newsLink = JsonUtils.getStringOr("newsLink", jsonObject, null);
		} catch (Exception var3) {
			LOGGER.error("Could not parse RealmsNews: {}", var3.getMessage());
		}

		return realmsNews;
	}
}
