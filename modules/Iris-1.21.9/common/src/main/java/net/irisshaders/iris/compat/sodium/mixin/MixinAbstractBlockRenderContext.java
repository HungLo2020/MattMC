package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.irisshaders.iris.compat.general.IrisModSupport;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(AbstractBlockRenderContext.class)
public class MixinAbstractBlockRenderContext {
	@Shadow
	protected BlockPos pos;

	@Shadow
	protected BlockAndTintGetter level;

	@Unique
	private Direction iris$capturedCullFace;

	/**
	 * Capture the cullFace Direction when it's stored.
	 * This avoids @Local capture issues on MC 1.21.10.
	 */
	@ModifyVariable(method = "bufferDefaultModel", at = @At(value = "STORE"), ordinal = 0)
	private Direction iris$captureCullFace(Direction cullFace) {
		this.iris$capturedCullFace = cullFace;
		return cullFace;
	}

	@Inject(method = "bufferDefaultModel", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/services/PlatformModelAccess;getQuads(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/model/BlockModelPart;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Ljava/util/List;"))
	private void checkDirectionNeo(BlockModelPart part, Predicate<Direction> cullTest, CallbackInfo ci) {
		if ((Object) this instanceof BlockRenderer r && WorldRenderingSettings.INSTANCE.getBlockStateIds() != null && iris$capturedCullFace != null) {
			BlockState override = IrisModSupport.INSTANCE.getModelPartState(part);
			if (override != null) {
				//((BlockSensitiveBufferBuilder) ((BlockRendererAccessor) r)).overrideBlock(WorldRenderingSettings.INSTANCE.getBlockStateIds().getInt(override));
			}
		}
	}

	@Inject(method = "bufferDefaultModel", at = @At(value = "TAIL"))
	private void checkDirectionNeoTail(BlockModelPart part, Predicate<Direction> cullTest, CallbackInfo ci) {
		if ((Object) this instanceof BlockRenderer r && WorldRenderingSettings.INSTANCE.getBlockStateIds() != null) {
			//((BlockSensitiveBufferBuilder) ((BlockRendererAccessor) r).getBuffers()).restoreBlock();
		}
		iris$capturedCullFace = null;
	}
}
