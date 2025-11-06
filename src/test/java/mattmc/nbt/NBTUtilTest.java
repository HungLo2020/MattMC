package mattmc.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NBTUtil exception handling and basic functionality.
 */
class NBTUtilTest {
    
    @Test
    void testBasicCompoundReadWrite(@TempDir Path tempDir) throws IOException {
        // Create a test compound
        Map<String, Object> compound = new HashMap<>();
        compound.put("testByte", (byte) 42);
        compound.put("testInt", 12345);
        compound.put("testLong", 9876543210L);
        compound.put("testFloat", 3.14f);
        compound.put("testDouble", 2.71828);
        compound.put("testString", "Hello NBT");
        compound.put("testByteArray", new byte[]{1, 2, 3, 4, 5});
        compound.put("testLongArray", new long[]{100L, 200L, 300L});
        
        // Write and read compressed
        File testFile = tempDir.resolve("test.nbt").toFile();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            NBTUtil.writeCompressed(compound, fos);
        }
        
        Map<String, Object> readCompound;
        try (FileInputStream fis = new FileInputStream(testFile)) {
            readCompound = NBTUtil.readCompressed(fis);
        }
        
        // Verify
        assertEquals((byte) 42, readCompound.get("testByte"));
        assertEquals(12345, readCompound.get("testInt"));
        assertEquals(9876543210L, readCompound.get("testLong"));
        assertEquals(3.14f, readCompound.get("testFloat"));
        assertEquals(2.71828, readCompound.get("testDouble"));
        assertEquals("Hello NBT", readCompound.get("testString"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, (byte[]) readCompound.get("testByteArray"));
        assertArrayEquals(new long[]{100L, 200L, 300L}, (long[]) readCompound.get("testLongArray"));
    }
    
    @Test
    void testBasicDeflatedReadWrite(@TempDir Path tempDir) throws IOException {
        // Create a test compound
        Map<String, Object> compound = new HashMap<>();
        compound.put("testInt", 999);
        compound.put("testString", "Deflated");
        
        // Write and read deflated
        File testFile = tempDir.resolve("test_deflated.nbt").toFile();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            NBTUtil.writeDeflated(compound, fos);
        }
        
        Map<String, Object> readCompound;
        try (FileInputStream fis = new FileInputStream(testFile)) {
            readCompound = NBTUtil.readDeflated(fis);
        }
        
