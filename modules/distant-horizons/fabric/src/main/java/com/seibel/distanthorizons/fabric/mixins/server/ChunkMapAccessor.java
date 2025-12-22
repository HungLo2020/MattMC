package com.seibel.distanthorizons.fabric.mixins.server;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to expose the package-private level field from ChunkMap.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("level")
	ServerLevel distanthorizons$getLevel();
}
