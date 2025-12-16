package net.irisshaders.iris.compat.sodium.mixin;

import net.minecraft.client.renderer.chunk.advanced.compile.ChunkBuildBuffers;
import net.minecraft.client.renderer.chunk.advanced.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockRenderer.class)
public interface BlockRendererAccessor {
	@Accessor
	ChunkBuildBuffers getBuffers();
}
