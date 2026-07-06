package net.minecraft.world.item;

import java.util.Map;

public final class TaczGunFireModeAdjustments {
	private static final Map<String, Map<TaczFireMode, Integer>> RPM_ADJUSTMENTS = Map.of(
		"fn_fal", Map.of(TaczFireMode.AUTO, 300),
		"hk_g3", Map.of(TaczFireMode.AUTO, 350),
		"hk_mk23", Map.of(TaczFireMode.BURST, 250),
		"mk14", Map.of(TaczFireMode.AUTO, 380),
		"spr15hb", Map.of(TaczFireMode.SEMI, -400),
		"stg44", Map.of(TaczFireMode.SEMI, -400),
		"type_81", Map.of(TaczFireMode.SEMI, -330)
	);

	private TaczGunFireModeAdjustments() {
	}

	public static int rpm(String gunId, TaczFireMode fireMode, int baseRpm) {
		int adjusted = baseRpm + RPM_ADJUSTMENTS.getOrDefault(gunId, Map.of()).getOrDefault(fireMode, 0);
		return adjusted > 0 ? adjusted : 300;
	}
}
