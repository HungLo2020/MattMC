package com.mojang.realmsclient.dto;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;

@Environment(EnvType.CLIENT)
public class Ops extends ValueObject {
	public Set<String> ops = Sets.<String>newHashSet();

	public static Ops parse(String string) {
		Ops ops = new Ops();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			JsonElement jsonElement = jsonObject.get("ops");
			if (jsonElement.isJsonArray()) {
				for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
					ops.ops.add(jsonElement2.getAsString());
				}
			}
		} catch (Exception var6) {
		}

		return ops;
	}
}
