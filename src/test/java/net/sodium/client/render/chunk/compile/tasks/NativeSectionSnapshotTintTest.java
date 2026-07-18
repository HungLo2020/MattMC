package net.sodium.client.render.chunk.compile.tasks;

import net.minecraft.client.color.block.BlockColors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeSectionSnapshotTintTest {
    @Test
    void normalizeBlockTintPreservesOpaqueNegativeLilyPadColors() {
        assertEquals(0xFF208030, NativeSectionSnapshot.normalizeBlockTintColor(BlockColors.LILY_PAD_IN_WORLD));
        assertEquals(0xFF71C35C, NativeSectionSnapshot.normalizeBlockTintColor(BlockColors.LILY_PAD_DEFAULT));
    }

    @Test
    void normalizeBlockTintOnlyTreatsMinusOneAsMissingTint() {
        assertEquals(-1, NativeSectionSnapshot.normalizeBlockTintColor(-1));
        assertEquals(0xFF00FF00, NativeSectionSnapshot.normalizeBlockTintColor(0x00FF00));
    }
}
