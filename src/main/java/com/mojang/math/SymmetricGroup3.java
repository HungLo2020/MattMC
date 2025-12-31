package com.mojang.math;

import java.util.Arrays;
import net.minecraft.Util;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;

public enum SymmetricGroup3 {
	P123(0, 1, 2),
	P213(1, 0, 2),
	P132(0, 2, 1),
	P231(1, 2, 0),
	P312(2, 0, 1),
	P321(2, 1, 0);

	private final int[] permutation;
	private final Matrix3fc transformation;
	private static final int ORDER = 3;
	private static final SymmetricGroup3[][] CAYLEY_TABLE = Util.make(
		new SymmetricGroup3[values().length][values().length],
		symmetricGroup3s -> {
			for (SymmetricGroup3 symmetricGroup3 : values()) {
				for (SymmetricGroup3 symmetricGroup32 : values()) {
					int[] is = new int[3];

					for (int i = 0; i < 3; i++) {
						is[i] = symmetricGroup3.permutation[symmetricGroup32.permutation[i]];
					}

					SymmetricGroup3 symmetricGroup33 = (SymmetricGroup3)Arrays.stream(values())
						.filter(symmetricGroup3x -> Arrays.equals(symmetricGroup3x.permutation, is))
						.findFirst()
						.get();
					symmetricGroup3s[symmetricGroup3.ordinal()][symmetricGroup32.ordinal()] = symmetricGroup33;
				}
			}
		}
	);

	private SymmetricGroup3(final int j, final int k, final int l) {
		this.permutation = new int[]{j, k, l};
		Matrix3f matrix3f = new Matrix3f().zero();
		matrix3f.set(this.permutation(0), 0, 1.0F);
		matrix3f.set(this.permutation(1), 1, 1.0F);
		matrix3f.set(this.permutation(2), 2, 1.0F);
		this.transformation = matrix3f;
	}

	public SymmetricGroup3 compose(SymmetricGroup3 symmetricGroup3) {
		return CAYLEY_TABLE[this.ordinal()][symmetricGroup3.ordinal()];
	}

	public int permutation(int i) {
		return this.permutation[i];
	}

	public Matrix3fc transformation() {
		return this.transformation;
	}
}
