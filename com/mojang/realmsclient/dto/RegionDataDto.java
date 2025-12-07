package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record RegionDataDto(@SerializedName("regionName") RealmsRegion region, @SerializedName("serviceQuality") ServiceQuality serviceQuality)
	implements ReflectionBasedSerialization {
}
