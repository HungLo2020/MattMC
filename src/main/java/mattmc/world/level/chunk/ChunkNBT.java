package mattmc.world.level.chunk;

import mattmc.nbt.BitPackedArray;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;

import java.util.*;

/**
 * Utility class for converting chunks to/from NBT format.
 * Follows MattMC's chunk NBT structure.
 */
public class ChunkNBT {
    
    /**
     * Convert a chunk to NBT format (Map-based).
     */
    public static Map<String, Object> toNBT(LevelChunk chunk) {
        // Pre-allocate root map with expected fields
        Map<String, Object> root = new HashMap<>(6);
        
        // LevelChunk position
        root.put("xPos", chunk.chunkX());
        root.put("zPos", chunk.chunkZ());
        
        // Data version (just a marker, not used for compatibility yet)
        root.put("DataVersion", 1);
        
        // Status (fully generated)
        root.put("Status", "full");
        
        // Sections - divide the chunk into 16-block-tall sections
        // Pre-allocate with exact size (HEIGHT / 16)
        List<Map<String, Object>> sections = new ArrayList<>(LevelChunk.HEIGHT / 16);
        
        for (int sectionY = 0; sectionY < LevelChunk.HEIGHT / 16; sectionY++) {
            Map<String, Object> section = createSection(chunk, sectionY);
            if (section != null) {
                sections.add(section);
            }
        }
        
        root.put("sections", sections);
        
        // ISSUE-004 fix: Save heightmap data using primitive int[] instead of ArrayList<Integer>
        // This eliminates 256 Integer boxing operations per chunk save
        int[][] heightmapData = chunk.getHeightmap().getData();
        int[] heightmapArray = new int[256];
        int index = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                heightmapArray[index++] = heightmapData[x][z];
            }
        }
        root.put("Heightmap", heightmapArray);
        
        return root;
    }
    
    /**
     * Create NBT for a single 16x16x16 section of the chunk.
     * Returns null if the section is empty (all air) and has default light values.
     * Uses bit-packed arrays like MattMC Java Edition for optimal storage.
     * 
     * PERFORMANCE FIX #4: Uses single-pass algorithm instead of 3 separate iterations.
     * This reduces getBlock() calls from 12,288 to 4,096 per section.
     */
    private static Map<String, Object> createSection(LevelChunk chunk, int sectionY) {
        int baseY = sectionY * 16;
        
        // PERFORMANCE FIX #4: Single-pass algorithm
        // Build palette and collect block data in one iteration instead of three
        List<String> paletteList = new ArrayList<>();
        Map<String, Integer> paletteMap = new HashMap<>();
        int[] paletteIndices = new int[4096];  // Temporary storage for palette indices
        boolean isEmpty = true;
        
        // Single pass: check empty, build palette, and collect indices
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Block block = chunk.getBlock(x, baseY + y, z);
                    String identifier = block.getIdentifier();
                    if (identifier == null) identifier = "mattmc:air";
                    
                    if (block != Blocks.AIR) {
                        isEmpty = false;
                    }
                    
                    Integer paletteIndex = paletteMap.get(identifier);
                    if (paletteIndex == null) {
                        paletteIndex = paletteList.size();
                        paletteMap.put(identifier, paletteIndex);
                        paletteList.add(identifier);
                    }
                    
                    int index = x + z * 16 + y * 16 * 16;
                    paletteIndices[index] = paletteIndex;
                }
            }
        }
        
        // Check if light has non-default values (only if section is empty)
        LightStorage lightStorage = chunk.getLightStorage(sectionY);
        boolean hasNonDefaultLight = false;
        if (lightStorage != null && isEmpty) {
            // Only check light if section is empty (to save time)
            outerLoop:
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (lightStorage.getSkyLight(x, y, z) != 15 || lightStorage.getBlockLightI(x, y, z) != 0) {
                            hasNonDefaultLight = true;
                            break outerLoop;
                        }
                    }
                }
            }
        }
        
        // Skip section if it's empty and has default light
        if (isEmpty && !hasNonDefaultLight) {
            return null;
        }
        
        // Section with palette and block states (estimated 5-7 fields with light)
        Map<String, Object> section = new HashMap<>(7);
        
        // Section Y coordinate (world Y / 16)
        section.put("Y", (byte) (sectionY + LevelChunk.MIN_Y / 16));
        
        if (!isEmpty) {
            // Calculate bits per entry (MattMC-style)
            int bitsPerEntry = BitPackedArray.calculateBitsPerEntry(paletteList.size());
            
            // Create bit-packed array for block states
            BitPackedArray blockStates = new BitPackedArray(bitsPerEntry, 16 * 16 * 16);
            
            // Fill block states from pre-collected indices (no additional getBlock calls!)
            for (int i = 0; i < 4096; i++) {
                blockStates.set(i, paletteIndices[i]);
            }
            
            // Build palette for NBT
            List<Map<String, Object>> palette = new ArrayList<>(paletteList.size());
            for (String identifier : paletteList) {
                Map<String, Object> paletteEntry = new HashMap<>(1);
                paletteEntry.put("Name", identifier);
                palette.add(paletteEntry);
            }
            
            section.put("Palette", palette);
            section.put("BlockStates", blockStates.getData());
            
            // Save blockstate properties for blocks that have them
            Map<String, Map<String, Object>> blockStateProperties = new HashMap<>();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        mattmc.world.level.block.state.BlockState state = chunk.getBlockState(x, baseY + y, z);
                        if (state != null && !state.isEmpty()) {
                            String key = x + "," + y + "," + z;
                            blockStateProperties.put(key, state.toNBT());
                        }
                    }
                }
            }
            
            if (!blockStateProperties.isEmpty()) {
                section.put("BlockStateProperties", blockStateProperties);
            }
        }
        
        // Always save light data (if section is being saved)
        if (lightStorage != null) {
            section.put("SkyLight", lightStorage.getSkyLightArray());
            section.put("BlockLight", lightStorage.getBlockLightArray());
        }
        
        return section;
    }
    
    /**
     * Load a chunk from NBT format (Map-based).
     */
    public static LevelChunk fromNBT(Map<String, Object> nbt) {
        // Get chunk position
        int chunkX = nbt.get("xPos") instanceof Integer ? (Integer) nbt.get("xPos") : 0;
        int chunkZ = nbt.get("zPos") instanceof Integer ? (Integer) nbt.get("zPos") : 0;
        
        LevelChunk chunk = new LevelChunk(chunkX, chunkZ);
        
        // Suppress light updates during chunk loading for performance
        chunk.setSuppressLightUpdates(true);
        
        // Load sections
        Object sectionsObj = nbt.get("sections");
        if (sectionsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sections = (List<Map<String, Object>>) sectionsObj;
            
            for (Map<String, Object> sectionTag : sections) {
                loadSection(chunk, sectionTag);
            }
        }
        
        // Load heightmap (versioned extension - optional for backward compatibility)
        // ISSUE-004 fix: Support both primitive int[] (new) and List<Integer> (legacy) formats
        Object heightmapObj = nbt.get("Heightmap");
        if (heightmapObj instanceof int[]) {
            // New optimized format: primitive int[]
            int[] heightmapArray = (int[]) heightmapObj;
            if (heightmapArray.length == 256) { // 16x16
                int index = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.getHeightmap().setHeight(x, z, heightmapArray[index++]);
                    }
                }
            }
        } else if (heightmapObj instanceof List) {
            // Legacy format: List<Integer>
            @SuppressWarnings("unchecked")
            List<Integer> heightmapList = (List<Integer>) heightmapObj;
            if (heightmapList.size() == 256) { // 16x16
                int index = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.getHeightmap().setHeight(x, z, heightmapList.get(index++));
                    }
                }
            }
        }
        
        // Re-enable light updates after loading complete
        chunk.setSuppressLightUpdates(false);
        
        // Recalculate block light for all emissive blocks to handle registry changes
        // This ensures that if block emission values changed since the world was saved,
        // the lighting will be corrected
        chunk.recalculateBlockLight();
        
        return chunk;
    }
    
    /**
     * Load a single section into the chunk.
     * Handles bit-packed block states like MattMC Java Edition.
     */
    private static void loadSection(LevelChunk chunk, Map<String, Object> section) {
        // Get section Y
        byte sectionY = section.get("Y") instanceof Byte ? (Byte) section.get("Y") : 0;
        int baseY = (sectionY - LevelChunk.MIN_Y / 16) * 16;
        
        // Get palette (may not exist if section only has light data)
        Object paletteObj = section.get("Palette");
        if (paletteObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> palette = (List<Map<String, Object>>) paletteObj;
            
            if (!palette.isEmpty()) {
                // Build palette array
                String[] paletteArray = new String[palette.size()];
                for (int i = 0; i < palette.size(); i++) {
                    Map<String, Object> paletteEntry = palette.get(i);
                    Object nameObj = paletteEntry.get("Name");
                    paletteArray[i] = nameObj instanceof String ? (String) nameObj : "mattmc:air";
                }
                
                // Get block states
                Object blockStatesObj = section.get("BlockStates");
                if (blockStatesObj instanceof long[]) {
                    long[] blockStatesData = (long[]) blockStatesObj;
                    
                    // Calculate expected bits per entry and validate data size
                    int bitsPerEntry = BitPackedArray.calculateBitsPerEntry(paletteArray.length);
                    
                    // Validate that we have enough data for 4096 blocks
                    if (bitsPerEntry > 0) {
                        int entriesPerLong = 64 / bitsPerEntry;
                        int expectedLongs = (4096 + entriesPerLong - 1) / entriesPerLong;
                        
                        if (blockStatesData.length >= expectedLongs) {
                            // Create bit-packed array for reading
                            BitPackedArray blockStates = new BitPackedArray(bitsPerEntry, 16 * 16 * 16, blockStatesData);
                            
                            // Load blocks
                            for (int x = 0; x < 16; x++) {
                                for (int y = 0; y < 16; y++) {
                                    for (int z = 0; z < 16; z++) {
                                        int index = x + z * 16 + y * 16 * 16;
                                        int paletteIndex = blockStates.get(index);
                                        
                                        if (paletteIndex >= 0 && paletteIndex < paletteArray.length) {
                                            String identifier = paletteArray[paletteIndex];
                                            Block block = Blocks.getBlockOrAir(identifier);
                                            chunk.setBlock(x, baseY + y, z, block);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Load blockstate properties
                Object blockStatePropertiesObj = section.get("BlockStateProperties");
                if (blockStatePropertiesObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, Object>> blockStateProperties = 
                        (Map<String, Map<String, Object>>) blockStatePropertiesObj;
                    
                    for (Map.Entry<String, Map<String, Object>> entry : blockStateProperties.entrySet()) {
                        String[] coords = entry.getKey().split(",");
                        if (coords.length == 3) {
                            try {
                                int x = Integer.parseInt(coords[0]);
                                int y = Integer.parseInt(coords[1]);
                                int z = Integer.parseInt(coords[2]);
                                
                                mattmc.world.level.block.state.BlockState state = 
                                    mattmc.world.level.block.state.BlockState.fromNBT(entry.getValue());
                                
                                // Get the current block and re-set with the state
                                Block block = chunk.getBlock(x, baseY + y, z);
                                chunk.setBlock(x, baseY + y, z, block, state);
                            } catch (NumberFormatException ignored) {
                                // Skip malformed coordinates
                            }
                        }
                    }
                }
            }
        }
        
        // Load light data
        Object skyLightObj = section.get("SkyLight");
        Object blockLightObj = section.get("BlockLight");
        if (skyLightObj instanceof byte[] && blockLightObj instanceof byte[]) {
            byte[] skyLight = (byte[]) skyLightObj;
            byte[] blockLight = (byte[]) blockLightObj;
            
            // Validate array sizes (support both legacy 2048 and RGBI 8192 formats)
            if (skyLight.length == 2048 && (blockLight.length == 2048 || blockLight.length == 8192)) {
                LightStorage lightStorage = new LightStorage(skyLight, blockLight);
                // sectionY is world section index, need to convert to chunk section index
                int chunkSectionIndex = baseY / 16;
                chunk.setLightStorage(chunkSectionIndex, lightStorage);
            }
        }
    }
}
