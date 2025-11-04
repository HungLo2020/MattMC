package mattmc.world.level.chunk;

import mattmc.client.Minecraft;

import mattmc.nbt.NBTUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Represents a Minecraft-style region file (.mca) that stores 32x32 chunks.
 * Based on the Anvil format used in modern Minecraft Java Edition.
 */
public class RegionFile implements AutoCloseable {
    public static final int REGION_SIZE = 32; // 32x32 chunks per region
    private static final int SECTOR_SIZE = 4096; // 4KB sectors
    private static final int HEADER_SIZE = 2 * SECTOR_SIZE; // 8KB header (locations + timestamps)
    
    private final Path filePath;
    private final int regionX;
    private final int regionZ;
    
    // LevelChunk location table: offset (3 bytes) + sector count (1 byte) for each chunk
    private final int[] locations = new int[REGION_SIZE * REGION_SIZE];
    // Timestamp table: last modified timestamp for each chunk
    private final int[] timestamps = new int[REGION_SIZE * REGION_SIZE];
    
    public RegionFile(Path filePath, int regionX, int regionZ) throws IOException {
        this.filePath = filePath;
        this.regionX = regionX;
        this.regionZ = regionZ;
        
        if (Files.exists(filePath)) {
            loadHeader();
        } else {
            // New file, initialize empty
            Files.createDirectories(filePath.getParent());
        }
    }
    
    /**
     * Get the region coordinates for a given chunk position.
     */
    public static int[] getRegionCoords(int chunkX, int chunkZ) {
        return new int[] {
            Math.floorDiv(chunkX, REGION_SIZE),
            Math.floorDiv(chunkZ, REGION_SIZE)
        };
    }
    
    /**
     * Get the local chunk index within the region (0-1023).
     */
    private static int getChunkIndex(int chunkX, int chunkZ) {
        int localX = Math.floorMod(chunkX, REGION_SIZE);
        int localZ = Math.floorMod(chunkZ, REGION_SIZE);
        return localX + localZ * REGION_SIZE;
    }
    
    /**
     * Load the header (location and timestamp tables) from the region file.
     */
    private void loadHeader() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            if (raf.length() < HEADER_SIZE) {
                return; // Incomplete/corrupted header
            }
            
            // Read location table
            byte[] locationData = new byte[SECTOR_SIZE];
            raf.readFully(locationData);
            ByteBuffer locationBuffer = ByteBuffer.wrap(locationData);
            for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
                locations[i] = locationBuffer.getInt();
            }
            
            // Read timestamp table
            byte[] timestampData = new byte[SECTOR_SIZE];
            raf.readFully(timestampData);
            ByteBuffer timestampBuffer = ByteBuffer.wrap(timestampData);
            for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
                timestamps[i] = timestampBuffer.getInt();
            }
        }
    }
    
    /**
     * Save the header (location and timestamp tables) to the region file.
     */
    private void saveHeader(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        
        // Write location table
        ByteBuffer locationBuffer = ByteBuffer.allocate(SECTOR_SIZE);
        for (int location : locations) {
            locationBuffer.putInt(location);
        }
        raf.write(locationBuffer.array());
        
        // Write timestamp table
        ByteBuffer timestampBuffer = ByteBuffer.allocate(SECTOR_SIZE);
        for (int timestamp : timestamps) {
            timestampBuffer.putInt(timestamp);
        }
        raf.write(timestampBuffer.array());
    }
    
    /**
     * Read chunk NBT data from the region file.
     */
    public Map<String, Object> readChunk(int chunkX, int chunkZ) throws IOException {
        int index = getChunkIndex(chunkX, chunkZ);
        int location = locations[index];
        
        if (location == 0) {
            return null; // LevelChunk not present
        }
        
        int offset = (location >> 8) & 0xFFFFFF; // 3 bytes: sector offset
        int sectorCount = location & 0xFF; // 1 byte: sector count
        
        if (offset < 2 || sectorCount == 0) {
            return null; // Invalid location
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            raf.seek(offset * SECTOR_SIZE);
            
            // Read chunk length (4 bytes)
            int length = raf.readInt();
            if (length <= 0 || length > sectorCount * SECTOR_SIZE) {
                return null; // Invalid length
            }
            
            // Read compression type (1 byte)
            byte compressionType = raf.readByte();
            
            // Read compressed data
            byte[] compressedData = new byte[length - 1];
            raf.readFully(compressedData);
            
            // Decompress and parse NBT
            InputStream input = new ByteArrayInputStream(compressedData);
            
            if (compressionType == 2) { // Zlib (deflate)
                return NBTUtil.readDeflated(input);
            } else {
                return null; // Unsupported compression
            }
        }
    }
    
    /**
     * Write chunk NBT data to the region file.
     */
    public void writeChunk(int chunkX, int chunkZ, Map<String, Object> chunkData) throws IOException {
        int index = getChunkIndex(chunkX, chunkZ);
        
        // Serialize and compress chunk data
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        NBTUtil.writeDeflated(chunkData, byteOut);
        byte[] compressedData = byteOut.toByteArray();
        
        // Calculate sectors needed (1 byte for compression type + compressed data + 4 bytes for length)
        int dataLength = compressedData.length + 1; // +1 for compression type byte
        int sectorsNeeded = (dataLength + 4 + SECTOR_SIZE - 1) / SECTOR_SIZE; // +4 for length int
        
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            // Find free space or reuse existing space
            int offset = findFreeSpace(raf, sectorsNeeded);
            
            // Write chunk data
            raf.seek(offset * SECTOR_SIZE);
            raf.writeInt(dataLength);
            raf.writeByte(2); // Compression type: 2 = zlib (deflate)
            raf.write(compressedData);
            
            // Pad to sector boundary
            int written = 4 + 1 + compressedData.length;
            int padding = (sectorsNeeded * SECTOR_SIZE) - written;
            if (padding > 0) {
                raf.write(new byte[padding]);
            }
            
            // Update location table
            locations[index] = (offset << 8) | (sectorsNeeded & 0xFF);
            timestamps[index] = (int) (System.currentTimeMillis() / 1000L);
            
            // Save header
            saveHeader(raf);
        }
    }
    
    /**
     * Find free space in the region file for the given number of sectors.
     * Returns the sector offset where data can be written.
     */
    private int findFreeSpace(RandomAccessFile raf, int sectorsNeeded) throws IOException {
        // Simple allocation: append at the end
        // A more sophisticated implementation would reuse freed space
        long fileLength = raf.length();
        int nextSector = Math.max(2, (int) ((fileLength + SECTOR_SIZE - 1) / SECTOR_SIZE));
        return nextSector;
    }
    
    /**
     * Check if a chunk exists in this region.
     */
    public boolean hasChunk(int chunkX, int chunkZ) {
        int index = getChunkIndex(chunkX, chunkZ);
        return locations[index] != 0;
    }
    
    public int getRegionX() {
        return regionX;
    }
    
    public int getRegionZ() {
        return regionZ;
    }
    
    public Path getFilePath() {
        return filePath;
    }
    
    @Override
    public void close() {
        // No persistent handles to close in this implementation
    }
}
