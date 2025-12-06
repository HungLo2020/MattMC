package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsServerList extends ValueObject implements ReflectionBasedSerialization {
	private static final Logger LOGGER = LogUtils.getLogger();
	@SerializedName("servers")
	public List<RealmsServer> servers = new ArrayList();

	public static RealmsServerList parse(GuardedSerializer guardedSerializer, String string) {
		try {
			RealmsServerList realmsServerList = guardedSerializer.fromJson(string, RealmsServerList.class);
			if (realmsServerList == null) {
				LOGGER.error("Could not parse McoServerList: {}", string);
				return new RealmsServerList();
			} else {
				realmsServerList.servers.forEach(RealmsServer::finalize);
				return realmsServerList;
			}
		} catch (Exception var3) {
			LOGGER.error("Could not parse McoServerList: {}", var3.getMessage());
			return new RealmsServerList();
		}
	}
}
