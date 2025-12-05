package com.mojang.math;

import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

public enum Quadrant {
	R0(0),
	R90(1),
	R180(2),
	R270(3);

	public static final Codec<Quadrant> CODEC = Codec.INT.comapFlatMap(integer -> {
		return switch (Mth.positiveModulo(integer, 360)) {
			case 0 -> DataResult.success(R0);
			case 90 -> DataResult.success(R90);
			case 180 -> DataResult.success(R180);
			case 270 -> DataResult.success(R270);
			default -> DataResult.error(() -> "Invalid rotation " + integer + " found, only 0/90/180/270 allowed");
		};
	}, quadrant -> {
		return switch (quadrant) {
			case R0 -> 0;
			case R90 -> 90;
			case R180 -> 180;
			case R270 -> 270;
		};
	});
	public final int shift;

	private Quadrant(final int j) {
		this.shift = j;
	}

	@Deprecated
	public static Quadrant parseJson(int i) {
		return switch (Mth.positiveModulo(i, 360)) {
			case 0 -> R0;
			case 90 -> R90;
			case 180 -> R180;
			case 270 -> R270;
			default -> throw new JsonParseException("Invalid rotation " + i + " found, only 0/90/180/270 allowed");
		};
	}

	public int rotateVertexIndex(int i) {
		return (i + this.shift) % 4;
	}
}
