package net.minecraft.world.level.chunk;

import java.util.Arrays;

/** A section's complete storage state, for comparing load routes: palette
 * classes and entries (by identity), storage classes, bits and raw words,
 * and the block counters. */
public final class SectionFingerprint {
    private SectionFingerprint() {}

    private static String container(PalettedContainer<?> container) {
        var data = container.dataForNativeScan();
        var palette = data.palette();
        var entries = new StringBuilder();
        for (int i = 0; i < palette.getSize(); i++) {
            if (palette.getClass() == GlobalPalette.class) break;
            entries.append(System.identityHashCode(palette.valueFor(i))).append(',');
        }
        return palette.getClass().getSimpleName() + "[" + entries + "]" + data.storage().getClass().getSimpleName() + data.storage().getBits()
            + Arrays.toString(data.storage().getRaw());
    }

    public static String of(LevelChunkSection section) {
        if (section == null) return "null";
        String biomes = section.getBiomes() instanceof PalettedContainer<?> c ? container(c) : section.getBiomes().getClass().getName();
        return container(section.getStates()) + "|" + biomes + "|" + section.hasOnlyAir() + section.isRandomlyTicking() + section.isRandomlyTickingBlocks()
            + section.isRandomlyTickingFluids();
    }

    /** The container's live storage words, for tests that corrupt saved data. */
    public static long[] rawWords(PalettedContainer<?> container) {
        return container.dataForCompatibilityMutation().storage().getRaw();
    }
}
