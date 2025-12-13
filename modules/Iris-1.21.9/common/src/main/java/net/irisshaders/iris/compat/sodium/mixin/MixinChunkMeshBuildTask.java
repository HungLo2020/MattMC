package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension;
import net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkBuilderMeshingTask.class)
public class MixinChunkMeshBuildTask {
	private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

	@Unique
	private ChunkBuildBuffers iris$currentBuffers;

	@Unique
	private BlockState iris$currentBlockState;

	@Unique
	private BlockPos.MutableBlockPos iris$currentBlockPos;

	@Unique
	private BlockRenderer iris$currentBlockRenderer;

	@Unique
	private FluidState iris$currentFluidState;

	@Unique
	private BlockRenderCache iris$currentCache;

	/**
	 * Capture ChunkBuildBuffers when stored.
	 */
	@ModifyVariable(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "STORE"), ordinal = 0)
	private ChunkBuildBuffers iris$captureBuffers(ChunkBuildBuffers buffers) {
		this.iris$currentBuffers = buffers;
		return buffers;
	}

	/**
	 * Capture BlockState when stored.
	 */
	@ModifyVariable(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "STORE"), ordinal = 0)
	private BlockState iris$captureBlockState(BlockState blockState) {
		this.iris$currentBlockState = blockState;
		return blockState;
	}

	/**
	 * Capture BlockPos.MutableBlockPos when stored (first one, ordinal 0).
	 */
	@ModifyVariable(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "STORE"), ordinal = 0)
	private BlockPos.MutableBlockPos iris$captureBlockPos(BlockPos.MutableBlockPos blockPos) {
		this.iris$currentBlockPos = blockPos;
		return blockPos;
	}

	/**
	 * Capture BlockRenderer when stored.
	 */
	@ModifyVariable(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "STORE"), ordinal = 0)
	private BlockRenderer iris$captureBlockRenderer(BlockRenderer blockRenderer) {
		this.iris$currentBlockRenderer = blockRenderer;
		return blockRenderer;
	}

	/**
	 * Capture FluidState when stored.
	 */
	@ModifyVariable(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "STORE"), ordinal = 0)
	private FluidState iris$captureFluidState(FluidState fluidState) {
		this.iris$currentFluidState = fluidState;
		return fluidState;
	}

	/**
	 * Capture BlockRenderCache when stored.
	 */
	@ModifyVariable(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "STORE"), ordinal = 0)
	private BlockRenderCache iris$captureCache(BlockRenderCache cache) {
		this.iris$currentCache = cache;
		return cache;
	}

	@Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;", ordinal = 1))
	private void iris$setLightBlock(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		if (WorldRenderingSettings.INSTANCE.getBlockStateIds() == null) return;
		if (iris$currentBuffers == null || iris$currentBlockState == null || iris$currentBlockPos == null) return;

		if (WorldRenderingSettings.INSTANCE.shouldVoxelizeLightBlocks() && iris$currentBlockState.getBlock() instanceof LightBlock) {
			ChunkModelBuilder buildBuffers = iris$currentBuffers.get(DefaultMaterials.CUTOUT);
			int id = WorldRenderingSettings.INSTANCE.getBlockStateIds().getInt(iris$currentBlockState);
			for (int i = 0; i < 4; i++) {
				// TODO: Add ignoreMidBlock support
				((ChunkVertexExtension) vertices[i]).iris$ignoresMidBlock(true);
				((ChunkVertexExtension) vertices[i]).iris$setData((byte) iris$currentBlockState.getLightEmission(), (byte) 0, id,
					(iris$currentBlockPos.getX() & 15), (iris$currentBlockPos.getY() & 15), (iris$currentBlockPos.getZ() & 15));
				vertices[i].x = (float) ((iris$currentBlockPos.getX() & 15)) + 0.25f;
				vertices[i].y = (float) ((iris$currentBlockPos.getY() & 15)) + 0.25f;
				vertices[i].z = (float) ((iris$currentBlockPos.getZ() & 15)) + 0.25f;
				vertices[i].u = 0;
				vertices[i].v = 0;
				vertices[i].color = 0;
				vertices[i].light = iris$currentBlockState.getLightEmission() << 4 | iris$currentBlockState.getLightEmission() << 20;
			}
			buildBuffers.getVertexBuffer(ModelQuadFacing.UNASSIGNED).push(vertices, DefaultMaterials.CUTOUT);
		}
	}

	@Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/renderer/block/model/BlockStateModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"))
	private void iris$onRenderModel(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		if (WorldRenderingSettings.INSTANCE.getBlockStateIds() == null) return;
		if (iris$currentBlockRenderer == null || iris$currentBlockState == null || iris$currentBlockPos == null) return;

		((VertexEncoderInterface) iris$currentBlockRenderer).beginBlock(WorldRenderingSettings.INSTANCE.getBlockStateIds().getOrDefault(iris$currentBlockState, -1), (byte) 0, (byte) iris$currentBlockState.getLightEmission(), iris$currentBlockPos.getX(), iris$currentBlockPos.getY(), iris$currentBlockPos.getZ());
	}

	@Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/FluidRenderer;render(Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers;)V"))
	private void iris$onRenderLiquid(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		if (WorldRenderingSettings.INSTANCE.getBlockStateIds() == null) return;
		if (iris$currentCache == null || iris$currentFluidState == null || iris$currentBlockState == null || iris$currentBlockPos == null) return;

		((VertexEncoderInterface) iris$currentCache.getFluidRenderer()).beginBlock(WorldRenderingSettings.INSTANCE.getBlockStateIds().getInt(iris$currentFluidState.createLegacyBlock()), (byte) 1, (byte) iris$currentBlockState.getLightEmission(), iris$currentBlockPos.getX(), iris$currentBlockPos.getY(), iris$currentBlockPos.getZ());
	}

	@Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z"))
	private void iris$onEnd(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		//((BlockSensitiveBufferBuilder) buffers).endBlock();
	}
}
