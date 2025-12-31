package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.hooks.DebugScreenHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.sodium.api.util.MathUtil;

import java.lang.management.ManagementFactory;

/**
 * Implements debug screen hooks for Sodium's off-heap memory display.
 */
public class SodiumDebugScreenHook implements DebugScreenHooks {
    private static final ResourceLocation MEMORY_GROUP = ResourceLocation.withDefaultNamespace("memory");

    @Override
    public void onDebugMemoryDisplay(DebugScreenDisplayer debugScreenDisplayer, Level level,
                                     LevelChunk levelChunk, LevelChunk levelChunk2) {
        debugScreenDisplayer.addToGroup(MEMORY_GROUP, getNativeMemoryString());
    }

    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }
}
