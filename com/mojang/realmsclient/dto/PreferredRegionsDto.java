package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record PreferredRegionsDto(@SerializedName("regionDataList") List<RegionDataDto> regionData) implements ReflectionBasedSerialization {
	public static PreferredRegionsDto empty() {
		return new PreferredRegionsDto(List.of());
	}
}
