package com.mojang.realmsclient.dto;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class BackupList extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	public List<Backup> backups;

	public static BackupList parse(String string) {
		BackupList backupList = new BackupList();
		backupList.backups = Lists.<Backup>newArrayList();

		try {
			JsonElement jsonElement = LenientJsonParser.parse(string).getAsJsonObject().get("backups");
			if (jsonElement.isJsonArray()) {
				for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
					backupList.backups.add(Backup.parse(jsonElement2));
				}
			}
		} catch (Exception var5) {
			LOGGER.error("Could not parse BackupList: {}", var5.getMessage());
		}

		return backupList;
	}
}
