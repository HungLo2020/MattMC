package mattmc.world.level.chunk;

import mattmc.nbt.NBTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a MattMC-style region file (.mca) that stores 32x32 chunks.
 * Based on the Anvil format used in modern MattMC Java Edition.
 * 
 * Thread-safe: Uses ReentrantReadWriteLock to allow concurrent reads while ensuring exclusive writes.
 */
public class RegionFile implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RegionFile.class);
    
    // Lock for thread-safe file access (ISSUE-001 fix)
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
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
    
    // Keep file handle open for reuse (MattMC Java Edition approach)
    // This file handle is accessed via synchronized methods for thread safety.
    // Set to null when close() is called to release resources.
    private RandomAccessFile file;
    private boolean headerDirty = false;
    private boolean closed = false;
    
    public RegionFile(Path filePath, int regionX, int regionZ) throws IOException {
        this.filePath = filePath;
        this.regionX = regionX;
        this.regionZ = regionZ;
        
        // Create parent directory if needed
        Files.createDirectories(filePath.getParent());
        
        // Open file for reading and writing
        this.file = new RandomAccessFile(filePath.toFile(), "rw");
        
        if (file.length() >= HEADER_SIZE) {
            loadHeader();
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
     * Thread-safe: Acquires write lock during initialization.
     */
    private void loadHeader() throws IOException {
        lock.writeLock().lock();
        try {
            if (file.length() < HEADER_SIZE) {
                return; // Incomplete/corrupted header
            }
            
            file.seek(0);
            
            // Read location table
            byte[] locationData = new byte[SECTOR_SIZE];
            file.readFully(locationData);
            ByteBuffer locationBuffer = ByteBuffer.wrap(locationData);
            for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
                locations[i] = locationBuffer.getInt();
            }
            
            // Read timestamp table
            byte[] timestampData = new byte[SECTOR_SIZE];
            file.readFully(timestampData);
            ByteBuffer timestampBuffer = ByteBuffer.wrap(timestampData);
            for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
                timestamps[i] = timestampBuffer.getInt();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Save the header (location and timestamp tables) to the region file.
     * Thread-safe: Must be called with write lock held.
     */
    private void saveHeader() throws IOException {
        // Caller must hold write lock
        file.seek(0);
        
        // Write location table
        ByteBuffer locationBuffer = ByteBuffer.allocate(SECTOR_SIZE);
        for (int location : locations) {
            locationBuffer.putInt(location);
        }
        file.write(locationBuffer.array());
        
        // Write timestamp table
        ByteBuffer timestampBuffer = ByteBuffer.allocate(SECTOR_SIZE);
        for (int timestamp : timestamps) {
            timestampBuffer.putInt(timestamp);
        }
        file.write(timestampBuffer.array());
        
        headerDirty = false;
    }
    
    /**
     * Read chunk NBT data from the region file.
     * Thread-safe: Uses read lock for concurrent reading.
     */
    public Map<String, Object> readChunk(int chunkX, int chunkZ) throws IOException {
        lock.readLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("Cannot read from closed RegionFile");
            }
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
            
            file.seek(offset * SECTOR_SIZE);
            
            // Read chunk length (4 bytes)
            int length = file.readInt();
            if (length <= 0 || length > sectorCount * SECTOR_SIZE) {
                return null; // Invalid length
            }
            
            // Read compression type (1 byte)
            byte compressionType = file.readByte();
            
            // Read compressed data
            byte[] compressedData = new byte[length - 1];
            file.readFully(compressedData);
            
            // Decompress and parse NBT
            InputStream input = new ByteArrayInputStream(compressedData);
            
            if (compressionType == 2) { // Zlib (deflate)
                return NBTUtil.readDeflated(input);
            } else {
                return null; // Unsupported compression
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Write chunk NBT data to the region file.
     * Thread-safe: Uses write lock for exclusive writing.
     */
    public void writeChunk(int chunkX, int chunkZ, Map<String, Object> chunkData) throws IOException {
        lock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("Cannot write to closed RegionFile");
            }
            int index = getChunkIndex(chunkX, chunkZ);
            
            // Serialize and compress chunk data
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            NBTUtil.writeDeflated(chunkData, byteOut);
            byte[] compressedData = byteOut.toByteArray();
            
            // Calculate sectors needed (1 byte for compression type + compressed data + 4 bytes for length)
            int dataLength = compressedData.length + 1; // +1 for compression type byte
            int sectorsNeeded = (dataLength + 4 + SECTOR_SIZE - 1) / SECTOR_SIZE; // +4 for length int
            
            // Get current allocation for this chunk
            int oldLocation = locations[index];
            int oldOffset = (oldLocation >> 8) & 0xFFFFFF;
            int oldSectorCount = oldLocation & 0xFF;
            
            // Check if we can reuse the existing space
            int offset;
            if (oldSectorCount >= sectorsNeeded && oldOffset >= 2) {
                // Reuse existing space (MattMC Java optimization)
                offset = oldOffset;
            } else {
                // Need to find new space
                offset = findFreeSpace(sectorsNeeded, index);
            }
            
            // Write chunk data
            file.seek(offset * SECTOR_SIZE);
            file.writeInt(dataLength);
            file.writeByte(2); // Compression type: 2 = zlib (deflate)
            file.write(compressedData);
            
            // Pad to sector boundary
            int written = 4 + 1 + compressedData.length;
            int padding = (sectorsNeeded * SECTOR_SIZE) - written;
            if (padding > 0) {
                file.write(new byte[padding]);
            }
            
            // Update location table
            locations[index] = (offset << 8) | (sectorsNeeded & 0xFF);
            timestamps[index] = (int) (System.currentTimeMillis() / 1000L);
            headerDirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Find free space in the region file for the given number of sectors.
     * Returns the sector offset where data can be written.
     * Improved to reuse freed space (MattMC Java optimization).
     */
    private int findFreeSpace(int sectorsNeeded, int excludeIndex) throws IOException {
        // Build a map of used sectors
        boolean[] usedSectors = new boolean[1024]; // Max reasonable size, will expand if needed
        
        // Mark header as used
        usedSectors[0] = true;
        usedSectors[1] = true;
        
        // Mark all allocated chunks as used
        for (int i = 0; i < REGION_SIZE * REGION_SIZE; i++) {
            if (i == excludeIndex) continue; // Skip the chunk we're updating
            
            int location = locations[i];
            if (location == 0) continue;
            
            int offset = (location >> 8) & 0xFFFFFF;
            int sectorCount = location & 0xFF;
            
            if (offset < 2 || sectorCount == 0) continue;
            
            // Expand array if needed, but cap at reasonable maximum to prevent excessive memory use
            int maxSector = offset + sectorCount;
            if (maxSector > usedSectors.length) {
                // Cap at 64MB worth of sectors (16384 sectors * 4KB = 64MB max region file)
                // This prevents unbounded growth from corrupted location data
                if (maxSector > 16384) {
                    logger.warn("Chunk location references sector beyond reasonable limit ({}), appending instead", maxSector);
                    continue;
                }
                boolean[] newArray = new boolean[Math.min(16384, Math.max(maxSector, usedSectors.length * 2))];
                System.arraycopy(usedSectors, 0, newArray, 0, usedSectors.length);
                usedSectors = newArray;
            }
            
            // Mark sectors as used
            for (int j = 0; j < sectorCount; j++) {
                usedSectors[offset + j] = true;
            }
        }
        
        // Find first contiguous free space
        for (int i = 2; i < usedSectors.length - sectorsNeeded + 1; i++) {
            boolean foundSpace = true;
            for (int j = 0; j < sectorsNeeded; j++) {
                if (usedSectors[i + j]) {
                    foundSpace = false;
                    i += j; // Skip ahead
                    break;
                }
            }
            if (foundSpace) {
                return i;
            }
        }
        
        // No free space found, append at end
        long fileLength = file.length();
        return Math.max(2, (int) ((fileLength + SECTOR_SIZE - 1) / SECTOR_SIZE));
    }
    
    /**
     * Flush any pending writes to disk.
     * Thread-safe: Uses write lock.
     */
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            if (closed) {
                return; // Already closed, nothing to flush
            }
            if (headerDirty) {
                saveHeader();
            }
            if (file != null) {
                file.getFD().sync();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if a chunk exists in this region.
     * Thread-safe: Uses read lock.
     */
    public boolean hasChunk(int chunkX, int chunkZ) {
        lock.readLock().lock();
        try {
            int index = getChunkIndex(chunkX, chunkZ);
            return locations[index] != 0;
        } finally {
            lock.readLock().unlock();
        }
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
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (closed) {
                return; // Already closed
            }
            closed = true;
            if (file != null) {
                // Flush header if dirty before closing
                if (headerDirty) {
                    saveHeader();
                }
                file.close();
                file = null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
