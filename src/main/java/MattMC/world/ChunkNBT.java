package MattMC.world;

import net.querz.nbt.tag.*;

/**
 * Utility class for converting chunks to/from NBT format.
 * Follows Minecraft's chunk NBT structure.
 */
public class ChunkNBT {
    
    /**
     * Convert a chunk to NBT format.
     */
    public static CompoundTag toNBT(Chunk chunk) {
        CompoundTag root = new CompoundTag();
        
        // Chunk position
        root.putInt("xPos", chunk.chunkX());
        root.putInt("zPos", chunk.chunkZ());
        
        // Data version (just a marker, not used for compatibility yet)
        root.putInt("DataVersion", 1);
        
        // Status (fully generated)
        root.putString("Status", "full");
        
        // Sections - divide the chunk into 16-block-tall sections
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        
        for (int sectionY = 0; sectionY < Chunk.HEIGHT / 16; sectionY++) {
            CompoundTag section = createSection(chunk, sectionY);
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
    private static CompoundTag createSection(Chunk chunk, int sectionY) {
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
        
        CompoundTag section = new CompoundTag();
        
        // Section Y coordinate (world Y / 16)
        section.putByte("Y", (byte) (sectionY + Chunk.MIN_Y / 16));
        
        // Block states - simple palette approach
        // For now, store block identifiers directly
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        long[] blockStates = new long[16 * 16 * 16]; // Simple 1:1 mapping for now
        
        java.util.Map<String, Integer> paletteMap = new java.util.HashMap<>();
        int paletteIndex = 0;
        
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, baseY + y, z);
                    String identifier = block.getIdentifier();
                    if (identifier == null) identifier = "mattmc:air";
                    
                    // Add to palette if not present
                    if (!paletteMap.containsKey(identifier)) {
                        CompoundTag paletteEntry = new CompoundTag();
                        paletteEntry.putString("Name", identifier);
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
        section.putLongArray("BlockStates", blockStates);
        
        return section;
    }
    
    /**
     * Load a chunk from NBT format.
     */
    public static Chunk fromNBT(CompoundTag nbt) {
        // Get chunk position
        int chunkX = nbt.getInt("xPos");
        int chunkZ = nbt.getInt("zPos");
        
        Chunk chunk = new Chunk(chunkX, chunkZ);
        
        // Load sections
        ListTag<?> sections = nbt.getListTag("sections");
        if (sections != null && sections.getTypeClass() == CompoundTag.class) {
            @SuppressWarnings("unchecked")
            ListTag<CompoundTag> sectionsList = (ListTag<CompoundTag>) sections;
            
            for (CompoundTag sectionTag : sectionsList) {
                loadSection(chunk, sectionTag);
            }
        }
        
        return chunk;
    }
    
    /**
     * Load a single section into the chunk.
     */
    private static void loadSection(Chunk chunk, CompoundTag section) {
        // Get section Y
        byte sectionY = section.getByte("Y");
        int baseY = (sectionY - Chunk.MIN_Y / 16) * 16;
        
        // Get palette
        ListTag<?> paletteTag = section.getListTag("Palette");
        if (paletteTag == null || paletteTag.getTypeClass() != CompoundTag.class) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        ListTag<CompoundTag> paletteList = (ListTag<CompoundTag>) paletteTag;
        
        if (paletteList.size() == 0) {
            return;
        }
        
        // Build palette array
        String[] paletteArray = new String[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            CompoundTag paletteEntry = paletteList.get(i);
            paletteArray[i] = paletteEntry.getString("Name");
            if (paletteArray[i] == null) {
                paletteArray[i] = "mattmc:air";
            }
        }
        
        // Get block states
        long[] blockStates = section.getLongArray("BlockStates");
        if (blockStates == null) {
            return;
        }
        
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
