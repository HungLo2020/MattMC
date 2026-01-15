package net.minecraft.world.level.chunk;

import net.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.SystemReport;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Performance test for chunk generation using actual Minecraft generation pathways.
 * This test creates a real Minecraft server environment and generates chunks through
 * the standard world generation pipeline to get realistic performance metrics.
 * 
 * Requirements:
 * - Uses fixed seed for reproducibility
 * - Generates 100 chunks using real Minecraft generation
 * - Reports: total time, average time, fastest chunk, slowest chunk
 * - May require graphics context for full generation pipeline
 */
@DisplayName("Chunk Generation Performance Test (100 chunks)")
public class ChunkGenerationPerformanceTest {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_COUNT = 100;
    private static final long WORLD_SEED = 12345L; // Fixed seed for reproducibility
    private static final String TEST_WORLD_DIR = "test-chunk-gen-world";
    private static Path testWorldPath;
    
    @BeforeAll
    static void setup() throws IOException {
        // Bootstrap Minecraft
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Util.startTimerHackThread();
        
        // Set up test world directory
        testWorldPath = Paths.get(TEST_WORLD_DIR);
        cleanupTestWorld();
        Files.createDirectories(testWorldPath);
        
        LOGGER.info("ChunkGenerationPerformanceTest setup complete");
    }
    
    @AfterAll
    static void cleanup() {
        // Clean up test world directory after tests
        cleanupTestWorld();
        LOGGER.info("ChunkGenerationPerformanceTest cleanup complete");
    }
    
