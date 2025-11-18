package mattmc.world.level.chunk;

/**
 * Stores light data for a 16x16x16 chunk section using packed arrays.
 * Light values use different bit depths for efficiency:
 * - skyLight: 4 bits per value (0-15)
 * - blockLight RGB: 5 bits per channel (0-31), packed into 2 bytes per position (15 bits total)
 * 
 * Storage format: 
 * - skyLight: 4096 values packed into 2048 bytes (nibble array)
 * - blockLight: 4096 RGB values packed into 8192 bytes (2 bytes per position for RGB)
 */
public class LightStorage {
	private static final int SECTION_SIZE = 16;
	private static final int TOTAL_BLOCKS = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE; // 4096
	private static final int SKYLIGHT_ARRAY_SIZE = TOTAL_BLOCKS / 2; // 2048 bytes (2 values per byte)
	private static final int BLOCKLIGHT_ARRAY_SIZE = TOTAL_BLOCKS * 2; // 8192 bytes (2 bytes per RGB value)
	
	// Nibble array for skylight: 4 bits per value, packed into bytes
	private final byte[] skyLight;
	// Packed RGB array for block light: 5 bits per channel (R=5, G=5, B=5 = 15 bits), stored in 2 bytes
	// Format per 2 bytes: RRRRRGGG GGBBBBB (5R, 5G, 5B)
	private final byte[] blockLight;
	
	/**
	 * Create a new light storage with default values.
	 * SkyLight defaults to 15 (full brightness).
	 * BlockLight RGBI defaults to 0 (no light).
	 */
	public LightStorage() {
		this.skyLight = new byte[SKYLIGHT_ARRAY_SIZE];
		this.blockLight = new byte[BLOCKLIGHT_ARRAY_SIZE];
		
		// Initialize skylight to 15 (full brightness)
		for (int i = 0; i < SKYLIGHT_ARRAY_SIZE; i++) {
			skyLight[i] = (byte) 0xFF; // 0xFF = 15 in both nibbles
		}
		// blockLight is already initialized to 0
	}
	
	/**
	 * Create light storage from existing data.
	 * Automatically detects legacy (2048 bytes) or RGBI (8192 bytes) block light format.
	 * @param skyLight Sky light nibble array (2048 bytes)
	 * @param blockLight Block light array (2048 bytes for legacy, 8192 bytes for RGBI)
	 */
	public LightStorage(byte[] skyLight, byte[] blockLight) {
		if (skyLight == null || skyLight.length != SKYLIGHT_ARRAY_SIZE) {
			throw new IllegalArgumentException("SkyLight array must be " + SKYLIGHT_ARRAY_SIZE + " bytes");
		}
		if (blockLight == null) {
			throw new IllegalArgumentException("BlockLight array cannot be null");
		}
		
		this.skyLight = skyLight.clone();
		
		// Detect format by array size
		if (blockLight.length == SKYLIGHT_ARRAY_SIZE) {
			// Legacy format: convert single-channel to white RGBI
			this.blockLight = new byte[BLOCKLIGHT_ARRAY_SIZE];
			
			// Convert legacy nibble format to RGBI format
			for (int i = 0; i < TOTAL_BLOCKS; i++) {
				int nibbleIndex = i / 2;
				boolean isLower = (i & 1) == 0;
				int legacyLight;
				if (isLower) {
					legacyLight = blockLight[nibbleIndex] & 0x0F;
				} else {
					legacyLight = (blockLight[nibbleIndex] >> 4) & 0x0F;
				}
				// Set white RGBI (R=G=B=legacyLight, I=legacyLight)
				setBlockLightRGBIAtIndex(i, legacyLight, legacyLight, legacyLight, legacyLight);
			}
		} else if (blockLight.length == BLOCKLIGHT_ARRAY_SIZE) {
			// RGBI format: use directly
			this.blockLight = blockLight.clone();
		} else {
			throw new IllegalArgumentException("BlockLight array must be " + SKYLIGHT_ARRAY_SIZE + 
				" bytes (legacy) or " + BLOCKLIGHT_ARRAY_SIZE + " bytes (RGBI), got: " + blockLight.length);
		}
	}
	
