package net.sodium.client.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.sodium.client.render.chunk.ExtendedBlockEntityType;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
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
import net.sodium.client.util.task.CancellationToken;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
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
                        boolean useJavaFluid = !fluidState.isEmpty()
                                && (forceJavaFluids || !nativeFluidSupported);

                        if (!forceJavaProducers) {
                            nativeSectionSnapshot.appendBlock(localBlockIndex, slice, blockState, blockPos, localX,
                                    localY, localZ, useJavaFluid);
                        }

                        if (blockState.getRenderShape() == RenderShape.MODEL) {
                            if (forceJavaModels || !NativeStaticBlockModelRegistry.hasNativeModel(blockState)) {
                                NativeMeshingCompatibilityFallback.renderModel(cache, blockRenderer, blockState,
                                        blockPos, modelOffset, fallbackStats);
                                fallbackBlockCount++;
                            } else {
                                for (var sprite : NativeStaticBlockModelRegistry.getSprites(blockState)) {
                                    buffers.get(DefaultMaterials.forBlockState(blockState).pass).addSprite(sprite);
                                }
                                fallbackStats.recordNativeModelBlock();
                            }
                        }

                        if (useJavaFluid) {
                            NativeMeshingCompatibilityFallback.renderFluid(cache, buffers, slice, blockState,
                                    fluidState, blockPos, modelOffset, collector, fallbackStats);
                            fallbackBlockCount++;
                        } else if (!fluidState.isEmpty()) {
                            for (var sprite : NativeStaticBlockModelRegistry.getFluidSprites(fluidState)) {
                                buffers.get(DefaultMaterials.forFluidState(fluidState).pass).addSprite(sprite);
                            }
                            fallbackStats.recordNativeFluidBlock(fluidState);
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

        NativeMeshingCompatibilityFallback.runMeshAppenders(this.renderContext, buffers, slice, collector,
                fallbackStats);
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

}
