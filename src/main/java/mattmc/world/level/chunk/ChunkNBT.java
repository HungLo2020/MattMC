package mattmc.world.level.chunk;

import mattmc.client.Minecraft;
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
        
        // Block states - simple palette approach
        // For now, store block identifiers directly
        List<Map<String, Object>> palette = new ArrayList<>();
        long[] blockStates = new long[16 * 16 * 16]; // Simple 1:1 mapping for now
        
        Map<String, Integer> paletteMap = new HashMap<>();
        int paletteIndex = 0;
        
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, baseY + y, z);
                    String identifier = block.getIdentifier();
                    if (identifier == null) identifier = "mattmc:air";
                    
                    // Add to palette if not present
                    if (!paletteMap.containsKey(identifier)) {
                        Map<String, Object> paletteEntry = new HashMap<>();
                        paletteEntry.put("Name", identifier);
                        palette.add(paletteEntry);
                        paletteMap.put(identifier, paletteIndex++);
                    }
                    
                    // Store block state index
                    int index = x + z * 16 + y * 16 * 16;
                    blockStates[index] = paletteMap.get(identifier);
                }
            }
        }
        
        section.put("Palette", palette);
        
        // Block states as long array
        section.put("BlockStates", blockStates);
        
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
        
        return chunk;
    }
    
    /**
     * Load a single section into the chunk.
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
        
        long[] blockStates = (long[]) blockStatesObj;
        
        // Load blocks
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    int index = x + z * 16 + y * 16 * 16;
                    if (index < blockStates.length) {
                        int paletteIndex = (int) blockStates[index];
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
