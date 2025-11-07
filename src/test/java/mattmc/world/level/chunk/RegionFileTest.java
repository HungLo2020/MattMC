package mattmc.world.level.chunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegionFile sector allocation and space reuse.
 */
public class RegionFileTest {
    
    @Test
    public void testWriteAndReadChunk(@TempDir Path tempDir) throws IOException {
        Path regionPath = tempDir.resolve("r.0.0.mca");
        
        try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
            // Create test chunk data
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("xPos", 5);
            chunkData.put("zPos", 10);
            chunkData.put("DataVersion", 1);
            
            // Write chunk
            regionFile.writeChunk(5, 10, chunkData);
            regionFile.flush();
            
            // Read chunk back
            Map<String, Object> readData = regionFile.readChunk(5, 10);
            
            assertNotNull(readData, "Chunk data should be readable");
            assertEquals(5, readData.get("xPos"), "Chunk X position should match");
            assertEquals(10, readData.get("zPos"), "Chunk Z position should match");
        }
    }
    
    @Test
    public void testHasChunk(@TempDir Path tempDir) throws IOException {
        Path regionPath = tempDir.resolve("r.0.0.mca");
        
        try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
            // Initially should not have chunk
            assertFalse(regionFile.hasChunk(0, 0), "Should not have chunk initially");
            
            // Write chunk
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("xPos", 0);
            chunkData.put("zPos", 0);
            regionFile.writeChunk(0, 0, chunkData);
            
            // Now should have chunk
            assertTrue(regionFile.hasChunk(0, 0), "Should have chunk after writing");
        }
    }
    
    @Test
    public void testOverwriteChunkReusesSpace(@TempDir Path tempDir) throws IOException {
        Path regionPath = tempDir.resolve("r.0.0.mca");
        
        long fileSizeAfterFirstWrite;
        long fileSizeAfterSecondWrite;
        
        try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
            // Write chunk first time
            Map<String, Object> chunkData1 = new HashMap<>();
            chunkData1.put("xPos", 0);
            chunkData1.put("zPos", 0);
            chunkData1.put("test", "data1");
            
            regionFile.writeChunk(0, 0, chunkData1);
            regionFile.flush();
            
            fileSizeAfterFirstWrite = regionPath.toFile().length();
            
            // Overwrite with similar sized data
            Map<String, Object> chunkData2 = new HashMap<>();
            chunkData2.put("xPos", 0);
            chunkData2.put("zPos", 0);
            chunkData2.put("test", "data2");
            
            regionFile.writeChunk(0, 0, chunkData2);
            regionFile.flush();
            
            fileSizeAfterSecondWrite = regionPath.toFile().length();
        }
        
        // File size should not grow significantly (should reuse space)
        // Allow for small header updates but file shouldn't double in size
        assertTrue(fileSizeAfterSecondWrite <= fileSizeAfterFirstWrite + 8192, 
                   "File should reuse space when overwriting chunk");
    }
    
    @Test
    public void testMultipleChunksInRegion(@TempDir Path tempDir) throws IOException {
        Path regionPath = tempDir.resolve("r.0.0.mca");
        
        try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
            // Write multiple chunks
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("xPos", x);
                    chunkData.put("zPos", z);
                    
                    regionFile.writeChunk(x, z, chunkData);
                }
            }
            
            regionFile.flush();
            
            // Verify all chunks can be read back
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    assertTrue(regionFile.hasChunk(x, z), 
                              "Chunk (" + x + ", " + z + ") should exist");
                    
                    Map<String, Object> readData = regionFile.readChunk(x, z);
                    assertNotNull(readData, "Should read chunk data");
                    assertEquals(x, readData.get("xPos"));
                    assertEquals(z, readData.get("zPos"));
                }
            }
        }
    }
    
    @Test
    public void testPersistenceAcrossReopens(@TempDir Path tempDir) throws IOException {
        Path regionPath = tempDir.resolve("r.0.0.mca");
        
        // Write chunk and close
        try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("xPos", 0);
            chunkData.put("zPos", 0);
            chunkData.put("test", "persistent");
            
            regionFile.writeChunk(0, 0, chunkData);
            regionFile.flush();
        }
        
        // Reopen and verify chunk still exists
        try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
            assertTrue(regionFile.hasChunk(0, 0), "Chunk should persist after reopen");
            
            Map<String, Object> readData = regionFile.readChunk(0, 0);
            assertNotNull(readData);
            assertEquals("persistent", readData.get("test"));
        }
    }
}
