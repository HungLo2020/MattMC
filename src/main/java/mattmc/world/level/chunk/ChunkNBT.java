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
        Map<String, Object> root = new HashMap<>();
        
        // LevelChunk position
        root.put("xPos", chunk.chunkX());
        root.put("zPos", chunk.chunkZ());
        
        // Data version (just a marker, not used for compatibility yet)
        root.put("DataVersion", 1);
        
        // Status (fully generated)
        root.put("Status", "full");
        
        // Sections - divide the chunk into 16-block-tall sections
        List<Map<String, Object>> sections = new ArrayList<>();
        
        for (int sectionY = 0; sectionY < LevelChunk.HEIGHT / 16; sectionY++) {
            Map<String, Object> section = createSection(chunk, sectionY);
            if (section != null) {
                sections.add(section);
            }
        }
        
        root.put("sections", sections);
        
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
            return null; // Skip empty sections
        }
        
        Map<String, Object> section = new HashMap<>();
        
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
        List<Map<String, Object>> palette = new ArrayList<>();
        for (String identifier : paletteList) {
            Map<String, Object> paletteEntry = new HashMap<>();
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
        
        // Recalculate lighting after loading blocks
        // TODO: In the future, save/load light data to avoid recalculation
        LightEngine.updateLighting(chunk);
        
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
        
        // Get palette
        Object paletteObj = section.get("Palette");
        if (!(paletteObj instanceof List)) {
            return;
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