	/**
	 * Helper method to set RGBI at a specific block index (0-4095).
	 */
	private void setBlockLightRGBIAtIndex(int blockIndex, int r, int g, int b, int i) {
		// Pack RGBI into 2 bytes: RRRRGGGG BBBBIIII
		int packed = ((r & 0x0F) << 12) | ((g & 0x0F) << 8) | ((b & 0x0F) << 4) | (i & 0x0F);
		int byteIndex = blockIndex * 2;
		blockLight[byteIndex] = (byte) (packed >> 8);
		blockLight[byteIndex + 1] = (byte) (packed & 0xFF);
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
	 * Get the block index for 3D coordinates.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return block index (0-4095)
	 */
	private int getBlockIndex(int x, int y, int z) {
		if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
			throw new IllegalArgumentException("Coordinates out of bounds: " + x + ", " + y + ", " + z);
		}
		return (y * SECTION_SIZE * SECTION_SIZE) + (z * SECTION_SIZE) + x;
	}
	
	/**
	 * Get block light RED level at position (0-15).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light red level (0-15)
	 */
	public int getBlockLightR(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return (packed >> 12) & 0x0F; // Extract 4 bits for R
	}
	
	/**
	 * Get block light GREEN level at position (0-15).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light green level (0-15)
	 */
	public int getBlockLightG(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return (packed >> 8) & 0x0F; // Extract 4 bits for G
	}
	
	/**
	 * Get block light BLUE level at position (0-15).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light blue level (0-15)
	 */
	public int getBlockLightB(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return (packed >> 4) & 0x0F; // Extract 4 bits for B
	}
	
	/**
	 * Get block light INTENSITY level at position (0-15).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light intensity level (0-15)
	 */
	public int getBlockLightI(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return packed & 0x0F; // Extract 4 bits for I
	}
	
	/**
	 * Set block light RGBI levels at position.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @param r Red level (0-15)
	 * @param g Green level (0-15)
	 * @param b Blue level (0-15)
	 * @param i Intensity level (0-15)
	 */
	public void setBlockLightRGBI(int x, int y, int z, int r, int g, int b, int i) {
		if (r < 0 || r > 15 || g < 0 || g > 15 || b < 0 || b > 15 || i < 0 || i > 15) {
			throw new IllegalArgumentException("RGBI light levels must be 0-15, got: " + r + ", " + g + ", " + b + ", " + i);
		}
		
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		
		// Pack RGBI into 2 bytes: RRRRGGGG BBBBIIII
		int packed = ((r & 0x0F) << 12) | ((g & 0x0F) << 8) | ((b & 0x0F) << 4) | (i & 0x0F);
		blockLight[byteIndex] = (byte) (packed >> 8);
		blockLight[byteIndex + 1] = (byte) (packed & 0xFF);
	}
	
	/**
	 * Set block light RGB levels at position (intensity = max of RGB).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @param r Red level (0-15)
	 * @param g Green level (0-15)
	 * @param b Blue level (0-15)
	 */
	public void setBlockLightRGB(int x, int y, int z, int r, int g, int b) {
		int intensity = Math.max(r, Math.max(g, b));
		setBlockLightRGBI(x, y, z, r, g, b, intensity);
	}
	
	/**
	
	/**
	 * Get the raw sky light array for serialization.
	 * @return Clone of sky light array (2048 bytes)
	 */
	public byte[] getSkyLightArray() {
		return skyLight.clone();
	}
	
	/**
	 * Get the raw block light RGBI array for serialization.
	 * @return Clone of block light RGBI array (8192 bytes)
	 */
	public byte[] getBlockLightArray() {
		return blockLight.clone();
	}
}

