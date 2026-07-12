package net.sodium.client.perf.real;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.hooks.GameHooks;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.util.task.CancellationToken;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.joml.Vector3d;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RealChunkMeshingReplayRunner implements GameHooks {
    private static final String ENABLE_PROPERTY = "mattmc.realMeshingReplay";
    private static final String OUTPUT_PROPERTY = "mattmc.realMeshingReplay.output";
    private static final String WARMUP_PROPERTY = "mattmc.realMeshingReplay.warmup";
    private static final String MEASURE_PROPERTY = "mattmc.realMeshingReplay.measure";
    private static final String FIXTURE_PROPERTY = "mattmc.realMeshingReplay.fixture";
    private static final String WORLD_NAME = "MattMC Real Chunk Meshing Replay";
    private static final long WORLD_SEED = 0x4d6174744d435265L;
    private static final int SECTION_X = 0;
    private static final int SECTION_Y = 4;
    private static final int SECTION_Z = 0;
    private static final int SETTLE_TICKS_AFTER_LEVEL = 1;
    private static final int SETTLE_TICKS_AFTER_POPULATE = 1;

    private enum Phase {
        WAITING_FOR_LEVEL,
        POPULATING,
        WAITING_FOR_LIGHT,
        RUNNING,
        DONE
    }

    private final Path outputPath;
    private final int warmupIterations;
    private final int measurementIterations;
    private final List<Fixture> fixtures;
    private Phase phase = Phase.WAITING_FOR_LEVEL;
    private int ticksInPhase;
    private int centerSectionX;
    private int centerSectionZ;

    private RealChunkMeshingReplayRunner(Path outputPath, int warmupIterations, int measurementIterations,
            String selectedFixture) {
        this.outputPath = outputPath;
        this.warmupIterations = warmupIterations;
        this.measurementIterations = measurementIterations;
        List<Fixture> allFixtures = List.of(
                new Fixture("dense_cube"),
                new Fixture("normal_surface"),
                new Fixture("foliage_tinted"),
                new Fixture("weighted_multipart"),
                new Fixture("fluid_heavy"),
                new Fixture("waterlogged"),
                new Fixture("translucent_heavy"),
                new Fixture("complex_static")
        );
        if (selectedFixture == null || selectedFixture.isBlank()) {
            this.fixtures = allFixtures;
        } else {
            this.fixtures = allFixtures.stream()
                    .filter(fixture -> fixture.name.equals(selectedFixture))
                    .toList();
            if (this.fixtures.isEmpty()) {
                throw new IllegalArgumentException("Unknown real chunk meshing replay fixture: " + selectedFixture);
            }
        }
    }

    public static void installIfRequested() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        String output = System.getProperty(OUTPUT_PROPERTY);
        if (output == null || output.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + OUTPUT_PROPERTY);
        }

        int warmup = Integer.getInteger(WARMUP_PROPERTY, 30);
        int measure = Integer.getInteger(MEASURE_PROPERTY, 80);
        net.minecraft.hooks.HookRegistry.registerGameHook(new RealChunkMeshingReplayRunner(Path.of(output), warmup,
                measure, System.getProperty(FIXTURE_PROPERTY)));
    }

    @Override
    public void onGameInitialized(Minecraft minecraft) {
        LevelSettings settings = new LevelSettings(
                WORLD_NAME,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()),
                WorldDataConfiguration.DEFAULT
        );

        minecraft.createWorldOpenFlows().createFreshLevel(
                uniqueWorldId(),
                settings,
                new WorldOptions(WORLD_SEED, false, false),
                WorldPresets::createNormalWorldDimensions,
                null
        );
    }

    @Override
    public void afterRunTick(Minecraft minecraft, boolean tick) {
        if (!tick || this.phase == Phase.DONE) {
            return;
        }

        try {
            this.tick(minecraft);
        } catch (Throwable throwable) {
            this.phase = Phase.DONE;
            try {
                this.writeFailure(throwable);
            } catch (IOException ignored) {
            }
            throwable.printStackTrace();
            minecraft.stop();
        }
    }

    private void tick(Minecraft minecraft) throws Exception {
        this.ticksInPhase++;

        if (this.phase == Phase.WAITING_FOR_LEVEL) {
            if (minecraft.level == null || minecraft.player == null || this.ticksInPhase < SETTLE_TICKS_AFTER_LEVEL) {
                return;
            }

            this.centerSectionX = SECTION_X;
            this.centerSectionZ = SECTION_Z;
            this.phase = Phase.POPULATING;
            this.ticksInPhase = 0;
            return;
        }

        if (this.phase == Phase.POPULATING) {
            if (!this.fixtureChunksLoaded(minecraft, this.centerSectionX, this.centerSectionZ)) {
                return;
            }

            clearFixtureVolume(minecraft, this.centerSectionX, this.centerSectionZ);
            this.phase = Phase.WAITING_FOR_LIGHT;
            this.ticksInPhase = 0;
            return;
        }

        if (this.phase == Phase.WAITING_FOR_LIGHT) {
            if (this.ticksInPhase < SETTLE_TICKS_AFTER_POPULATE) {
                return;
            }

            this.phase = Phase.RUNNING;
            this.ticksInPhase = 0;
            return;
        }

        if (this.phase == Phase.RUNNING) {
            ResultDocument document = this.runBenchmarks(minecraft);
            this.writeResults(document);
            this.phase = Phase.DONE;
            minecraft.stop();
        }
    }

    private boolean fixtureChunksLoaded(Minecraft minecraft, int sectionX, int sectionZ) {
        for (int x = sectionX - 1; x <= sectionX + 1; x++) {
            for (int z = sectionZ - 1; z <= sectionZ + 1; z++) {
                try {
                    LevelChunk ignored = minecraft.level.getChunk(x, z);
                } catch (RuntimeException exception) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void clearFixtureVolume(Minecraft minecraft, int sectionX, int sectionZ) {
        int minX = SectionPos.sectionToBlockCoord(sectionX) - 16;
        int minY = SectionPos.sectionToBlockCoord(SECTION_Y) - 1;
        int minZ = SectionPos.sectionToBlockCoord(sectionZ) - 16;
        int maxX = SectionPos.sectionToBlockCoord(sectionX) + 31;
        int maxY = SectionPos.sectionToBlockCoord(SECTION_Y) + 16;
        int maxZ = SectionPos.sectionToBlockCoord(sectionZ) + 31;
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    minecraft.level.setBlock(new BlockPos(x, y, z), air, 3);
                }
            }
        }
    }

    private static void populateFixture(Minecraft minecraft, String fixture, int sectionX, int sectionZ) {
        switch (fixture) {
            case "empty" -> {
            }
            case "dense_cube" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> Blocks.STONE.defaultBlockState());
            case "normal_surface" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 7) return Blocks.STONE.defaultBlockState();
                if (y == 7) return Blocks.DIRT.defaultBlockState();
                if (y == 8) return Blocks.GRASS_BLOCK.defaultBlockState();
                if (y == 9 && ((x + z) & 7) == 0) return Blocks.SHORT_GRASS.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            });
            case "foliage_tinted" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 4) return Blocks.DIRT.defaultBlockState();
                if (y == 4) return Blocks.GRASS_BLOCK.defaultBlockState();
                if (y >= 7 && y <= 12 && ((x * 31 + z * 17 + y) & 3) != 0) return Blocks.OAK_LEAVES.defaultBlockState();
                if (y == 5 && ((x + z) & 2) == 0) return Blocks.FERN.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            });
            case "weighted_multipart" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 3) return Blocks.STONE.defaultBlockState();
                if (y == 3 && ((x + z) & 1) == 0) return fenceState(x, z);
                if (y == 4 && ((x * 13 + z) & 3) == 0) return Blocks.REDSTONE_WIRE.defaultBlockState()
                        .setValue(RedStoneWireBlock.POWER, (x + z) & 15);
                if (y == 5 && ((x + z) % 5) == 0) return Blocks.OAK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, switch ((x + z) & 3) {
                            case 0 -> net.minecraft.core.Direction.NORTH;
                            case 1 -> net.minecraft.core.Direction.SOUTH;
                            case 2 -> net.minecraft.core.Direction.WEST;
                            default -> net.minecraft.core.Direction.EAST;
                        })
                        .setValue(StairBlock.HALF, ((x ^ z) & 1) == 0 ? Half.BOTTOM : Half.TOP)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
                return Blocks.AIR.defaultBlockState();
            });
            case "fluid_heavy" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 3) return Blocks.STONE.defaultBlockState();
                if (y >= 3 && y <= 11) {
                    int level = (x + z + y) & 7;
                    return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
                }
                return Blocks.AIR.defaultBlockState();
            });
            case "waterlogged" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 4) return Blocks.STONE.defaultBlockState();
                if (y == 4) return Blocks.WATER.defaultBlockState();
                if (y == 5 && ((x + z) & 1) == 0) return Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                        .setValue(SlabBlock.WATERLOGGED, true);
                if (y == 5) return Blocks.SEAGRASS.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            });
            case "translucent_heavy" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 3) return Blocks.STONE.defaultBlockState();
                if (y >= 4 && y <= 12 && ((x + y + z) & 1) == 0) return Blocks.GLASS.defaultBlockState();
                if (y >= 4 && y <= 12) return Blocks.WATER.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            });
            case "complex_static" -> fill(minecraft, sectionX, sectionZ, (x, y, z) -> {
                if (y < 3) return Blocks.STONE_BRICKS.defaultBlockState();
                if (y == 3) return Blocks.GRASS_BLOCK.defaultBlockState();
                if (y == 4 && ((x + z) & 3) == 0) return Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                        .setValue(TrapDoorBlock.OPEN, ((x ^ z) & 1) == 0)
                        .setValue(TrapDoorBlock.WATERLOGGED, false);
                if (y == 5 && ((x * 7 + z) & 7) == 0) return Blocks.COBWEB.defaultBlockState();
                if (y == 6 && ((x + z) & 3) == 1) return Blocks.OAK_LEAVES.defaultBlockState();
                if (y == 7 && ((x + z) & 7) == 2) return Blocks.LANTERN.defaultBlockState();
                if (y == 8 && ((x + z) & 7) == 3) return Blocks.GLASS_PANE.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            });
            default -> throw new IllegalArgumentException("Unknown fixture: " + fixture);
        }
    }

    private static BlockState fenceState(int x, int z) {
        return Blocks.OAK_FENCE.defaultBlockState()
                .setValue(FenceBlock.NORTH, (z & 1) == 0)
                .setValue(FenceBlock.SOUTH, (z & 2) == 0)
                .setValue(FenceBlock.WEST, (x & 1) == 0)
                .setValue(FenceBlock.EAST, (x & 2) == 0)
                .setValue(BlockStateProperties.WATERLOGGED, false);
    }

    private static void fill(Minecraft minecraft, int sectionX, int sectionZ, StateFactory factory) {
        int originX = SectionPos.sectionToBlockCoord(sectionX);
        int originY = SectionPos.sectionToBlockCoord(SECTION_Y);
        int originZ = SectionPos.sectionToBlockCoord(sectionZ);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    minecraft.level.setBlock(new BlockPos(originX + x, originY + y, originZ + z), factory.state(x, y, z), 3);
                }
            }
        }
    }

    private ResultDocument runBenchmarks(Minecraft minecraft) throws IOException {
        List<FixtureResult> results = new ArrayList<>();

        try (CloseableChunkBuildContext context = new CloseableChunkBuildContext(minecraft.level)) {
            for (Fixture fixture : this.fixtures) {
                System.out.println("[RealReplay] running fixture " + fixture.name);
                clearFixtureVolume(minecraft, this.centerSectionX, this.centerSectionZ);
                populateFixture(minecraft, fixture.name, this.centerSectionX, this.centerSectionZ);
                minecraft.level.pollLightUpdates();
                results.add(this.runFixture(minecraft, context, fixture));
                this.writeResults(new ResultDocument(results));
                System.out.println("[RealReplay] finished fixture " + fixture.name);
            }
        }

        return new ResultDocument(results);
    }

    private FixtureResult runFixture(Minecraft minecraft, ChunkBuildContext context, Fixture fixture) {
        SectionPos sectionPos = SectionPos.of(this.centerSectionX, SECTION_Y, this.centerSectionZ);
        ClonedChunkSectionCache cache = new ClonedChunkSectionCache(minecraft.level);
        ChunkRenderContext renderContext = LevelSlice.prepare(minecraft.level, sectionPos, cache);

        if (renderContext == null) {
            return FixtureResult.empty(fixture.name);
        }

        for (int i = 0; i < this.warmupIterations; i++) {
            System.out.println("[RealReplay] warmup " + fixture.name + " " + (i + 1) + "/" + this.warmupIterations);
            ChunkBuildOutput output = executeTask(sectionPos, renderContext, context);
            if (output != null) {
                output.destroy();
            }
        }

        long[] times = new long[this.measurementIterations];
        ChunkSummary lastSummary = null;
        for (int i = 0; i < this.measurementIterations; i++) {
            System.out.println("[RealReplay] measure execute " + fixture.name + " " + (i + 1) + "/" + this.measurementIterations);
            long start = System.nanoTime();
            ChunkBuildOutput output = executeTask(sectionPos, renderContext, context);
            long elapsed = System.nanoTime() - start;
            times[i] = elapsed;
            System.out.println("[RealReplay] measure summarize " + fixture.name + " " + (i + 1) + "/" + this.measurementIterations);
            lastSummary = summarize(fixture.name, output);
            if (output != null) {
                System.out.println("[RealReplay] measure destroy " + fixture.name + " " + (i + 1) + "/" + this.measurementIterations);
                output.destroy();
            }
        }

        return new FixtureResult(fixture.name, false, times, lastSummary);
    }

    private static ChunkBuildOutput executeTask(SectionPos sectionPos, ChunkRenderContext renderContext,
                                                ChunkBuildContext context) {
        RenderRegion region = new RenderRegion(sectionPos.getX() >> 3, sectionPos.getY() >> 2, sectionPos.getZ() >> 3, null);
        RenderSection renderSection = new RenderSection(region, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
        ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(renderSection, 0,
                new Vector3d(sectionPos.minBlockX() + 8.0, sectionPos.minBlockY() + 8.0, sectionPos.minBlockZ() + 8.0),
                renderContext, SortBehavior.STATIC, true);
        return task.execute(context, NonCancellingToken.INSTANCE);
    }

    private static ChunkSummary summarize(String fixture, ChunkBuildOutput output) {
        if (output == null) {
            return ChunkSummary.empty();
        }

        int passCount = 0;
        int totalVertexBytes = 0;
        int totalVertices = 0;
        long checksum = 1469598103934665603L;
        List<PassSummary> passes = new ArrayList<>();

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            BuiltSectionMeshParts mesh = output.meshes.get(pass);
            if (mesh == null) {
                continue;
            }

            passCount++;
            ByteBuffer data = mesh.getVertexData().getDirectBuffer().duplicate();
            data.order(ByteOrder.nativeOrder());
            int bytes = data.remaining();
            int vertices = 0;
            for (int i = 0; i < mesh.getVertexSegments().length; i += 2) {
                vertices += mesh.getVertexSegments()[i];
            }
            totalVertexBytes += bytes;
            totalVertices += vertices;
            checksum = checksum(checksum, data.duplicate());
            passes.add(PassSummary.fromMesh(fixture, passName(pass), mesh, data.duplicate(), vertices, bytes));
        }

        int blockEntities = count(output.info.globalBlockEntities) + count(output.info.culledBlockEntities);
        List<String> animatedSpriteNames = animatedSpriteNames(output.info.animatedSprites);
        return new ChunkSummary(passCount, totalVertices, totalVertexBytes, checksum, blockEntities,
                animatedSpriteNames.size(), animatedSpriteNames, fallbackBlocks(output), fallbackQuads(output), passes);
    }

    private static long checksum(long seed, ByteBuffer data) {
        long hash = seed;
        while (data.hasRemaining()) {
            hash ^= data.get() & 0xffL;
            hash *= 1099511628211L;
        }
        return hash;
    }

    private static long checksum(ByteBuffer data) {
        return checksum(1469598103934665603L, data);
    }

    private static int count(Object[] array) {
        return array == null ? 0 : array.length;
    }

    private static List<String> animatedSpriteNames(net.minecraft.client.renderer.texture.TextureAtlasSprite[] sprites) {
        if (sprites == null || sprites.length == 0) {
            return List.of();
        }

        List<String> names = new ArrayList<>(sprites.length);
        for (var sprite : sprites) {
            if (sprite != null && sprite.contents() != null) {
                names.add(sprite.contents().name().toString());
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private static int fallbackBlocks(ChunkBuildOutput output) {
        return intField(output.info, "nativeMeshingFallbackBlocks");
    }

    private static int fallbackQuads(ChunkBuildOutput output) {
        return intField(output.info, "nativeMeshingFallbackQuads");
    }

    private static int intField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.getInt(target);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static String passName(TerrainRenderPass pass) {
        if (pass == DefaultTerrainRenderPasses.SOLID) return "solid";
        if (pass == DefaultTerrainRenderPasses.CUTOUT) return "cutout";
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) return "translucent";
        return "unknown";
    }

    private void writeResults(ResultDocument document) throws IOException {
        Files.createDirectories(this.outputPath.getParent());
        Files.writeString(this.outputPath, document.toJson());
    }

    private void writeFailure(Throwable throwable) throws IOException {
        Files.createDirectories(this.outputPath.getParent());
        Files.writeString(this.outputPath, "{\n" +
                "  \"status\": \"failed\",\n" +
                "  \"error\": " + quote(throwable.toString()) + "\n" +
                "}\n");
    }

    private static String uniqueWorldId() {
        return "mattmc-real-meshing-replay-" + Long.toUnsignedString(System.nanoTime(), 36).toLowerCase(Locale.ROOT);
    }

    private interface StateFactory {
        BlockState state(int x, int y, int z);
    }

    private enum NonCancellingToken implements CancellationToken {
        INSTANCE;

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelled() {
        }
    }

    private static final class CloseableChunkBuildContext extends ChunkBuildContext implements AutoCloseable {
        private CloseableChunkBuildContext(net.minecraft.client.multiplayer.ClientLevel level) {
            super(level, ChunkMeshFormats.COMPACT);
        }

        @Override
        public void close() {
            this.cleanup();
        }
    }

    private record Fixture(String name) {
    }

    private record ResultDocument(List<FixtureResult> fixtures) {
        private String toJson() {
            StringBuilder builder = new StringBuilder(16384);
            builder.append("{\n");
            builder.append("  \"status\": \"ok\",\n");
            builder.append("  \"timestamp\": ").append(quote(Instant.now().toString())).append(",\n");
            builder.append("  \"runner\": \"real-production-chunk-meshing\",\n");
            builder.append("  \"fixtures\": [\n");
            for (int i = 0; i < this.fixtures.size(); i++) {
                if (i > 0) builder.append(",\n");
                this.fixtures.get(i).appendJson(builder, "    ");
            }
            builder.append("\n  ]\n");
            builder.append("}\n");
            return builder.toString();
        }
    }

    private record FixtureResult(String name, boolean skippedByProductionEmptySection, long[] times,
                                 ChunkSummary summary) {
        private static FixtureResult empty(String name) {
            return new FixtureResult(name, true, new long[0], ChunkSummary.empty());
        }

        private void appendJson(StringBuilder builder, String indent) {
            builder.append(indent).append("{\n");
            builder.append(indent).append("  \"name\": ").append(quote(this.name)).append(",\n");
            builder.append(indent).append("  \"skipped_by_production_empty_section\": ").append(this.skippedByProductionEmptySection).append(",\n");
            builder.append(indent).append("  \"iterations\": ").append(this.times.length).append(",\n");
            builder.append(indent).append("  \"mean_ns\": ").append(mean(this.times)).append(",\n");
            builder.append(indent).append("  \"median_ns\": ").append(median(this.times)).append(",\n");
            builder.append(indent).append("  \"best_ns\": ").append(best(this.times)).append(",\n");
            builder.append(indent).append("  \"summary\": ");
            this.summary.appendJson(builder, indent + "  ");
            builder.append("\n").append(indent).append("}");
        }

        private static long mean(long[] values) {
            if (values.length == 0) return 0L;
            long total = 0L;
            for (long value : values) total += value;
            return total / values.length;
        }

        private static long median(long[] values) {
            if (values.length == 0) return 0L;
            long[] copy = values.clone();
            java.util.Arrays.sort(copy);
            return copy[copy.length / 2];
        }

        private static long best(long[] values) {
            if (values.length == 0) return 0L;
            long best = Long.MAX_VALUE;
            for (long value : values) best = Math.min(best, value);
            return best;
        }
    }

    private record ChunkSummary(int passCount, int totalVertices, int totalVertexBytes, long checksum,
                                int blockEntities, int animatedSprites, List<String> animatedSpriteNames,
                                int fallbackBlocks, int fallbackQuads,
                                List<PassSummary> passes) {
        private static ChunkSummary empty() {
            return new ChunkSummary(0, 0, 0, 1469598103934665603L, 0, 0, List.of(), 0, 0, List.of());
        }

        private void appendJson(StringBuilder builder, String indent) {
            builder.append("{\n");
            builder.append(indent).append("  \"pass_count\": ").append(this.passCount).append(",\n");
            builder.append(indent).append("  \"total_vertices\": ").append(this.totalVertices).append(",\n");
            builder.append(indent).append("  \"total_vertex_bytes\": ").append(this.totalVertexBytes).append(",\n");
            builder.append(indent).append("  \"checksum\": ").append(Long.toUnsignedString(this.checksum)).append(",\n");
            builder.append(indent).append("  \"block_entities\": ").append(this.blockEntities).append(",\n");
            builder.append(indent).append("  \"animated_sprites\": ").append(this.animatedSprites).append(",\n");
            builder.append(indent).append("  \"animated_sprite_names\": [");
            for (int i = 0; i < this.animatedSpriteNames.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append(quote(this.animatedSpriteNames.get(i)));
            }
            builder.append("],\n");
            builder.append(indent).append("  \"fallback_blocks\": ").append(this.fallbackBlocks).append(",\n");
            builder.append(indent).append("  \"fallback_quads\": ").append(this.fallbackQuads).append(",\n");
            builder.append(indent).append("  \"passes\": [");
            for (int i = 0; i < this.passes.size(); i++) {
                if (i > 0) builder.append(", ");
                this.passes.get(i).appendJson(builder);
            }
            builder.append("]\n");
            builder.append(indent).append("}");
        }
    }

    private record PassSummary(String name, int vertices, int bytes, long checksum, int[] vertexSegments,
                               List<CanonicalQuad> quads) {
        private static PassSummary fromMesh(String fixture, String name, BuiltSectionMeshParts mesh, ByteBuffer data, int vertices,
                                            int bytes) {
            int[] segments = mesh.getVertexSegments().clone();
            List<CanonicalQuad> quads = canonicalizeQuads(fixture, name, data, segments);
            return new PassSummary(name, vertices, bytes, RealChunkMeshingReplayRunner.checksum(data.duplicate()), segments, quads);
        }

        private void appendJson(StringBuilder builder) {
            builder.append("{\"name\": ").append(quote(this.name))
                    .append(", \"vertices\": ").append(this.vertices)
                    .append(", \"bytes\": ").append(this.bytes)
                    .append(", \"checksum\": ").append(Long.toUnsignedString(this.checksum))
                    .append(", \"vertex_segments\": [");
            for (int i = 0; i < this.vertexSegments.length; i++) {
                if (i > 0) builder.append(", ");
                builder.append(this.vertexSegments[i]);
            }
            builder.append("], \"canonical_quads\": [");
            for (int i = 0; i < this.quads.size(); i++) {
                if (i > 0) builder.append(", ");
                this.quads.get(i).appendJson(builder);
            }
            builder.append("]}");
        }
    }

    private static List<CanonicalQuad> canonicalizeQuads(String fixture, String pass, ByteBuffer data, int[] vertexSegments) {
        List<CanonicalQuad> quads = new ArrayList<>();
        int vertexCursor = 0;
        int stride = 20;

        for (int segmentIndex = 0; segmentIndex < vertexSegments.length; segmentIndex += 2) {
            int vertexCount = vertexSegments[segmentIndex];
            if (vertexCount == 0) {
                continue;
            }

            int facingIndex = vertexSegments[segmentIndex + 1];
            String facing = facingName(facingIndex);
            for (int localVertex = 0; localVertex < vertexCount; localVertex += 4) {
                CanonicalVertex[] vertices = new CanonicalVertex[4];
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = (vertexCursor + localVertex + vertex) * stride;
                    int positionHi = data.getInt(offset);
                    int positionLo = data.getInt(offset + 4);
                    int color = data.getInt(offset + 8);
                    int texture = data.getInt(offset + 12);
                    int lightMaterialSection = data.getInt(offset + 16);
                    vertices[vertex] = new CanonicalVertex(positionHi, positionLo, color, texture, lightMaterialSection);
                }
                quads.add(CanonicalQuad.from(fixture, pass, facing, facingIndex, vertices));
            }
            vertexCursor += vertexCount;
        }

        quads.sort(Comparator
                .comparing(CanonicalQuad::sortKey)
                .thenComparing(CanonicalQuad::rawKey));
        return quads;
    }

    private static String facingName(int facingIndex) {
        if (facingIndex >= 0 && facingIndex < ModelQuadFacing.VALUES.length) {
            return ModelQuadFacing.VALUES[facingIndex].name();
        }
        return "UNKNOWN_" + facingIndex;
    }

    private record CanonicalQuad(String pass, String facing, int facingIndex, int blockX, int blockY, int blockZ,
                                 String blockState, String producer, CanonicalVertex[] vertices, String sortKey,
                                 String rawKey) {
        private static CanonicalQuad from(String fixture, String pass, String facing, int facingIndex,
                                          CanonicalVertex[] vertices) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            StringBuilder raw = new StringBuilder(256);
            for (CanonicalVertex vertex : vertices) {
                int x = vertex.x20();
                int y = vertex.y20();
                int z = vertex.z20();
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
                raw.append(vertex.rawKey()).append('|');
            }

            int blockX = inferredBlockCoordinate(facing, 0, minX, maxX);
            int blockY = inferredBlockCoordinate(facing, 1, minY, maxY);
            int blockZ = inferredBlockCoordinate(facing, 2, minZ, maxZ);
            BlockState state = fixtureState(fixture, blockX, blockY, blockZ);
            String stateName = state.toString();
            String producer = inferProducer(pass, state);
            String sort = pass + '/' + producer + '/' + stateName + '/' + facing + '/' + blockX + '/' + blockY + '/' + blockZ + '/' + raw;
            return new CanonicalQuad(pass, facing, facingIndex, blockX, blockY, blockZ, stateName, producer,
                    vertices, sort, raw.toString());
        }

        private void appendJson(StringBuilder builder) {
            builder.append("{\"pass\": ").append(quote(this.pass))
                    .append(", \"producer\": ").append(quote(this.producer))
                    .append(", \"block_state\": ").append(quote(this.blockState))
                    .append(", \"facing\": ").append(quote(this.facing))
                    .append(", \"block\": [").append(this.blockX).append(", ").append(this.blockY).append(", ").append(this.blockZ).append("]")
                    .append(", \"vertices\": [");
            for (int i = 0; i < this.vertices.length; i++) {
                if (i > 0) builder.append(", ");
                this.vertices[i].appendJson(builder);
            }
            builder.append("]}");
        }
    }

    private static String inferProducer(String pass, BlockState state) {
        if (!state.getFluidState().isEmpty() && "translucent".equals(pass)) {
            return "fluid";
        }
        return "model";
    }

    private static BlockState fixtureState(String fixture, int x, int y, int z) {
        return switch (fixture) {
            case "dense_cube" -> Blocks.STONE.defaultBlockState();
            case "normal_surface" -> {
                if (y < 7) yield Blocks.STONE.defaultBlockState();
                if (y == 7) yield Blocks.DIRT.defaultBlockState();
                if (y == 8) yield Blocks.GRASS_BLOCK.defaultBlockState();
                if (y == 9 && ((x + z) & 7) == 0) yield Blocks.SHORT_GRASS.defaultBlockState();
                yield Blocks.AIR.defaultBlockState();
            }
            case "foliage_tinted" -> {
                if (y < 4) yield Blocks.DIRT.defaultBlockState();
                if (y == 4) yield Blocks.GRASS_BLOCK.defaultBlockState();
                if (y >= 7 && y <= 12 && ((x * 31 + z * 17 + y) & 3) != 0) yield Blocks.OAK_LEAVES.defaultBlockState();
                if (y == 5 && ((x + z) & 2) == 0) yield Blocks.FERN.defaultBlockState();
                yield Blocks.AIR.defaultBlockState();
            }
            case "weighted_multipart" -> {
                if (y < 3) yield Blocks.STONE.defaultBlockState();
                if (y == 3 && ((x + z) & 1) == 0) yield fenceState(x, z);
                if (y == 4 && ((x * 13 + z) & 3) == 0) yield Blocks.REDSTONE_WIRE.defaultBlockState()
                        .setValue(RedStoneWireBlock.POWER, (x + z) & 15);
                if (y == 5 && ((x + z) % 5) == 0) yield Blocks.OAK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, switch ((x + z) & 3) {
                            case 0 -> net.minecraft.core.Direction.NORTH;
                            case 1 -> net.minecraft.core.Direction.SOUTH;
                            case 2 -> net.minecraft.core.Direction.WEST;
                            default -> net.minecraft.core.Direction.EAST;
                        })
                        .setValue(StairBlock.HALF, ((x ^ z) & 1) == 0 ? Half.BOTTOM : Half.TOP)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
                yield Blocks.AIR.defaultBlockState();
            }
            case "fluid_heavy" -> {
                if (y < 3) yield Blocks.STONE.defaultBlockState();
                if (y >= 3 && y <= 11) yield Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, (x + z + y) & 7);
                yield Blocks.AIR.defaultBlockState();
            }
            case "waterlogged" -> {
                if (y < 4) yield Blocks.STONE.defaultBlockState();
                if (y == 4) yield Blocks.WATER.defaultBlockState();
                if (y == 5 && ((x + z) & 1) == 0) yield Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                        .setValue(SlabBlock.WATERLOGGED, true);
                if (y == 5) yield Blocks.SEAGRASS.defaultBlockState();
                yield Blocks.AIR.defaultBlockState();
            }
            case "translucent_heavy" -> {
                if (y < 3) yield Blocks.STONE.defaultBlockState();
                if (y >= 4 && y <= 12 && ((x + y + z) & 1) == 0) yield Blocks.GLASS.defaultBlockState();
                if (y >= 4 && y <= 12) yield Blocks.WATER.defaultBlockState();
                yield Blocks.AIR.defaultBlockState();
            }
            case "complex_static" -> {
                if (y < 3) yield Blocks.STONE_BRICKS.defaultBlockState();
                if (y == 3) yield Blocks.GRASS_BLOCK.defaultBlockState();
                if (y == 4 && ((x + z) & 3) == 0) yield Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                        .setValue(TrapDoorBlock.OPEN, ((x ^ z) & 1) == 0)
                        .setValue(TrapDoorBlock.WATERLOGGED, false);
                if (y == 5 && ((x * 7 + z) & 7) == 0) yield Blocks.COBWEB.defaultBlockState();
                if (y == 6 && ((x + z) & 3) == 1) yield Blocks.OAK_LEAVES.defaultBlockState();
                if (y == 7 && ((x + z) & 7) == 2) yield Blocks.LANTERN.defaultBlockState();
                if (y == 8 && ((x + z) & 7) == 3) yield Blocks.GLASS_PANE.defaultBlockState();
                yield Blocks.AIR.defaultBlockState();
            }
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static int inferredBlockCoordinate(String facing, int axis, int min, int max) {
        int minBlock = quantizedToBlock(min);
        int maxBlock = quantizedToBlock(max);
        if ((axis == 0 && "POS_X".equals(facing)) || (axis == 1 && "POS_Y".equals(facing)) ||
                (axis == 2 && "POS_Z".equals(facing))) {
            return clampBlock(maxBlock - 1);
        }
        return clampBlock(minBlock);
    }

    private static int quantizedToBlock(int quantized) {
        return (int) Math.floor(((quantized / (double) (1 << 20)) * 32.0) - 8.0 + 1.0e-7);
    }

    private static int clampBlock(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private record CanonicalVertex(int positionHi, int positionLo, int color, int texture, int lightMaterialSection) {
        private int x20() {
            return ((this.positionHi & 0x3ff) << 10) | (this.positionLo & 0x3ff);
        }

        private int y20() {
            return (((this.positionHi >>> 10) & 0x3ff) << 10) | ((this.positionLo >>> 10) & 0x3ff);
        }

        private int z20() {
            return (((this.positionHi >>> 20) & 0x3ff) << 10) | ((this.positionLo >>> 20) & 0x3ff);
        }

        private String rawKey() {
            return Integer.toUnsignedString(this.positionHi) + ',' +
                    Integer.toUnsignedString(this.positionLo) + ',' +
                    Integer.toUnsignedString(this.color) + ',' +
                    Integer.toUnsignedString(this.texture) + ',' +
                    Integer.toUnsignedString(this.lightMaterialSection);
        }

        private void appendJson(StringBuilder builder) {
            builder.append("{\"pos\": [").append(this.x20()).append(", ").append(this.y20()).append(", ").append(this.z20())
                    .append("], \"color\": ").append(Integer.toUnsignedString(this.color))
                    .append(", \"texture\": ").append(Integer.toUnsignedString(this.texture))
                    .append(", \"light_material_section\": ").append(Integer.toUnsignedString(this.lightMaterialSection))
                    .append("}");
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
