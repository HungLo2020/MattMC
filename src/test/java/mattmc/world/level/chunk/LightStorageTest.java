package mattmc.world.level.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for LightStorage class.
 * Verifies nibble array storage and retrieval of light values.
 */
public class LightStorageTest {

@Test
public void testDefaultInitialization() {
LightStorage storage = new LightStorage();

// Sky light should default to 15 (full brightness)
assertEquals(15, storage.getSkyLight(0, 0, 0));
assertEquals(15, storage.getSkyLight(15, 15, 15));

// Block light should default to 0 (no light)
assertEquals(0, storage.getBlockLight(0, 0, 0));
assertEquals(0, storage.getBlockLight(15, 15, 15));
}

@Test
public void testSetAndGetSkyLight() {
LightStorage storage = new LightStorage();

storage.setSkyLight(0, 0, 0, 0);
assertEquals(0, storage.getSkyLight(0, 0, 0));

storage.setSkyLight(1, 2, 3, 7);
assertEquals(7, storage.getSkyLight(1, 2, 3));

storage.setSkyLight(15, 15, 15, 15);
assertEquals(15, storage.getSkyLight(15, 15, 15));
}

@Test
public void testSetAndGetBlockLight() {
LightStorage storage = new LightStorage();

storage.setBlockLight(0, 0, 0, 0);
assertEquals(0, storage.getBlockLight(0, 0, 0));

storage.setBlockLight(1, 2, 3, 7);
assertEquals(7, storage.getBlockLight(1, 2, 3));

storage.setBlockLight(15, 15, 15, 15);
assertEquals(15, storage.getBlockLight(15, 15, 15));
}

@Test
public void testArraySerialization() {
LightStorage storage = new LightStorage();

storage.setSkyLight(5, 5, 5, 10);
storage.setBlockLight(5, 5, 5, 8);

byte[] skyLight = storage.getSkyLightArray();
byte[] blockLight = storage.getBlockLightArray();

assertEquals(2048, skyLight.length);
assertEquals(8192, blockLight.length);

LightStorage restored = new LightStorage(skyLight, blockLight);

assertEquals(10, restored.getSkyLight(5, 5, 5));
assertEquals(8, restored.getBlockLight(5, 5, 5));
}
}
