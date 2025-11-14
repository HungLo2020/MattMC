package mattmc.world.level.chunk;

/**
 * Stores light data for a 16x16x16 chunk section using nibble arrays.
 * Each light value is stored in 4 bits (0-15).
 * 
 * This class handles two types of lighting:
 * - skyLight: Light from the sky (0-15, where 15 is full daylight)
 * - blockLight: Light emitted by blocks (0-15, where 15 is brightest)
 * 
 * Storage format: 4096 values (16x16x16) packed into 2048 bytes per light type.
 */
public class LightStorage {
	private static final int SECTION_SIZE = 16;
	private static final int TOTAL_BLOCKS = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE; // 4096
	private static final int ARRAY_SIZE = TOTAL_BLOCKS / 2; // 2048 bytes (2 values per byte)
	
	// Nibble arrays: 4 bits per value, packed into bytes
	private final byte[] skyLight;
	private final byte[] blockLight;
	
	/**
	 * Create a new light storage with default values.
	 * SkyLight defaults to 15 (full brightness).
	 * BlockLight defaults to 0 (no light).
	 */
	public LightStorage() {
		this.skyLight = new byte[ARRAY_SIZE];
		this.blockLight = new byte[ARRAY_SIZE];
		
		// Initialize skylight to 15 (full brightness)
		for (int i = 0; i < ARRAY_SIZE; i++) {
			skyLight[i] = (byte) 0xFF; // 0xFF = 15 in both nibbles
		}
		// blockLight is already initialized to 0
	}
	
	/**
	 * Create light storage from existing data.
	 * @param skyLight Sky light nibble array (2048 bytes)
	 * @param blockLight Block light nibble array (2048 bytes)
	 */
	public LightStorage(byte[] skyLight, byte[] blockLight) {
		if (skyLight == null || skyLight.length != ARRAY_SIZE) {
			throw new IllegalArgumentException("SkyLight array must be " + ARRAY_SIZE + " bytes");
		}
		if (blockLight == null || blockLight.length != ARRAY_SIZE) {
			throw new IllegalArgumentException("BlockLight array must be " + ARRAY_SIZE + " bytes");
		}
		
		this.skyLight = skyLight.clone();
		this.blockLight = blockLight.clone();
	}
	
	/**
	 * Get the array index and nibble position for block coordinates.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return index in the byte array
	 */
	private int getIndex(int x, int y, int z) {
		if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
			throw new IllegalArgumentException("Coordinates out of bounds: " + x + ", " + y + ", " + z);
		}
		// Convert 3D coordinates to 1D index
		int blockIndex = (y * SECTION_SIZE * SECTION_SIZE) + (z * SECTION_SIZE) + x;
		// Each byte stores 2 nibbles, so divide by 2
		return blockIndex / 2;
	}
	
	/**
	 * Check if the block index is in the lower nibble (even) or upper nibble (odd).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return true if lower nibble, false if upper nibble
	 */
	private boolean isLowerNibble(int x, int y, int z) {
		int blockIndex = (y * SECTION_SIZE * SECTION_SIZE) + (z * SECTION_SIZE) + x;
		return (blockIndex & 1) == 0; // Even index = lower nibble
	}
	
	/**
	 * Get sky light level at position.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Sky light level (0-15)
	 */
	public int getSkyLight(int x, int y, int z) {
		int index = getIndex(x, y, z);
		byte value = skyLight[index];
		
		if (isLowerNibble(x, y, z)) {
			return value & 0x0F; // Lower 4 bits
		} else {
			return (value >> 4) & 0x0F; // Upper 4 bits
		}
	}
	
	/**
	 * Set sky light level at position.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @param level Light level (0-15)
	 */
	public void setSkyLight(int x, int y, int z, int level) {
		if (level < 0 || level > 15) {
			throw new IllegalArgumentException("Light level must be 0-15, got: " + level);
		}
		
		int index = getIndex(x, y, z);
		byte currentByte = skyLight[index];
		
		if (isLowerNibble(x, y, z)) {
			// Set lower nibble, preserve upper nibble
			skyLight[index] = (byte) ((currentByte & 0xF0) | level);
		} else {
			// Set upper nibble, preserve lower nibble
			skyLight[index] = (byte) ((currentByte & 0x0F) | (level << 4));
		}
	}
	
	/**
	 * Get block light level at position.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light level (0-15)
	 */
	public int getBlockLight(int x, int y, int z) {
		int index = getIndex(x, y, z);
		byte value = blockLight[index];
		
		if (isLowerNibble(x, y, z)) {
			return value & 0x0F; // Lower 4 bits
		} else {
			return (value >> 4) & 0x0F; // Upper 4 bits
		}
	}
	
	/**
	 * Set block light level at position.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @param level Light level (0-15)
	 */
	public void setBlockLight(int x, int y, int z, int level) {
		if (level < 0 || level > 15) {
			throw new IllegalArgumentException("Light level must be 0-15, got: " + level);
		}
		
		int index = getIndex(x, y, z);
		byte currentByte = blockLight[index];
		
		if (isLowerNibble(x, y, z)) {
			// Set lower nibble, preserve upper nibble
			blockLight[index] = (byte) ((currentByte & 0xF0) | level);
		} else {
			// Set upper nibble, preserve lower nibble
			blockLight[index] = (byte) ((currentByte & 0x0F) | (level << 4));
		}
	}
	
	/**
	 * Get the raw sky light array for serialization.
	 * @return Clone of sky light array (2048 bytes)
	 */
	public byte[] getSkyLightArray() {
		return skyLight.clone();
	}
	
	/**
	 * Get the raw block light array for serialization.
	 * @return Clone of block light array (2048 bytes)
	 */
	public byte[] getBlockLightArray() {
		return blockLight.clone();
	}
}
