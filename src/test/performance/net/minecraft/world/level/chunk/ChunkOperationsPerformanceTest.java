package net.minecraft.world.level.chunk;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Performance tests for chunk-related operations.
 * Tests realistic chunk operations matching in-game behavior.
 * 
 * Minecraft 1.21 Chunk Structure:
 * - Height range: Y -64 to 319 (384 blocks total)
 * - 24 sections of 16x16x16 blocks each
 * - Each section has block states, biomes, and lighting
 * - Additional data: heightmaps, block entities, tick data
 */
@DisplayName("Chunk Operations Performance Tests")
class ChunkOperationsPerformanceTest {
    
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_SIZE = CHUNK_SIZE * CHUNK_SIZE * SECTION_HEIGHT; // 4096 blocks per section
    
    // Minecraft 1.21 world height configuration
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;
    private static final int WORLD_HEIGHT = MAX_Y - MIN_Y + 1; // 384 blocks
    private static final int SECTION_COUNT = WORLD_HEIGHT / SECTION_HEIGHT; // 24 sections
    
    private static final int BLOCKS_PER_CHUNK = CHUNK_SIZE * CHUNK_SIZE * WORLD_HEIGHT; // 16x16x384 blocks
    
    @BeforeAll
    static void bootstrapMinecraft() {
        // Initialize Minecraft registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    
    @Test
    @DisplayName("should measure BlockPos creation for 1 chunk worth of blocks")
    void testBlockPosCreation1Chunk() {
        int blockCount = BLOCKS_PER_CHUNK;
        long startTime = System.nanoTime();
        
        List<BlockPos> positions = new ArrayList<>(blockCount);
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("BlockPos creation (1 chunk)", 1, blockCount, duration);
    }
    
    @Test
    @DisplayName("should measure BlockPos creation for 16 chunks worth of blocks")
    void testBlockPosCreation16Chunks() {
        int chunkCount = 16;
        int blockCount = BLOCKS_PER_CHUNK * chunkCount;
        long startTime = System.nanoTime();
        
        List<BlockPos> positions = new ArrayList<>(blockCount);
        for (int chunkX = 0; chunkX < 4; chunkX++) {
            for (int chunkZ = 0; chunkZ < 4; chunkZ++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        for (int y = MIN_Y; y <= MAX_Y; y++) {
                            positions.add(new BlockPos(chunkX * CHUNK_SIZE + x, y, chunkZ * CHUNK_SIZE + z));
                        }
                    }
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("BlockPos creation (16 chunks)", chunkCount, blockCount, duration);
    }
    
    @Test
    @DisplayName("should measure BlockPos creation for 128 chunks worth of blocks")
    void testBlockPosCreation128Chunks() {
        int chunkCount = 128;
        int blockCount = BLOCKS_PER_CHUNK * chunkCount;
        long startTime = System.nanoTime();
        
        List<BlockPos> positions = new ArrayList<>(blockCount);
        for (int chunkIdx = 0; chunkIdx < chunkCount; chunkIdx++) {
            int chunkX = chunkIdx % 16;
            int chunkZ = chunkIdx / 16;
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = MIN_Y; y <= MAX_Y; y++) {
                        positions.add(new BlockPos(chunkX * CHUNK_SIZE + x, y, chunkZ * CHUNK_SIZE + z));
                    }
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("BlockPos creation (128 chunks)", chunkCount, blockCount, duration);
    }
    
    @Test
    @DisplayName("should measure NBT serialization for 1 chunk metadata")
    void testNBTSerialization1Chunk() {
        int chunkCount = 1;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < chunkCount; i++) {
            serializeChunkMetadata(i, 0);
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("NBT serialization (1 chunk)", chunkCount, chunkCount, duration);
    }
    
    @Test
    @DisplayName("should measure NBT serialization for 16 chunk metadata")
    void testNBTSerialization16Chunks() {
        int chunkCount = 16;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < chunkCount; i++) {
            serializeChunkMetadata(i % 4, i / 4);
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("NBT serialization (16 chunks)", chunkCount, chunkCount, duration);
    }
    
    @Test
    @DisplayName("should measure NBT serialization for 128 chunk metadata")
    void testNBTSerialization128Chunks() {
        int chunkCount = 128;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < chunkCount; i++) {
            serializeChunkMetadata(i % 16, i / 16);
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("NBT serialization (128 chunks)", chunkCount, chunkCount, duration);
    }
    
    @Test
    @DisplayName("should measure BlockState operations for 1 chunk")
    void testBlockStateOperations1Chunk() {
        int chunkCount = 1;
        int blockCount = BLOCKS_PER_CHUNK;
        
        long startTime = System.nanoTime();
        
        List<BlockState> states = new ArrayList<>(blockCount);
        Random random = new Random(12345); // Fixed seed for reproducibility
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    // Simulate realistic terrain generation with varied blocks
                    states.add(getRealisticBlockState(x, y, z, random));
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("BlockState operations (1 chunk)", chunkCount, blockCount, duration);
    }
    
    @Test
    @DisplayName("should measure BlockState operations for 16 chunks")
    void testBlockStateOperations16Chunks() {
        int chunkCount = 16;
        int blockCount = BLOCKS_PER_CHUNK * chunkCount;
        
        long startTime = System.nanoTime();
        
        List<BlockState> states = new ArrayList<>(blockCount);
        Random random = new Random(12345); // Fixed seed for reproducibility
        
        for (int chunkX = 0; chunkX < 4; chunkX++) {
            for (int chunkZ = 0; chunkZ < 4; chunkZ++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        for (int y = MIN_Y; y <= MAX_Y; y++) {
                            states.add(getRealisticBlockState(x, y, z, random));
                        }
                    }
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("BlockState operations (16 chunks)", chunkCount, blockCount, duration);
    }
    
    @Test
    @DisplayName("should measure BlockState operations for 128 chunks")
    void testBlockStateOperations128Chunks() {
        int chunkCount = 128;
        int blockCount = BLOCKS_PER_CHUNK * chunkCount;
        
        long startTime = System.nanoTime();
        
        List<BlockState> states = new ArrayList<>(blockCount);
        Random random = new Random(12345); // Fixed seed for reproducibility
        
        for (int chunkIdx = 0; chunkIdx < chunkCount; chunkIdx++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = MIN_Y; y <= MAX_Y; y++) {
                        states.add(getRealisticBlockState(x, y, z, random));
                    }
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("BlockState operations (128 chunks)", chunkCount, blockCount, duration);
    }
    
    /**
     * Serializes chunk metadata to NBT and writes to byte array.
     * Simulates realistic chunk serialization with all expected data.
     */
    private byte[] serializeChunkMetadata(int chunkX, int chunkZ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("xPos", chunkX);
        tag.putInt("zPos", chunkZ);
        tag.putInt("yPos", MIN_Y / SECTION_HEIGHT); // Section position
        tag.putLong("LastUpdate", System.currentTimeMillis());
        tag.putLong("InhabitedTime", 1000L);
        tag.putString("Status", "minecraft:full");
        
        // Add sections with realistic data structure
        ListTag sectionsTag = new ListTag();
        Random random = new Random(chunkX * 31L + chunkZ);
        
        for (int i = 0; i < SECTION_COUNT; i++) {
            CompoundTag sectionTag = new CompoundTag();
            sectionTag.putByte("Y", (byte)(i + (MIN_Y / SECTION_HEIGHT)));
            
            // Add block states palette and data
            CompoundTag blockStatesTag = new CompoundTag();
            ListTag paletteTag = new ListTag();
            
            // Simulate varied palette (5-15 different block types per section)
            int paletteSize = 5 + random.nextInt(11);
            String[] blockTypes = {"minecraft:stone", "minecraft:air", "minecraft:deepslate", 
                                   "minecraft:dirt", "minecraft:grass_block", "minecraft:coal_ore",
                                   "minecraft:iron_ore", "minecraft:water", "minecraft:gravel",
                                   "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                                   "minecraft:diamond_ore", "minecraft:gold_ore", "minecraft:obsidian"};
            for (int p = 0; p < paletteSize; p++) {
                CompoundTag blockTag = new CompoundTag();
                blockTag.putString("Name", blockTypes[p % blockTypes.length]);
                paletteTag.add(blockTag);
            }
            blockStatesTag.put("palette", paletteTag);
            
            // Add data array (simulated bit-packed data)
            int bitsPerBlock = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
            int arraySize = (SECTION_SIZE * bitsPerBlock + 63) / 64;
            long[] data = new long[arraySize];
            for (int d = 0; d < arraySize; d++) {
                data[d] = random.nextLong();
            }
            blockStatesTag.put("data", new LongArrayTag(data));
            
            sectionTag.put("block_states", blockStatesTag);
            
            // Add biome data (4x4x4 = 64 biomes per section)
            CompoundTag biomesTag = new CompoundTag();
            ListTag biomePaletteTag = new ListTag();
            
            // Varied biomes based on section height
            String[] biomeTypes = i < 0 ? 
                new String[]{"minecraft:deep_dark", "minecraft:dripstone_caves"} :
                i < 10 ? 
                new String[]{"minecraft:plains", "minecraft:forest", "minecraft:river"} :
                new String[]{"minecraft:plains", "minecraft:mountains"};
            
            for (String biomeType : biomeTypes) {
                CompoundTag biomeEntry = new CompoundTag();
                biomeEntry.putString("Name", biomeType);
                biomePaletteTag.add(biomeEntry);
            }
            biomesTag.put("palette", biomePaletteTag);
            sectionTag.put("biomes", biomesTag);
            
            // Add lighting data (2048 bytes each for sky and block light)
            byte[] skyLight = new byte[2048];
            byte[] blockLight = new byte[2048];
            random.nextBytes(skyLight);
            random.nextBytes(blockLight);
            sectionTag.putByteArray("SkyLight", skyLight);
            sectionTag.putByteArray("BlockLight", blockLight);
            
            sectionsTag.add(sectionTag);
        }
        tag.put("sections", sectionsTag);
        
        // Add heightmaps
        CompoundTag heightmaps = new CompoundTag();
        int heightmapSize = CHUNK_SIZE * CHUNK_SIZE;
        int longsPerHeightmap = (heightmapSize * 9 + 63) / 64; // 9 bits per height value
        
        for (String type : new String[]{"MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES", "OCEAN_FLOOR", "WORLD_SURFACE"}) {
            long[] heightmapData = new long[longsPerHeightmap];
            for (int h = 0; h < longsPerHeightmap; h++) {
                heightmapData[h] = random.nextLong();
            }
            heightmaps.put(type, new LongArrayTag(heightmapData));
        }
        tag.put("Heightmaps", heightmaps);
        
        // Add block entities (simulate 2-5 per chunk with varied types)
        ListTag blockEntities = new ListTag();
        int blockEntityCount = 2 + random.nextInt(4);
        String[] blockEntityTypes = {"minecraft:chest", "minecraft:furnace", "minecraft:sign", 
                                      "minecraft:barrel", "minecraft:hopper"};
        for (int be = 0; be < blockEntityCount; be++) {
            CompoundTag beTag = new CompoundTag();
            beTag.putString("id", blockEntityTypes[be % blockEntityTypes.length]);
            beTag.putInt("x", chunkX * CHUNK_SIZE + random.nextInt(CHUNK_SIZE));
            beTag.putInt("y", random.nextInt(WORLD_HEIGHT) + MIN_Y);
            beTag.putInt("z", chunkZ * CHUNK_SIZE + random.nextInt(CHUNK_SIZE));
            blockEntities.add(beTag);
        }
        tag.put("block_entities", blockEntities);
        
        // Add tick data
        ListTag blockTicks = new ListTag();
        int tickCount = random.nextInt(10);
        for (int t = 0; t < tickCount; t++) {
            CompoundTag tickTag = new CompoundTag();
            tickTag.putString("i", "minecraft:water");
            tickTag.putInt("x", random.nextInt(CHUNK_SIZE));
            tickTag.putInt("y", random.nextInt(WORLD_HEIGHT) + MIN_Y);
            tickTag.putInt("z", random.nextInt(CHUNK_SIZE));
            tickTag.putInt("t", random.nextInt(100));
            blockTicks.add(tickTag);
        }
        tag.put("block_ticks", blockTicks);
        
        // Add structures data
        CompoundTag structures = new CompoundTag();
        structures.putString("starts", "{}");
        tag.put("structures", structures);
        
        // Add carving masks
        tag.putLongArray("CarvingMasks", new long[]{random.nextLong(), random.nextLong()});
        
        // Serialize to bytes
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            tag.write(dos);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
    
    /**
     * Generates realistic block state distribution matching actual terrain generation.
     * Includes varied blocks like stone, dirt, grass, ores, water, air for caves.
     */
    private BlockState getRealisticBlockState(int x, int y, int z, Random random) {
        // Air in upper sections
        if (y > 200) {
            return Blocks.AIR.defaultBlockState();
        }
        
        // Surface layer
        if (y >= 60 && y <= 65) {
            return y == 65 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        
        // Water level
        if (y >= 58 && y < 62) {
            return random.nextDouble() < 0.3 ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
        
        // Cave air (simulating caves)
        if (y > MIN_Y && y < 50 && random.nextDouble() < 0.15) {
            return Blocks.AIR.defaultBlockState();
        }
        
        // Deepslate layer (below y=0)
        if (y < 0) {
            // Occasional ores in deepslate
            double oreChance = random.nextDouble();
            if (oreChance < 0.001) return Blocks.DIAMOND_ORE.defaultBlockState();
            if (oreChance < 0.005) return Blocks.GOLD_ORE.defaultBlockState();
            if (oreChance < 0.02) return Blocks.IRON_ORE.defaultBlockState();
            if (oreChance < 0.04) return Blocks.COAL_ORE.defaultBlockState();
            
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        
        // Stone layer with ores
        double oreChance = random.nextDouble();
        if (oreChance < 0.001) return Blocks.DIAMOND_ORE.defaultBlockState();
        if (oreChance < 0.003) return Blocks.GOLD_ORE.defaultBlockState();
        if (oreChance < 0.015) return Blocks.IRON_ORE.defaultBlockState();
        if (oreChance < 0.05) return Blocks.COAL_ORE.defaultBlockState();
        if (oreChance < 0.07) return Blocks.GRAVEL.defaultBlockState();
        if (oreChance < 0.09) return Blocks.ANDESITE.defaultBlockState();
        if (oreChance < 0.11) return Blocks.DIORITE.defaultBlockState();
        if (oreChance < 0.13) return Blocks.GRANITE.defaultBlockState();
        
        return Blocks.STONE.defaultBlockState();
    }
    
    @Test
    @DisplayName("should measure PalettedContainer operations for 1 chunk (24 sections)")
    void testPalettedContainerOperations1Chunk() {
        int sectionCount = SECTION_COUNT;
        long startTime = System.nanoTime();
        
        // Keep containers in list to simulate real memory patterns and prevent premature GC during test
        List<PalettedContainer<BlockState>> containers = new ArrayList<>(sectionCount);
        Random random = new Random(12345);
        
        // Create strategy for block states
        Strategy<BlockState> strategy = Strategy.createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
        
        // Create one PalettedContainer per section
        for (int section = 0; section < sectionCount; section++) {
            PalettedContainer<BlockState> container = new PalettedContainer<>(
                Blocks.AIR.defaultBlockState(),
                strategy
            );
            
            // Fill the section with realistic block distribution
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = 0; y < SECTION_HEIGHT; y++) {
                        int worldY = MIN_Y + section * SECTION_HEIGHT + y;
                        BlockState state = getRealisticBlockState(x, worldY, z, random);
                        container.set(x, y, z, state);
                    }
                }
            }
            
            containers.add(container);
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("PalettedContainer operations (1 chunk)", 1, BLOCKS_PER_CHUNK, duration);
    }
    
    @Test
    @DisplayName("should measure PalettedContainer operations for 16 chunks")
    void testPalettedContainerOperations16Chunks() {
        int chunkCount = 16;
        int totalSections = chunkCount * SECTION_COUNT;
        long startTime = System.nanoTime();
        
        // Keep containers in list to simulate real memory patterns and prevent premature GC during test
        List<PalettedContainer<BlockState>> containers = new ArrayList<>(totalSections);
        Random random = new Random(12345);
        
        // Create strategy for block states
        Strategy<BlockState> strategy = Strategy.createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
        
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            for (int section = 0; section < SECTION_COUNT; section++) {
                PalettedContainer<BlockState> container = new PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(),
                    strategy
                );
                
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        for (int y = 0; y < SECTION_HEIGHT; y++) {
                            int worldY = MIN_Y + section * SECTION_HEIGHT + y;
                            BlockState state = getRealisticBlockState(x, worldY, z, random);
                            container.set(x, y, z, state);
                        }
                    }
                }
                
                containers.add(container);
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("PalettedContainer operations (16 chunks)", chunkCount, BLOCKS_PER_CHUNK * chunkCount, duration);
    }
    
    @Test
    @DisplayName("should measure section-based block operations for 1 chunk")
    void testSectionBasedBlockOperations1Chunk() {
        int sectionCount = SECTION_COUNT;
        long startTime = System.nanoTime();
        
        Random random = new Random(12345);
        Strategy<BlockState> strategy = Strategy.createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
        
        // Process blocks section by section (as done in real chunk operations)
        // Keep sections in list to simulate real memory patterns
        List<PalettedContainer<BlockState>> sections = new ArrayList<>(sectionCount);
        
        for (int s = 0; s < sectionCount; s++) {
            PalettedContainer<BlockState> container = new PalettedContainer<>(
                Blocks.AIR.defaultBlockState(),
                strategy
            );
            
            // Fill section with realistic data
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = 0; y < SECTION_HEIGHT; y++) {
                        int worldY = MIN_Y + s * SECTION_HEIGHT + y;
                        BlockState state = getRealisticBlockState(x, worldY, z, random);
                        container.set(x, y, z, state);
                    }
                }
            }
            
            sections.add(container);
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("Section-based block operations (1 chunk)", 1, BLOCKS_PER_CHUNK, duration);
    }
    
    @Test
    @DisplayName("should measure heightmap calculation for 1 chunk")
    void testHeightmapCalculation1Chunk() {
        int chunkCount = 1;
        long startTime = System.nanoTime();
        
        Random random = new Random(12345);
        
        // Simulate heightmap calculation
        int[] motionBlocking = new int[CHUNK_SIZE * CHUNK_SIZE];
        int[] worldSurface = new int[CHUNK_SIZE * CHUNK_SIZE];
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int idx = z * CHUNK_SIZE + x;
                
                // Scan from top down to find surface
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    BlockState state = getRealisticBlockState(x, y, z, random);
                    if (!state.isAir()) {
                        motionBlocking[idx] = y;
                        worldSurface[idx] = y;
                        break;
                    }
                }
            }
        }
        
