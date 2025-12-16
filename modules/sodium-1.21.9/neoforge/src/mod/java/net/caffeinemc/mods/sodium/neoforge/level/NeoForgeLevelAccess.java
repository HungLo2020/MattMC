package net.caffeinemc.mods.sodium.neoforge.level;

import net.minecraft.client.renderer.sodium.services.PlatformLevelAccess;
import net.minecraft.client.renderer.sodium.world.SodiumAuxiliaryLightManager;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

public class NeoForgeLevelAccess implements PlatformLevelAccess {
    @Override
    public @Nullable Object getBlockEntityData(BlockEntity blockEntity) {
        return null; // NeoForge does not have an equivalent API.
    }

    @Override
    public @Nullable SodiumAuxiliaryLightManager getLightManager(LevelChunk chunk, SectionPos pos) {
        return (SodiumAuxiliaryLightManager) chunk.getAuxLightManager(pos.origin());
    }
}
