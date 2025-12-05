package com.mojang.realmsclient.dto;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class PendingInvitesList extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	public List<PendingInvite> pendingInvites = Lists.<PendingInvite>newArrayList();

	public static PendingInvitesList parse(String string) {
		PendingInvitesList pendingInvitesList = new PendingInvitesList();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			if (jsonObject.get("invites").isJsonArray()) {
				for (JsonElement jsonElement : jsonObject.get("invites").getAsJsonArray()) {
					pendingInvitesList.pendingInvites.add(PendingInvite.parse(jsonElement.getAsJsonObject()));
				}
			}
		} catch (Exception var5) {
			LOGGER.error("Could not parse PendingInvitesList: {}", var5.getMessage());
		}

		return pendingInvitesList;
	}
}