        long duration = System.nanoTime() - startTime;
        printResults("Heightmap calculation (1 chunk)", chunkCount, CHUNK_SIZE * CHUNK_SIZE * 2, duration);
    }
    
    @Test
    @DisplayName("should measure complete chunk data structure for 1 chunk")
    void testCompleteChunkDataStructure1Chunk() {
        int chunkCount = 1;
        long startTime = System.nanoTime();
        
        Random random = new Random(12345);
        Strategy<BlockState> strategy = Strategy.createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
        
        // Create all sections with PalettedContainers
        // Keep sections in list to simulate real chunk memory usage
        List<PalettedContainer<BlockState>> sections = new ArrayList<>(SECTION_COUNT);
        for (int s = 0; s < SECTION_COUNT; s++) {
            PalettedContainer<BlockState> container = new PalettedContainer<>(
                Blocks.AIR.defaultBlockState(),
                strategy
            );
            
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = 0; y < SECTION_HEIGHT; y++) {
                        int worldY = MIN_Y + s * SECTION_HEIGHT + y;
                        BlockState state = getRealisticBlockState(x, worldY, z, random);
                        container.set(x, y, z, state);
                    }
                }
            }
            
            sections.add(container);
        }
        
        // Calculate heightmaps
        int[] heightmap = new int[CHUNK_SIZE * CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    BlockState state = getRealisticBlockState(x, y, z, random);
                    if (!state.isAir()) {
                        heightmap[z * CHUNK_SIZE + x] = y;
                        break;
                    }
                }
            }
        }
        
        // Simulate lighting data
        byte[][] lightData = new byte[SECTION_COUNT][2048];
        for (int s = 0; s < SECTION_COUNT; s++) {
            random.nextBytes(lightData[s]);
        }
        
        // Serialize to NBT
        byte[] serialized = serializeChunkMetadata(0, 0);
        
        long duration = System.nanoTime() - startTime;
        printResults("Complete chunk data structure (1 chunk)", chunkCount, BLOCKS_PER_CHUNK, duration);
    }
    
    private void printResults(String operation, int chunkCount, int itemCount, long durationNanos) {
        double durationMs = durationNanos / 1_000_000.0;
        double avgMsPerChunk = durationMs / chunkCount;
        double chunksPerSecond = chunkCount / (durationNanos / 1_000_000_000.0);
        double itemsPerSecond = itemCount / (durationNanos / 1_000_000_000.0);
        
        System.out.printf("%s: %.2f ms total (%.2f ms/chunk, %.2f chunks/sec, %.0f items/sec)%n",
            operation, durationMs, avgMsPerChunk, chunksPerSecond, itemsPerSecond);
    }
}