        // Verify
        assertEquals(999, readCompound.get("testInt"));
        assertEquals("Deflated", readCompound.get("testString"));
    }
    
    @Test
    void testNestedCompounds(@TempDir Path tempDir) throws IOException {
        // Create nested compounds
        Map<String, Object> inner = new HashMap<>();
        inner.put("innerValue", 123);
        
        Map<String, Object> outer = new HashMap<>();
        outer.put("outerValue", 456);
        outer.put("innerCompound", inner);
        
        // Write and read
        File testFile = tempDir.resolve("nested.nbt").toFile();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            NBTUtil.writeCompressed(outer, fos);
        }
        
        Map<String, Object> readCompound;
        try (FileInputStream fis = new FileInputStream(testFile)) {
            readCompound = NBTUtil.readCompressed(fis);
        }
        
        // Verify
        assertEquals(456, readCompound.get("outerValue"));
        @SuppressWarnings("unchecked")
        Map<String, Object> readInner = (Map<String, Object>) readCompound.get("innerCompound");
        assertNotNull(readInner);
        assertEquals(123, readInner.get("innerValue"));
    }
    
    @Test
    void testLists(@TempDir Path tempDir) throws IOException {
        // Create a compound with lists
        Map<String, Object> compound = new HashMap<>();
        compound.put("intList", List.of(1, 2, 3, 4, 5));
        compound.put("stringList", List.of("a", "b", "c"));
        
        // Write and read
        File testFile = tempDir.resolve("lists.nbt").toFile();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            NBTUtil.writeCompressed(compound, fos);
        }
        
        Map<String, Object> readCompound;
        try (FileInputStream fis = new FileInputStream(testFile)) {
            readCompound = NBTUtil.readCompressed(fis);
        }
        
        // Verify
        @SuppressWarnings("unchecked")
        List<Object> intList = (List<Object>) readCompound.get("intList");
        assertNotNull(intList);
        assertEquals(5, intList.size());
        assertEquals(1, intList.get(0));
        assertEquals(5, intList.get(4));
        
        @SuppressWarnings("unchecked")
        List<Object> stringList = (List<Object>) readCompound.get("stringList");
        assertNotNull(stringList);
        assertEquals(3, stringList.size());
        assertEquals("a", stringList.get(0));
        assertEquals("c", stringList.get(2));
    }
    
    @Test
    void testInvalidRootTagThrowsDeserializationException() throws IOException {
        // Create a stream with invalid root tag type (compressed with GZIP)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            dos.writeByte(1); // TAG_BYTE instead of TAG_COMPOUND
            dos.writeUTF("invalid");
            dos.writeByte(42);
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        
        NBTDeserializationException exception = assertThrows(NBTDeserializationException.class, () -> {
            NBTUtil.readCompressed(bais);
        });
        
        assertTrue(exception.getMessage().contains("Root tag must be compound"));
        assertEquals(1, exception.getTagType());
    }
    
    @Test
    void testInvalidByteArrayLengthThrowsDeserializationException() throws IOException {
        // Create a compound with an invalid byte array length
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            // Write compound header
            dos.writeByte(10); // TAG_COMPOUND
            dos.writeUTF(""); // root name
            
            // Write byte array tag with invalid length
            dos.writeByte(7); // TAG_BYTE_ARRAY
            dos.writeUTF("badArray");
            dos.writeInt(-1); // Invalid length
            
            dos.writeByte(0); // TAG_END
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        
        NBTDeserializationException exception = assertThrows(NBTDeserializationException.class, () -> {
            NBTUtil.readCompressed(bais);
        });
        
        assertTrue(exception.getMessage().contains("Invalid byte array length") || 
                   exception.getMessage().contains("badArray"));
    }
    
    @Test
    void testInvalidLongArrayLengthThrowsDeserializationException() throws IOException {
        // Create a compound with an invalid long array length
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            // Write compound header
            dos.writeByte(10); // TAG_COMPOUND
            dos.writeUTF(""); // root name
            
            // Write long array tag with excessive length
            dos.writeByte(12); // TAG_LONG_ARRAY
            dos.writeUTF("badLongArray");
            dos.writeInt(Integer.MAX_VALUE); // Too large
            
            dos.writeByte(0); // TAG_END
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        
        NBTDeserializationException exception = assertThrows(NBTDeserializationException.class, () -> {
            NBTUtil.readCompressed(bais);
        });
        
        assertTrue(exception.getMessage().contains("Invalid long array length") || 
                   exception.getMessage().contains("badLongArray"));
    }
    
    @Test
    void testInvalidListLengthThrowsDeserializationException() throws IOException {
        // Create a compound with an invalid list length
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            // Write compound header
            dos.writeByte(10); // TAG_COMPOUND
            dos.writeUTF(""); // root name
            
            // Write list tag with excessive length
            dos.writeByte(9); // TAG_LIST
            dos.writeUTF("badList");
            dos.writeByte(3); // TAG_INT
            dos.writeInt(Integer.MAX_VALUE); // Too large
            
            dos.writeByte(0); // TAG_END
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        
        NBTDeserializationException exception = assertThrows(NBTDeserializationException.class, () -> {
            NBTUtil.readCompressed(bais);
        });
        
        assertTrue(exception.getMessage().contains("Invalid list length") || 
                   exception.getMessage().contains("badList"));
    }
    
    @Test
    void testUnknownTagTypeThrowsDeserializationException() throws IOException {
        // Create a compound with an unknown tag type
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            // Write compound header
            dos.writeByte(10); // TAG_COMPOUND
            dos.writeUTF(""); // root name
            
            // Write tag with invalid type
            dos.writeByte(99); // Unknown tag type
            dos.writeUTF("unknownTag");
            
            dos.writeByte(0); // TAG_END
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        
        NBTDeserializationException exception = assertThrows(NBTDeserializationException.class, () -> {
            NBTUtil.readCompressed(bais);
        });
        
        assertTrue(exception.getMessage().contains("Unknown tag type"));
        assertEquals(99, exception.getTagType());
    }
    
    @Test
    void testSerializationExceptionContextForWriteFailure() {
        // Create a stream that will fail on write
        OutputStream failingStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Simulated write failure");
            }
        };
        
        Map<String, Object> compound = new HashMap<>();
        compound.put("test", 123);
        
        NBTSerializationException exception = assertThrows(NBTSerializationException.class, () -> {
            NBTUtil.writeCompressed(compound, failingStream);
        });
        
        assertTrue(exception.getMessage().contains("Failed to"));
        assertTrue(exception.getMessage().contains("NBT"));
    }
    
    @Test
    void testDeserializationExceptionContextForReadFailure() {
        // Create a stream that will fail on read
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated read failure");
            }
        };
        
        NBTDeserializationException exception = assertThrows(NBTDeserializationException.class, () -> {
            NBTUtil.readCompressed(failingStream);
        });
        
        assertTrue(exception.getMessage().contains("Failed to"));
        assertTrue(exception.getMessage().contains("NBT"));
    }
    
    @Test
    void testExceptionPropertiesAreSet() {
        String testName = "testTag";
        String testType = "TEST_TYPE";
        NBTSerializationException serEx = new NBTSerializationException("Test", testName, testType);
        
        assertEquals(testName, serEx.getTagName());
        assertEquals(testType, serEx.getTagType());
        assertTrue(serEx.getMessage().contains(testName));
        assertTrue(serEx.getMessage().contains(testType));
    }
    
    @Test
    void testDeserializationExceptionPropertiesAreSet() {
        String testName = "testTag";
        byte testType = 10;
        long testPosition = 42;
        NBTDeserializationException deserEx = new NBTDeserializationException("Test", testName, testType, testPosition);
        
        assertEquals(testName, deserEx.getTagName());
        assertEquals(testType, deserEx.getTagType());
        assertEquals(testPosition, deserEx.getStreamPosition());
        assertTrue(deserEx.getMessage().contains(testName));
        assertTrue(deserEx.getMessage().contains("42"));
    }
}
