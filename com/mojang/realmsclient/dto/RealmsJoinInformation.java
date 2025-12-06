package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record RealmsJoinInformation(
	@Nullable @SerializedName("address") String address,
	@Nullable @SerializedName("resourcePackUrl") String resourcePackUrl,
	@Nullable @SerializedName("resourcePackHash") String resourcePackHash,
	@Nullable @SerializedName("sessionRegionData") RealmsJoinInformation.RegionData regionData
) implements ReflectionBasedSerialization {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final RealmsJoinInformation EMPTY = new RealmsJoinInformation(null, null, null, null);

	public static RealmsJoinInformation parse(GuardedSerializer guardedSerializer, String string) {
		try {
			RealmsJoinInformation realmsJoinInformation = guardedSerializer.fromJson(string, RealmsJoinInformation.class);
			if (realmsJoinInformation == null) {
				LOGGER.error("Could not parse RealmsServerAddress: {}", string);
				return EMPTY;
			} else {
				return realmsJoinInformation;
			}
		} catch (Exception var3) {
			LOGGER.error("Could not parse RealmsServerAddress: {}", var3.getMessage());
			return EMPTY;
		}
	}

	@Environment(EnvType.CLIENT)
	public record RegionData(
		@Nullable @SerializedName("regionName") RealmsRegion region, @Nullable @SerializedName("serviceQuality") ServiceQuality serviceQuality
	) implements ReflectionBasedSerialization {
	}
}
