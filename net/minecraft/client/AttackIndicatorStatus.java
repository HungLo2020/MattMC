package net.minecraft.client;

import java.util.function.IntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.OptionEnum;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;

@Environment(EnvType.CLIENT)
public enum AttackIndicatorStatus implements OptionEnum {
	OFF(0, "options.off"),
	CROSSHAIR(1, "options.attack.crosshair"),
	HOTBAR(2, "options.attack.hotbar");

	private static final IntFunction<AttackIndicatorStatus> BY_ID = ByIdMap.continuous(AttackIndicatorStatus::getId, values(), OutOfBoundsStrategy.WRAP);
	private final int id;
	private final String key;

	private AttackIndicatorStatus(final int j, final String string2) {
		this.id = j;
		this.key = string2;
	}

	public int getId() {
		return this.id;
	}

	public String getKey() {
		return this.key;
	}

	public static AttackIndicatorStatus byId(int i) {
		return (AttackIndicatorStatus)BY_ID.apply(i);
	}
}
