package net.minecraft.worldedit.schematic;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.clipboard.Clipboard;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.BlockPatternParser;
import net.minecraft.worldedit.region.CuboidRegion;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles schematic file I/O operations.
 * Supports basic schematic format for saving and loading structures.
 */
public class SchematicHandler {
    
    private final File schematicDirectory;
    
    public SchematicHandler(File worldDirectory) {
        this.schematicDirectory = new File(worldDirectory, "schematics");
        if (!schematicDirectory.exists()) {
            schematicDirectory.mkdirs();
        }
    }
    
    /**
     * Save a clipboard to a schematic file.
     */
    public void save(Clipboard clipboard, String name) throws IOException {
        File file = new File(schematicDirectory, name + ".schem");
        
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2); // Sponge schematic format version 2
        root.putInt("DataVersion", 2975); // MC 1.21 data version
        
        // Metadata
        CompoundTag metadata = new CompoundTag();
        metadata.putLong("Date", System.currentTimeMillis());
        root.put("Metadata", metadata);
        
        // Dimensions
        BlockVector3 dimensions = clipboard.getDimensions();
        root.putShort("Width", (short) dimensions.getX());
        root.putShort("Height", (short) dimensions.getY());
        root.putShort("Length", (short) dimensions.getZ());
        
        // Offset (origin) - store as relative to schematic coordinates
        // Preserve the original clipboard anchor relative to minimum corner.
        // This keeps paste positioning identical after save/load round-trips.
        BlockVector3 clipboardOffset = clipboard.getOffset();
        int[] offset = new int[3];
        offset[0] = clipboardOffset.getX();
        offset[1] = clipboardOffset.getY();
        offset[2] = clipboardOffset.getZ();
        root.putIntArray("Offset", offset);
        
        // Save palette and block data
        Map<BlockState, Integer> palette = new HashMap<>();
        byte[] blocks = new byte[dimensions.getX() * dimensions.getY() * dimensions.getZ()];
        
        int paletteIndex = 0;
        int blockIndex = 0;
        
        BlockVector3 min = clipboard.getMinimumPoint();
        for (int y = 0; y < dimensions.getY(); y++) {
            for (int z = 0; z < dimensions.getZ(); z++) {
                for (int x = 0; x < dimensions.getX(); x++) {
                    BlockVector3 pos = min.add(x, y, z);
                    BlockState state = clipboard.getBlock(pos);
                    
                    if (!palette.containsKey(state)) {
                        palette.put(state, paletteIndex++);
                    }
                    
                    blocks[blockIndex++] = palette.get(state).byteValue();
                }
            }
        }
        
        // Write palette
        CompoundTag paletteTag = new CompoundTag();
        for (Map.Entry<BlockState, Integer> entry : palette.entrySet()) {
            paletteTag.putInt(BlockStateParser.serialize(entry.getKey()), entry.getValue());
        }
        root.put("Palette", paletteTag);
        
        // Write block data
        root.putByteArray("BlockData", blocks);
        
        // Save to file using Path API
        NbtIo.writeCompressed(root, file.toPath());
        
        System.out.println("Saved schematic '" + name + "' with " + palette.size() + " unique blocks and " + blocks.length + " total blocks");
    }
    
    /**
     * Load a schematic file into a clipboard.
     */
    public Clipboard load(String name) throws IOException {
        File file = new File(schematicDirectory, name + ".schem");
        if (!file.exists()) {
            throw new IOException("Schematic file not found: " + name);
        }
        
        // Read NBT data using Path API
        CompoundTag root = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
        
        // Read dimensions
        short width = root.getShortOr("Width", (short) 0);
        short height = root.getShortOr("Height", (short) 0);
        short length = root.getShortOr("Length", (short) 0);
        
        // Read offset as clipboard origin relative to minimum corner.
        // The loaded clipboard uses a 0-based region min, so origin equals this offset.
        int[] offsetArray = root.getIntArray("Offset").orElse(new int[]{0, 0, 0});
        BlockVector3 origin = BlockVector3.ZERO;
        if (offsetArray.length >= 3) {
            origin = BlockVector3.at(offsetArray[0], offsetArray[1], offsetArray[2]);
        }
        
        // Create region and clipboard
        BlockVector3 min = BlockVector3.ZERO;
        BlockVector3 max = min.add(width - 1, height - 1, length - 1);
        CuboidRegion region = new CuboidRegion(min, max);
        Clipboard clipboard = new Clipboard(region, origin);
        
        // Read palette
        CompoundTag paletteTag = root.getCompoundOrEmpty("Palette");
        Map<Integer, BlockState> palette = new HashMap<>();
        
        for (String blockName : paletteTag.keySet()) {
            int paletteId = paletteTag.getIntOr(blockName, 0);
            
            try {
                palette.put(paletteId, BlockPatternParser.parseSingleBlockState(blockName));
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Invalid block state in schematic: " + blockName);
                palette.put(paletteId, Blocks.AIR.defaultBlockState());
            }
        }
        
        // Read block data
        byte[] blocks = root.getByteArray("BlockData").orElse(new byte[0]);
        
        // Reconstruct blocks in clipboard
        int blockIndex = 0;
        boolean arrayTooShort = false;
        for (int y = 0; y < height && !arrayTooShort; y++) {
            for (int z = 0; z < length && !arrayTooShort; z++) {
                for (int x = 0; x < width; x++) {
                    if (blockIndex >= blocks.length) {
                        System.err.println("Warning: Block data array is shorter than expected");
                        arrayTooShort = true;
                        break;
                    }
                    
                    int paletteId = blocks[blockIndex++] & 0xFF; // Convert byte to unsigned
                    BlockState state = palette.get(paletteId);
                    
                    if (state != null) {
                        BlockVector3 pos = min.add(x, y, z);
                        clipboard.setBlock(pos, state);
                    }
                }
            }
        }
        
        System.out.println("Loaded schematic '" + name + "' with " + clipboard.getVolume() + " blocks");
        
        return clipboard;
    }
    
    /**
     * List all available schematics.
     */
    public String[] listSchematics() {
        File[] files = schematicDirectory.listFiles((dir, name) -> name.endsWith(".schem"));
        if (files == null) {
            return new String[0];
        }
        
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            names[i] = name.substring(0, name.length() - 6); // Remove .schem extension
        }
        
        return names;
    }
    
    /**
     * Delete a schematic file.
     */
    public boolean delete(String name) {
        File file = new File(schematicDirectory, name + ".schem");
        return file.exists() && file.delete();
    }
}
