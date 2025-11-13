package mattmc.world.level.chunk;

import mattmc.client.Minecraft;
import mattmc.nbt.BitPackedArray;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;

import java.util.*;

/**
 * Utility class for converting chunks to/from NBT format.
 * Follows Minecraft's chunk NBT structure.
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
        
        // Save heightmap data (versioned extension)
        int[][] heightmapData = chunk.getHeightmap().getData();
        // Pre-allocate with exact size (16 * 16 = 256)
        List<Integer> heightmapList = new ArrayList<>(256);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                heightmapList.add(heightmapData[x][z]);
            }
        }
        root.put("Heightmap", heightmapList);
        
        return root;
    }
    
    /**
     * Create NBT for a single 16x16x16 section of the chunk.
     * Returns null if the section is empty (all air).
     * Uses bit-packed arrays like Minecraft Java Edition for optimal storage.
     */
    private static Map<String, Object> createSection(LevelChunk chunk, int sectionY) {
        // Check if section is empty
        boolean isEmpty = true;
        int baseY = sectionY * 16;
        
        for (int x = 0; x < 16 && isEmpty; x++) {
            for (int y = baseY; y < baseY + 16 && isEmpty; y++) {
                for (int z = 0; z < 16 && isEmpty; z++) {
                    if (chunk.getBlock(x, y, z) != Blocks.AIR) {
                        isEmpty = false;
                    }
                }
            }
        }
        
        if (isEmpty) {
            // Even if section has no blocks, we might still want to save light data
            // Check if we have non-default light values
            LightStorage lightSection = chunk.getLightSection(sectionY);
            if (lightSection != null) {
                boolean hasNonDefaultLight = false;
                byte[] lightData = lightSection.getData();
                for (byte b : lightData) {
                    // Check if any light value is not the default (sky=15, block=0, i.e., 0xF0)
                    if (b != (byte) 0xF0) {
                        hasNonDefaultLight = true;
                        break;
                    }
                }
                
                if (!hasNonDefaultLight) {
                    return null; // Skip sections with no blocks and default light
                }
                
                // Create a minimal section just for light data
                Map<String, Object> section = new HashMap<>(2);
                section.put("Y", (byte) (sectionY + LevelChunk.MIN_Y / 16));
                section.put("LightData", lightSection.getData());
                return section;
            }
            return null; // Skip empty sections with no light data
        }
        
        // Section with palette and block states (estimated 4-5 fields)
        Map<String, Object> section = new HashMap<>(5);
        
        // Section Y coordinate (world Y / 16)
        section.put("Y", (byte) (sectionY + LevelChunk.MIN_Y / 16));
        
        // Build palette - map unique blocks to palette indices
        List<String> paletteList = new ArrayList<>();
        Map<String, Integer> paletteMap = new HashMap<>();
        
        // First pass: collect unique blocks
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, baseY + y, z);
                    String identifier = block.getIdentifier();
                    if (identifier == null) identifier = "mattmc:air";
                    
                    if (!paletteMap.containsKey(identifier)) {
                        paletteMap.put(identifier, paletteList.size());
                        paletteList.add(identifier);
                    }
                }
            }
        }
        
        // Calculate bits per entry (Minecraft-style)
        int bitsPerEntry = BitPackedArray.calculateBitsPerEntry(paletteList.size());
        
        // Create bit-packed array for block states
        BitPackedArray blockStates = new BitPackedArray(bitsPerEntry, 16 * 16 * 16);
        
        // Second pass: fill block states
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, baseY + y, z);
                    String identifier = block.getIdentifier();
                    if (identifier == null) identifier = "mattmc:air";
                    
                    int index = x + z * 16 + y * 16 * 16;
                    int paletteIndex = paletteMap.get(identifier);
                    blockStates.set(index, paletteIndex);
                }
            }
        }
        
        // Build palette for NBT
        // Pre-allocate with exact size (number of unique blocks in palette)
        List<Map<String, Object>> palette = new ArrayList<>(paletteList.size());
        for (String identifier : paletteList) {
            // Pre-allocate HashMap with single entry
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
        
        // Save light data for this section (versioned extension)
        LightStorage lightSection = chunk.getLightSection(sectionY);
        if (lightSection != null) {
            section.put("LightData", lightSection.getData());
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
        Object heightmapObj = nbt.get("Heightmap");
        if (heightmapObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Integer> heightmapList = (List<Integer>) heightmapObj;
            if (heightmapList.size() == 256) { // 16x16
                int[][] heightmapData = new int[16][16];
                int index = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        heightmapData[x][z] = heightmapList.get(index++);
                    }
                }
                ColumnHeightmap heightmap = new ColumnHeightmap(heightmapData);
                // Note: Cannot directly set heightmap as it's final, but we can update it
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.getHeightmap().setHeight(x, z, heightmapData[x][z]);
                    }
                }
            }
        }
        
        return chunk;
    }
    
    /**
     * Load a single section into the chunk.
     * Handles bit-packed block states like Minecraft Java Edition.
     */
    private static void loadSection(LevelChunk chunk, Map<String, Object> section) {
        // Get section Y
        byte sectionY = section.get("Y") instanceof Byte ? (Byte) section.get("Y") : 0;
        int baseY = (sectionY - LevelChunk.MIN_Y / 16) * 16;
        
        // Load light data for this section (versioned extension - optional for backward compatibility)
        // This should be loaded first, as sections might only contain light data
        Object lightDataObj = section.get("LightData");
        if (lightDataObj instanceof byte[]) {
            byte[] lightData = (byte[]) lightDataObj;
            if (lightData.length == 4096) {
                int sectionIndex = (sectionY - LevelChunk.MIN_Y / 16);
                chunk.setLightSection(sectionIndex, new LightStorage(lightData));
            }
        }
        
        // Get palette (might not exist for light-only sections)
        Object paletteObj = section.get("Palette");
        if (!(paletteObj instanceof List)) {
            return; // Light-only section, no blocks to load
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> palette = (List<Map<String, Object>>) paletteObj;
        
        if (palette.isEmpty()) {
            return;
        }
        
        // Build palette array
        String[] paletteArray = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            Map<String, Object> paletteEntry = palette.get(i);
            Object nameObj = paletteEntry.get("Name");
            paletteArray[i] = nameObj instanceof String ? (String) nameObj : "mattmc:air";
        }
        
        // Get block states
        Object blockStatesObj = section.get("BlockStates");
        if (!(blockStatesObj instanceof long[])) {
            return;
        }
        
        long[] blockStatesData = (long[]) blockStatesObj;
        
        // Calculate expected bits per entry and validate data size
        int bitsPerEntry = BitPackedArray.calculateBitsPerEntry(paletteArray.length);
        
        // Validate that we have enough data for 4096 blocks
        if (bitsPerEntry > 0) {
            int entriesPerLong = 64 / bitsPerEntry;
            int expectedLongs = (4096 + entriesPerLong - 1) / entriesPerLong;
            
            if (blockStatesData.length < expectedLongs) {
                // Data is corrupted or incomplete, skip this section
                return;
            }
        }
        
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
