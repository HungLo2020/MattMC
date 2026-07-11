package net.sodium.client.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.sodium.client.render.chunk.ExtendedBlockEntityType;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.sodium.client.render.chunk.compile.estimation.MeshTaskSizeEstimator;
import net.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.sodium.client.render.chunk.compile.pipeline.NativeStaticBlockModelRegistry;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.sodium.client.render.chunk.translucent_sorting.data.PresentTranslucentData;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.services.PlatformLevelRenderHooks;
import net.sodium.client.services.PlatformBlockAccess;
import net.sodium.client.util.task.CancellationToken;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.system.MemoryUtil;
import org.joml.Vector3dc;

import java.util.Map;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 * <p>
 * This task takes a slice of the level from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;

    private final ChunkRenderContext renderContext;
    private final SortBehavior sortBehavior;
    private final boolean forceSort;

    public ChunkBuilderMeshingTask(RenderSection render, int buildTime, Vector3dc absoluteCameraPos, ChunkRenderContext renderContext, SortBehavior sortBehavior, boolean forceSort) {
        super(render, buildTime, absoluteCameraPos);
        this.renderContext = renderContext;
        this.sortBehavior = sortBehavior;
        this.forceSort = forceSort;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext buildContext, CancellationToken cancellationToken) {
        ProfilerFiller profiler = Profiler.get();
        BuiltSectionInfo.Builder renderData = new BuiltSectionInfo.Builder();
        VisGraph occluder = new VisGraph();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        BlockRenderCache cache = buildContext.cache;
        cache.init(this.renderContext);

        LevelSlice slice = cache.getWorldSlice();

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(minX, minY, minZ);
        BlockPos.MutableBlockPos modelOffset = new BlockPos.MutableBlockPos();

        boolean sortEnabled = this.sortBehavior != SortBehavior.OFF && !NativeMeshingDiagnostics.forceNoTranslucentSort();
        TranslucentGeometryCollector collector;
        if (sortEnabled) {
            collector = new TranslucentGeometryCollector(this.render.getPosition(), this.sortBehavior);
        } else {
            collector = null;
        }
        BlockRenderer blockRenderer = cache.getBlockRenderer();
        blockRenderer.prepare(buffers, slice, collector);
        cache.getFluidRenderer().resetEmittedQuadCount();
        buffers.resetFallbackConsumerEmittedQuadCount();
        NativeMeshingDiagnostics.FallbackStats fallbackStats = NativeMeshingDiagnostics.createFallbackStats();
        boolean forceJavaProducers = NativeMeshingDiagnostics.forceJavaProducers();
        boolean forceJavaModels = NativeMeshingDiagnostics.forceJavaModels();
        boolean forceJavaFluids = NativeMeshingDiagnostics.forceJavaFluids();
        int fallbackBlockCount = 0;
        int fallbackQuadStart = blockRenderer.getEmittedQuadCount();

        profiler.push("render blocks");
        try (NativeSectionSnapshot nativeSectionSnapshot =
	                     new NativeSectionSnapshot(buffers, this.render.getSectionIndex(), minX, minY, minZ)) {
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        BlockState blockState = slice.getBlockState(x, y, z);

                        if (blockState.isAir() && !blockState.hasBlockEntity()) {
                            continue;
                        }

                        blockPos.set(x, y, z);
                        modelOffset.set(x & 15, y & 15, z & 15);
                        int localX = blockPos.getX() & 15;
                        int localY = blockPos.getY() & 15;
                        int localZ = blockPos.getZ() & 15;
                        int localBlockIndex = (localY << 8) | (localZ << 4) | localX;

                        FluidState fluidState = blockState.getFluidState();
                        boolean nativeFluidSupported = !fluidState.isEmpty()
                                && NativeSectionSnapshot.isNativeFluidSupported(fluidState);
                        boolean collectorNeedsJavaFluid = !fluidState.isEmpty()
                                && nativeFluidSupported
                                && collector != null
                                && DefaultMaterials.forFluidState(fluidState).pass.isTranslucent()
                                && !collector.supportsNativeBatching();
                        boolean useJavaFluid = !fluidState.isEmpty()
                                && (forceJavaFluids || !nativeFluidSupported || collectorNeedsJavaFluid);

                        if (!forceJavaProducers) {
                            nativeSectionSnapshot.appendBlock(localBlockIndex, slice, blockState, blockPos, localX,
                                    localY, localZ, useJavaFluid);
                        }

                        if (blockState.getRenderShape() == RenderShape.MODEL) {
                            if (forceJavaModels || !NativeStaticBlockModelRegistry.hasNativeModel(blockState)) {
                                BlockStateModel model = cache.getBlockModels()
                                        .getBlockModel(blockState);
                                int modelFallbackQuadStart = blockRenderer.getEmittedQuadCount();
                                // Iris: Begin block data for Iris shaders (merged from MixinChunkMeshBuildTask)
                                if (net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds() != null) {
                                    ((net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface) blockRenderer).beginBlock(
                                        net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds().getOrDefault(blockState, -1),
                                        (byte) 0, (byte) blockState.getLightEmission(), blockPos.getX(), blockPos.getY(), blockPos.getZ());
                                }
                                blockRenderer.renderModel(model, blockState, blockPos, modelOffset);
                                fallbackStats.recordModelFallback(blockState,
                                        blockRenderer.getEmittedQuadCount() - modelFallbackQuadStart);
                                fallbackBlockCount++;
                            } else {
                                for (var sprite : NativeStaticBlockModelRegistry.getSprites(blockState)) {
                                    buffers.get(DefaultMaterials.forBlockState(blockState).pass).addSprite(sprite);
                                }
                                fallbackStats.recordNativeModelBlock();
                            }
                        }

                        if (useJavaFluid) {
                            int fluidFallbackQuadStart = cache.getFluidRenderer().getEmittedQuadCount();
                            // Iris: Begin block data for fluids (merged from MixinChunkMeshBuildTask)
                            if (net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds() != null) {
                                ((net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface) cache.getFluidRenderer()).beginBlock(
                                    net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds().getInt(fluidState.createLegacyBlock()),
                                    (byte) 1, (byte) blockState.getLightEmission(), blockPos.getX(), blockPos.getY(), blockPos.getZ());
                            }
                            cache.getFluidRenderer().render(slice, blockState, fluidState, blockPos, modelOffset, collector, buffers);
                            fallbackStats.recordFluidFallback(blockState, fluidState,
                                    cache.getFluidRenderer().getEmittedQuadCount() - fluidFallbackQuadStart);
                            fallbackBlockCount++;
                        } else if (!fluidState.isEmpty()) {
                            for (var sprite : NativeStaticBlockModelRegistry.getFluidSprites(fluidState)) {
                                buffers.get(DefaultMaterials.forFluidState(fluidState).pass).addSprite(sprite);
                            }
                            fallbackStats.recordNativeFluidBlock();
                        }

                        if (blockState.hasBlockEntity()) {
                            BlockEntity entity = slice.getBlockEntity(blockPos);

                            if (entity != null && ExtendedBlockEntityType.shouldRender(entity.getType(), slice, blockPos, entity)) {
                                BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);

                                if (renderer != null) {
                                    renderData.addBlockEntity(entity, !renderer.shouldRenderOffScreen());
                                }
                            }
                        }

                        if (blockState.isSolidRender()) {
                            occluder.setOpaque(blockPos);
                        }
                    }
                }
            }
            int[] nativeQuads = forceJavaProducers ? new int[] { 0, 0, 0 } : nativeSectionSnapshot.flushAll(collector);
            fallbackStats.recordNativeSnapshotQuads(nativeQuads[0], nativeQuads[1], nativeQuads[2]);
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getReport(), slice, blockPos);
        } catch (Exception ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.forThrowable(ex, "Encountered exception while building chunk meshes"), slice, blockPos);
        }
        profiler.popPush("mesh appenders");

        int appenderFallbackQuadStart = buffers.getFallbackConsumerEmittedQuadCount();
        PlatformLevelRenderHooks.INSTANCE.runChunkMeshAppenders(this.renderContext.getRenderers(), type -> buffers.get(DefaultMaterials.forChunkLayer(type)).asFallbackVertexConsumer(DefaultMaterials.forChunkLayer(type), collector),
                slice);
        fallbackStats.recordAppenderFallbackQuads(buffers.getFallbackConsumerEmittedQuadCount() - appenderFallbackQuadStart);
        int fallbackQuadCount = (blockRenderer.getEmittedQuadCount() - fallbackQuadStart)
                + cache.getFluidRenderer().getEmittedQuadCount()
                + buffers.getFallbackConsumerEmittedQuadCount();
        if (fallbackBlockCount != 0 || fallbackQuadCount != 0) {
            renderData.setNativeMeshingFallbackCounts(fallbackBlockCount, fallbackQuadCount);
        }
        fallbackStats.report(this.render.getSectionIndex(), new BlockPos(minX, minY, minZ));

        blockRenderer.release();

        SortType sortType = SortType.NONE;
        if (sortEnabled) {
            sortType = collector.finishRendering();
        }

        // cancellation opportunity right before translucent sorting
        if (cancellationToken.isCancelled()) {
            profiler.pop();
            return null;
        }
        profiler.popPush("translucency sorting");

        boolean reuseUploadedData = false;
        TranslucentData translucentData = null;
        if (sortEnabled) {
            TranslucentData oldData = this.render.getTranslucentData();
            
            // Reusing non-dynamic data leads to attempting to sort with it again,
            // which throws an exception since it can only generate a sorter once.
            // To prevent this, reusing data is prevented when forceSort is enabled and the data is not dynamic.
            if (this.forceSort && !(oldData instanceof DynamicData)) {
                oldData = null;
            }
            
            translucentData = collector.getTranslucentData(oldData, this);
            reuseUploadedData = !this.forceSort && translucentData == oldData;
        }

        profiler.popPush("meshing");

        Map<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();
        var visibleSlices = SectionRenderDataStorage.getVisibleFaces(
                (int) this.absoluteCameraPos.x(), (int) this.absoluteCameraPos.y(), (int) this.absoluteCameraPos.z(),
                this.render.getChunkX(), this.render.getChunkY(), this.render.getChunkZ());

        if (translucentData != null && translucentData.meshesWereModified()) {
            meshes.put(DefaultTerrainRenderPasses.TRANSLUCENT, buffers.createModifiedTranslucentMesh(translucentData.getUpdatedQuads()));
            renderData.addRenderPass(DefaultTerrainRenderPasses.TRANSLUCENT);
        }

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            if (meshes.containsKey(pass)) {
                continue;
            }

            // if the translucent geometry needs to share an index buffer between the directions,
            // consolidate all translucent geometry into UNASSIGNED
            boolean translucentBehavior = sortEnabled && pass.isTranslucent();
            boolean forceUnassigned = translucentBehavior && sortType.needsDirectionMixing;
            boolean sliceReordering = !translucentBehavior || sortType.allowSliceReordering;
            BuiltSectionMeshParts mesh = buffers.createMesh(pass, visibleSlices, forceUnassigned, sliceReordering);

            if (mesh != null) {
                meshes.put(pass, mesh);
                renderData.addRenderPass(pass);
            }
        }

        renderData.setOcclusionData(occluder.resolve());
        var output = new ChunkBuildOutput(this.render, this.submitTime, translucentData, renderData.build(), meshes);

        if (sortEnabled) {
            if (reuseUploadedData) {
                output.markAsReusingUploadedData();
            } else if (translucentData instanceof PresentTranslucentData present) {
                var sorter = present.getSorter();
                sorter.writeIndexBuffer(this, true);
                output.setSorter(sorter);
            }
        }

        profiler.pop();

        return output;
    }

    private ReportedException fillCrashInfo(CrashReport report, LevelSlice slice, BlockPos pos) {
        CrashReportCategory crashReportSection = report.addCategory("Block being rendered", 1);

        BlockState state = null;
        try {
            state = slice.getBlockState(pos);
        } catch (Exception ignored) {}
        CrashReportCategory.populateBlockDetails(crashReportSection, slice, pos, state);

        crashReportSection.setDetail("Chunk section", this.render);
        if (this.renderContext != null) {
            crashReportSection.setDetail("Render context volume", this.renderContext.getVolume());
        }

        return new ReportedException(report);
    }

    @Override
    public long estimateTaskSizeWith(MeshTaskSizeEstimator estimator) {
        return estimator.estimateSize(this.render);
    }

    private static final class NativeSectionSnapshot implements AutoCloseable {
        private final ChunkBuildBuffers buffers;
        private final int sectionIndex;
        private final int minX;
        private final int minY;
        private final int minZ;
        private long address;

        private NativeSectionSnapshot(ChunkBuildBuffers buffers, int sectionIndex, int minX, int minY, int minZ) {
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

        private void appendBlock(int localBlockIndex, LevelSlice slice, BlockState blockState, BlockPos blockPos,
                int localX, int localY, int localZ, boolean suppressNativeFluid) {
            long recordAddress = this.recordAddress(localBlockIndex);
            long seed = blockState.getSeed(blockPos);
            int[] lightWords = new int[27];
            int[] neighborhoodStateIds = new int[27];
            int neighborhoodIndex = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int sampleX = blockPos.getX() + dx;
                        int sampleY = blockPos.getY() + dy;
                        int sampleZ = blockPos.getZ() + dz;
                        BlockState sampleState = slice.getBlockState(sampleX, sampleY, sampleZ);
                        neighborhoodStateIds[neighborhoodIndex] = NativeStaticBlockModelRegistry.getStateId(sampleState);
                        lightWords[neighborhoodIndex] = computeLightWord(slice, sampleState, sampleX, sampleY, sampleZ);
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
                    lightWords, neighborhoodStateIds, tint, fluidTint, (float) flow.x, (float) flow.z,
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

        private int[] flushAll(TranslucentGeometryCollector collector) {
            NativeMeshingDiagnostics.dumpSectionSnapshot(this.sectionIndex, this.minX, this.minY, this.minZ,
                    this.address, SECTION_BLOCK_COUNT);
            int solid = this.buffers.appendNativeSectionSnapshot(DefaultTerrainRenderPasses.SOLID, this.address, SECTION_BLOCK_COUNT,
                    0, this.sectionIndex, false, null);
            int cutout = this.buffers.appendNativeSectionSnapshot(DefaultTerrainRenderPasses.CUTOUT, this.address, SECTION_BLOCK_COUNT,
                    1, this.sectionIndex, false, null);
            int translucent = this.buffers.appendNativeSectionSnapshot(DefaultTerrainRenderPasses.TRANSLUCENT, this.address,
                    SECTION_BLOCK_COUNT, 2, this.sectionIndex, false, collector);
            return new int[] { solid, cutout, translucent };
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

        private static boolean isNativeFluidSupported(FluidState fluidState) {
            return NativeStaticBlockModelRegistry.isNativeFluidSupported(fluidState);
        }
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
            blockLight = slice.getBrightness(LightLayer.BLOCK, pos);
            skyLight = slice.getBrightness(LightLayer.SKY, pos);
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
