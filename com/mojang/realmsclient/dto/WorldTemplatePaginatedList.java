package com.mojang.realmsclient.dto;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class WorldTemplatePaginatedList extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	public List<WorldTemplate> templates;
	public int page;
	public int size;
	public int total;

	public WorldTemplatePaginatedList() {
	}

	public WorldTemplatePaginatedList(int i) {
		this.templates = Collections.emptyList();
		this.page = 0;
		this.size = i;
		this.total = -1;
	}

	public boolean isLastPage() {
		return this.page * this.size >= this.total && this.page > 0 && this.total > 0 && this.size > 0;
	}

	public static WorldTemplatePaginatedList parse(String string) {
		WorldTemplatePaginatedList worldTemplatePaginatedList = new WorldTemplatePaginatedList();
		worldTemplatePaginatedList.templates = Lists.<WorldTemplate>newArrayList();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			if (jsonObject.get("templates").isJsonArray()) {
				for (JsonElement jsonElement : jsonObject.get("templates").getAsJsonArray()) {
					worldTemplatePaginatedList.templates.add(WorldTemplate.parse(jsonElement.getAsJsonObject()));
				}
			}

			worldTemplatePaginatedList.page = JsonUtils.getIntOr("page", jsonObject, 0);
			worldTemplatePaginatedList.size = JsonUtils.getIntOr("size", jsonObject, 0);
			worldTemplatePaginatedList.total = JsonUtils.getIntOr("total", jsonObject, 0);
		} catch (Exception var5) {
			LOGGER.error("Could not parse WorldTemplatePaginatedList: {}", var5.getMessage());
		}

		return worldTemplatePaginatedList;
	}
}
