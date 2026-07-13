package net.sodium.client.render.chunk.compile.tasks;

import net.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.sodium.client.render.chunk.compile.pipeline.NativeStaticBlockModelRegistry;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.services.PlatformBlockAccess;
import net.sodium.client.world.LevelSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.lwjgl.system.MemoryUtil;

final class NativeSectionSnapshot implements AutoCloseable {
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;

    private final ChunkBuildBuffers buffers;
    private final int sectionIndex;
    private final int minX;
    private final int minY;
    private final int minZ;
    private long address;
    private int activeRecordCount;
    private final int[] lightWords = new int[27];
    private final int[] neighborhoodStateIds = new int[27];

    NativeSectionSnapshot(ChunkBuildBuffers buffers, int sectionIndex, int minX, int minY, int minZ) {
        this.buffers = buffers;
        this.sectionIndex = sectionIndex;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;

        this.address = MemoryUtil.nmemCalloc(SECTION_BLOCK_COUNT,
                NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE);
        if (this.address == 0L) {
            throw new OutOfMemoryError("Could not allocate native section snapshot");
        }
    }

    void appendBlock(int localBlockIndex, LevelSlice slice, BlockState blockState, BlockPos blockPos,
            int localX, int localY, int localZ, boolean suppressNativeFluid) {
        long recordAddress = this.recordAddress(this.activeRecordCount++);
        long seed = blockState.getSeed(blockPos);
        int neighborhoodIndex = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int sampleX = blockPos.getX() + dx;
                    int sampleY = blockPos.getY() + dy;
                    int sampleZ = blockPos.getZ() + dz;
                    BlockState sampleState = slice.getBlockState(sampleX, sampleY, sampleZ);
                    this.neighborhoodStateIds[neighborhoodIndex] = NativeStaticBlockModelRegistry.getStateId(sampleState);
                    this.lightWords[neighborhoodIndex] = computeLightWord(slice, sampleState, sampleX, sampleY, sampleZ);
                    neighborhoodIndex++;
                }
            }
        }

        FluidState fluidState = blockState.getFluidState();
        var flow = fluidState.isEmpty() ? net.minecraft.world.phys.Vec3.ZERO : fluidState.getFlow(slice, blockPos);
        int tint = blockTint(slice, blockState, blockPos);
        int fluidTint = fluidTint(slice, fluidState, blockPos);
        NativeChunkMeshEncoder.writeNativeSectionBlockRecord(recordAddress,
                NativeStaticBlockModelRegistry.getStateId(blockState), irisBlockId(blockState), localX, localY,
                localZ, seed,
                NativeStaticBlockModelRegistry.getStateId(slice.getBlockState(blockPos.getX(), blockPos.getY() - 1, blockPos.getZ())),
                NativeStaticBlockModelRegistry.getStateId(slice.getBlockState(blockPos.getX(), blockPos.getY() + 1, blockPos.getZ())),
                NativeStaticBlockModelRegistry.getStateId(slice.getBlockState(blockPos.getX(), blockPos.getY(), blockPos.getZ() - 1)),
                NativeStaticBlockModelRegistry.getStateId(slice.getBlockState(blockPos.getX(), blockPos.getY(), blockPos.getZ() + 1)),
                NativeStaticBlockModelRegistry.getStateId(slice.getBlockState(blockPos.getX() - 1, blockPos.getY(), blockPos.getZ())),
                NativeStaticBlockModelRegistry.getStateId(slice.getBlockState(blockPos.getX() + 1, blockPos.getY(), blockPos.getZ())),
                this.lightWords, this.neighborhoodStateIds, tint, fluidTint, (float) flow.x, (float) flow.z,
                blockPos.getX(), blockPos.getY(), blockPos.getZ());
        if (!fluidState.isEmpty()) {
            NativeChunkMeshEncoder.writeNativeSectionBlockFluidBlockId(recordAddress,
                    irisFluidBlockId(fluidState));
        }
        if (suppressNativeFluid) {
            NativeChunkMeshEncoder.writeNativeSectionBlockFlags(recordAddress,
                    NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID);
        }
    }

    int[] flushAll(TranslucentGeometryCollector collector) {
        NativeMeshingDiagnostics.dumpSectionSnapshot(this.sectionIndex, this.minX, this.minY, this.minZ,
                this.address, this.activeRecordCount);
        int[] nativeQuads = this.buffers.appendNativeSectionSnapshotAllPasses(this.address, this.activeRecordCount,
                this.sectionIndex, collector);
        this.addNativeFluidSprites(DefaultTerrainRenderPasses.SOLID);
        this.addNativeFluidSprites(DefaultTerrainRenderPasses.CUTOUT);
        this.addNativeFluidSprites(DefaultTerrainRenderPasses.TRANSLUCENT);
        return nativeQuads;
    }

    private void addNativeFluidSprites(TerrainRenderPass pass) {
        int emittedSpriteMask = this.buffers.nativeFluidSpriteMask(pass);
        for (var sprite : NativeStaticBlockModelRegistry.getNativeFluidSprites(emittedSpriteMask)) {
            this.buffers.get(pass).addSprite(sprite);
        }
    }

    private long recordAddress(int localBlockIndex) {
        if (localBlockIndex < 0 || localBlockIndex >= SECTION_BLOCK_COUNT) {
            throw new IllegalArgumentException("Invalid section block index: " + localBlockIndex);
        }

        return this.address + (long) localBlockIndex * NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE;
    }

    @Override
    public void close() {
        if (this.address != 0L) {
            MemoryUtil.nmemFree(this.address);
            this.address = 0L;
        }
    }

    static boolean isNativeFluidSupported(FluidState fluidState) {
        return NativeStaticBlockModelRegistry.isNativeFluidSupported(fluidState);
    }

    private static int computeLightWord(LevelSlice slice, BlockState state, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        boolean emissive = state.emissiveRendering(slice, pos);
        boolean opaque = state.isViewBlocking(slice, pos) && state.getLightBlock() != 0;
        boolean fullOpaque = state.isSolidRender();
        boolean fullCube = state.isCollisionShapeFullBlock(slice, pos);
        int luminance = PlatformBlockAccess.getInstance().getLightEmission(state, slice, pos);
        int blockLight;
        int skyLight;
        if (fullOpaque && luminance == 0) {
            blockLight = 0;
            skyLight = 0;
        } else {
            if (emissive) {
                blockLight = slice.getBrightness(LightLayer.BLOCK, pos);
                skyLight = slice.getBrightness(LightLayer.SKY, pos);
            } else {
                int light = LevelRenderer.getLightColor(LevelRenderer.BrightnessGetter.DEFAULT, slice, state, pos);
                blockLight = LightTexture.block(light);
                skyLight = LightTexture.sky(light);
            }
        }
        float ao = luminance == 0 ? state.getShadeBrightness(slice, pos) : 1.0F;
        int aoi = (int) (ao * 4096.0F);
        return (blockLight & 0xF)
                | ((skyLight & 0xF) << 4)
                | ((luminance & 0xF) << 8)
                | ((aoi & 0xFFFF) << 12)
                | ((emissive ? 1 : 0) << 28)
                | ((opaque ? 1 : 0) << 29)
                | ((fullOpaque ? 1 : 0) << 30)
                | ((fullCube ? 1 : 0) << 31);
    }

    private static int blockTint(LevelSlice slice, BlockState state, BlockPos pos) {
        if (NativeMeshingDiagnostics.forceWhiteTint()) {
            return 0xFFFFFFFF;
        }
        Block block = state.getBlock();
        if (block == Blocks.GRASS_BLOCK || block == Blocks.FERN || block == Blocks.SHORT_GRASS
                || block == Blocks.POTTED_FERN || block == Blocks.BUSH || block == Blocks.SUGAR_CANE
                || block == Blocks.PINK_PETALS || block == Blocks.WILDFLOWERS
                || block == Blocks.LARGE_FERN || block == Blocks.TALL_GRASS) {
            return BiomeColors.getAverageGrassColor(slice, pos) | 0xFF000000;
        }
        if (block == Blocks.OAK_LEAVES || block == Blocks.JUNGLE_LEAVES || block == Blocks.ACACIA_LEAVES
                || block == Blocks.DARK_OAK_LEAVES || block == Blocks.VINE || block == Blocks.MANGROVE_LEAVES
                || block == Blocks.LEAF_LITTER) {
            return BiomeColors.getAverageFoliageColor(slice, pos) | 0xFF000000;
        }
        if (block == Blocks.REDSTONE_WIRE) {
            return RedStoneWireBlock.getColorForPower(state.getValue(RedStoneWireBlock.POWER)) | 0xFF000000;
        }
        int color = Minecraft.getInstance().getBlockColors().getColor(state, slice, pos, 0);
        return color < 0 ? -1 : color | 0xFF000000;
    }

    private static int fluidTint(LevelSlice slice, FluidState state, BlockPos pos) {
        if (NativeMeshingDiagnostics.forceWhiteTint()) {
            return 0xFFFFFFFF;
        }
        if (state.is(Fluids.WATER) || state.is(Fluids.FLOWING_WATER)) {
            return BiomeColors.getAverageWaterColor(slice, pos) | 0xFF000000;
        }
        return -1;
    }

    private static int irisBlockId(BlockState state) {
        var ids = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds();
        return ids == null ? -1 : ids.getOrDefault(state, -1);
    }

    private static int irisFluidBlockId(FluidState state) {
        var ids = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds();
        return ids == null ? -1 : ids.getInt(state.createLegacyBlock());
    }
}