    private static void cleanupTestWorld() {
        if (testWorldPath != null && Files.exists(testWorldPath)) {
            try {
                // Delete recursively
                try (Stream<Path> walk = Files.walk(testWorldPath)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                LOGGER.warn("Failed to delete: " + path, e);
                            }
                        });
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to clean up test world", e);
            }
        }
    }
    
    @Test
    @DisplayName("should generate 100 chunks and measure performance")
    void testChunkGeneration100Chunks() throws Exception {
        LOGGER.info("Starting chunk generation performance test for {} chunks with seed {}", CHUNK_COUNT, WORLD_SEED);
        
        // Create level storage
        LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(testWorldPath);
        LevelStorageSource.LevelStorageAccess storageAccess = levelStorageSource.createAccess("testworld");
        
        try {
            // Set up pack repository
            PackRepository packRepository = ServerPacksSource.createPackRepository(storageAccess);
            packRepository.reload();
            
            // Configure world with all features enabled
            List<String> availablePacks = new ArrayList<>(packRepository.getAvailableIds());
            availablePacks.remove("vanilla");
            availablePacks.addFirst("vanilla");
            
            FeatureFlagSet enabledFeatures = FeatureFlags.REGISTRY.allFlags()
                .subtract(FeatureFlagSet.of(FeatureFlags.REDSTONE_EXPERIMENTS, FeatureFlags.MINECART_IMPROVEMENTS));
            
            WorldDataConfiguration dataConfiguration = new WorldDataConfiguration(
                new DataPackConfig(availablePacks, List.of()), 
                enabledFeatures
            );
            
            // Create level settings with our fixed seed
            LevelSettings levelSettings = new LevelSettings(
                "ChunkGenPerfTest",
                GameType.CREATIVE,
                false,
                Difficulty.NORMAL,
                true,
                new GameRules(enabledFeatures),
                dataConfiguration
            );
            
            // Load world data
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, dataConfiguration, false, true);
            WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, 4);
            
            WorldStem worldStem = Util.blockUntilDone(
                executor -> WorldLoader.load(
                    initConfig,
                    dataLoadContext -> {
                        // Create world with fixed seed
                        WorldOptions worldOptions = new WorldOptions(WORLD_SEED, false, false);
                        Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
                        
                        WorldDimensions.Complete complete = dataLoadContext.datapackWorldgen()
                            .lookupOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.NORMAL)
                            .value()
                            .createWorldDimensions()
                            .bake(registry);
                        
                        return new WorldLoader.DataLoadOutput<>(
                            new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), complete.lifecycle()),
                            complete.dimensionsRegistryAccess()
                        );
                    },
                    WorldStem::new,
                    Util.backgroundExecutor(),
                    executor
                )
            ).get();
            
            LayeredRegistryAccess<RegistryLayer> registries = worldStem.registries();
            
            // Create a minimal test server
            TestMinecraftServer server = new TestMinecraftServer(
                Thread.currentThread(),
                storageAccess,
                packRepository,
                worldStem,
                registries.compositeAccess()
            );
            
            // Initialize the server
            server.initServer();
            
            // Get the overworld level
            ServerLevel level = server.overworld();
            
            // Track chunk generation times
            List<Long> chunkTimes = new ArrayList<>(CHUNK_COUNT);
            long totalStartTime = System.nanoTime();
            
            // Generate chunks in a grid pattern around spawn (0, 0)
            int gridSize = (int) Math.ceil(Math.sqrt(CHUNK_COUNT));
            int chunksGenerated = 0;
            
            for (int x = 0; x < gridSize && chunksGenerated < CHUNK_COUNT; x++) {
                for (int z = 0; z < gridSize && chunksGenerated < CHUNK_COUNT; z++) {
                    ChunkPos chunkPos = new ChunkPos(x, z);
                    
                    long chunkStartTime = System.nanoTime();
                    
                    // Generate chunk through the real pipeline
                    // This uses ChunkStatus.FULL to ensure complete generation including lighting
                    ChunkAccess chunk = level.getChunkSource().getChunk(
                        chunkPos.x,
                        chunkPos.z,
                        ChunkStatus.FULL,
                        true
                    );
                    
                    long chunkEndTime = System.nanoTime();
                    long chunkDuration = chunkEndTime - chunkStartTime;
                    chunkTimes.add(chunkDuration);
                    
                    chunksGenerated++;
                    
                    if (chunksGenerated % 10 == 0) {
                        LOGGER.info("Generated {}/{} chunks", chunksGenerated, CHUNK_COUNT);
                    }
                }
            }
            
            long totalEndTime = System.nanoTime();
            long totalDuration = totalEndTime - totalStartTime;
            
            // Calculate statistics
            double totalMs = totalDuration / 1_000_000.0;
            double averageMs = chunkTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0) / 1_000_000.0;
            
            long fastestNs = chunkTimes.stream()
                .min(Long::compareTo)
                .orElse(0L);
            double fastestMs = fastestNs / 1_000_000.0;
            
            long slowestNs = chunkTimes.stream()
                .max(Long::compareTo)
                .orElse(0L);
            double slowestMs = slowestNs / 1_000_000.0;
            
            // Print results
            System.out.println("\n========================================");
            System.out.println("Chunk Generation Performance Test Results");
            System.out.println("========================================");
            System.out.printf("World Seed: %d%n", WORLD_SEED);
            System.out.printf("Chunks Generated: %d%n", CHUNK_COUNT);
            System.out.printf("Total Time: %.2f ms (%.2f seconds)%n", totalMs, totalMs / 1000.0);
            System.out.printf("Average Time per Chunk: %.2f ms%n", averageMs);
            System.out.printf("Fastest Chunk: %.2f ms%n", fastestMs);
            System.out.printf("Slowest Chunk: %.2f ms%n", slowestMs);
            System.out.printf("Chunks per Second: %.2f%n", CHUNK_COUNT / (totalMs / 1000.0));
            System.out.println("========================================\n");
            
            // Shutdown server
            server.halt(false);
            
        } finally {
            storageAccess.close();
        }
    }
    
    /**
     * Minimal test server implementation for chunk generation testing.
     * This provides just enough functionality to initialize a world and generate chunks.
     */
    private static class TestMinecraftServer extends MinecraftServer {
        private final WorldStem worldStem;
        private final LocalSampleLogger sampleLogger = new LocalSampleLogger(4);
        
        public TestMinecraftServer(
            Thread thread,
            LevelStorageSource.LevelStorageAccess storageAccess,
            PackRepository packRepository,
            WorldStem worldStem,
            RegistryAccess.Frozen registryAccess
        ) {
            super(
                thread,
                storageAccess,
                packRepository,
                worldStem,
                Proxy.NO_PROXY,
                DataFixers.getDataFixer(),
                new Services(new TestUserNameToIdResolver(), new TestProfileResolver()),
                LoggingLevelLoadListener.forDedicatedServer()
            );
            this.worldStem = worldStem;
        }
        
        @Override
        public boolean initServer() {
            // Initialize player list before loading the world
            this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, new EmptyNotificationService()) {});
            // Initialize and load the world
            this.loadLevel();
            return true;
        }
        
        @Override
        protected SampleLogger getTickTimeLogger() {
            return this.sampleLogger;
        }
        
        @Override
        public boolean isDedicatedServer() {
            return true;
        }
        
        @Override
        public boolean isSingleplayerOwner(NameAndId nameAndId) {
            return false;
        }
        
        @Override
        public boolean shouldInformAdmins() {
            return true;
        }
        
        @Override
        public boolean isPublished() {
            return false;
        }
        
        @Override
        public int operatorUserPermissionLevel() {
            return 4;
        }
        
        @Override
        public int getFunctionCompilationLevel() {
            return 2;
        }
        
        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }
        
        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }
        
        @Override
        public SystemReport fillServerSystemReport(SystemReport systemReport) {
            return systemReport;
        }
        
        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }
        
        @Override
        public boolean isEpollEnabled() {
            return false;
        }
        
        @Override
        public String getMotd() {
            return "ChunkGenTest";
        }
        
        @Override
        public String getServerVersion() {
            return "1.21.10";
        }
        
        @Override
        public int getPlayerCount() {
            return 0;
        }
        
        @Override
        public int getMaxPlayers() {
            return 0;
        }
    }
    
    /**
     * Test implementation of UserNameToIdResolver.
     */
    private static class TestUserNameToIdResolver implements UserNameToIdResolver {
        private final Set<NameAndId> savedIds = new HashSet<>();
        
        @Override
        public void add(NameAndId nameAndId) {
            this.savedIds.add(nameAndId);
        }
        
        @Override
        public Optional<NameAndId> get(String name) {
            return this.savedIds.stream()
                .filter(nameAndId -> nameAndId.name().equals(name))
                .findFirst()
                .or(() -> Optional.of(NameAndId.createOffline(name)));
        }
        
        @Override
        public Optional<NameAndId> get(UUID uuid) {
            return this.savedIds.stream()
                .filter(nameAndId -> nameAndId.id().equals(uuid))
                .findFirst();
        }
        
        @Override
        public void resolveOfflineUsers(boolean bl) {
            // No-op for test
        }
        
        @Override
        public void save() {
            // No-op for test
        }
    }
    
    /**
     * Test implementation of ProfileResolver.
     */
    private static class TestProfileResolver implements ProfileResolver {
        @Override
        public Optional<PlayerProfile> fetchByName(String name) {
            return Optional.empty();
        }
        
        @Override
        public Optional<PlayerProfile> fetchById(UUID uuid) {
            return Optional.empty();
        }
    }
}
