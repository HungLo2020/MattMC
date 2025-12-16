package net.minecraft.client.renderer.sodium.util;

import net.minecraft.util.RandomSource;

public interface WeightedRandomListExtension<T> {
    T sodium$getQuick(RandomSource random);
}
