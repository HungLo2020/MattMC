package com.mojang.realmsclient.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RegionSelectionPreferenceDto extends ValueObject implements ReflectionBasedSerialization {
	public static final RegionSelectionPreferenceDto DEFAULT = new RegionSelectionPreferenceDto(RegionSelectionPreference.AUTOMATIC_OWNER, null);
	private static final Logger LOGGER = LogUtils.getLogger();
	@SerializedName("regionSelectionPreference")
	@JsonAdapter(RegionSelectionPreference.RegionSelectionPreferenceJsonAdapter.class)
	public RegionSelectionPreference regionSelectionPreference;
	@SerializedName("preferredRegion")
	@JsonAdapter(RealmsRegion.RealmsRegionJsonAdapter.class)
	@Nullable
	public RealmsRegion preferredRegion;

	public RegionSelectionPreferenceDto(RegionSelectionPreference regionSelectionPreference, @Nullable RealmsRegion realmsRegion) {
		this.regionSelectionPreference = regionSelectionPreference;
		this.preferredRegion = realmsRegion;
	}

	private RegionSelectionPreferenceDto() {
	}

	public static RegionSelectionPreferenceDto parse(GuardedSerializer guardedSerializer, String string) {
		try {
			RegionSelectionPreferenceDto regionSelectionPreferenceDto = guardedSerializer.fromJson(string, RegionSelectionPreferenceDto.class);
			if (regionSelectionPreferenceDto == null) {
				LOGGER.error("Could not parse RegionSelectionPreference: {}", string);
				return new RegionSelectionPreferenceDto();
			} else {
				return regionSelectionPreferenceDto;
			}
		} catch (Exception var3) {
			LOGGER.error("Could not parse RegionSelectionPreference: {}", var3.getMessage());
			return new RegionSelectionPreferenceDto();
		}
	}

	public RegionSelectionPreferenceDto clone() {
		return new RegionSelectionPreferenceDto(this.regionSelectionPreference, this.preferredRegion);
	}
}
