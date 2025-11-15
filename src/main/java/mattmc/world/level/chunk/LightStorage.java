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
	 * BlockLight RGB defaults to 0 (no light).
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
	 * Automatically detects legacy (2048 bytes) or RGB (8192 bytes) block light format.
	 * @param skyLight Sky light nibble array (2048 bytes)
	 * @param blockLight Block light array (2048 bytes for legacy, 8192 bytes for RGB)
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
			// Legacy format: convert single-channel to RGB
			this.blockLight = new byte[BLOCKLIGHT_ARRAY_SIZE];
			
			// Convert legacy nibble format to RGB format
			for (int i = 0; i < TOTAL_BLOCKS; i++) {
				int nibbleIndex = i / 2;
				boolean isLower = (i & 1) == 0;
				int legacyLight;
				if (isLower) {
					legacyLight = blockLight[nibbleIndex] & 0x0F;
				} else {
					legacyLight = (blockLight[nibbleIndex] >> 4) & 0x0F;
				}
				// Convert 0-15 to 0-31 by doubling (approximately)
				int rgbLight = legacyLight * 2;
				// Set white RGB
				setBlockLightRGBAtIndex(i, rgbLight, rgbLight, rgbLight);
			}
		} else if (blockLight.length == BLOCKLIGHT_ARRAY_SIZE) {
			// RGB format: use directly
			this.blockLight = blockLight.clone();
		} else {
			throw new IllegalArgumentException("BlockLight array must be " + SKYLIGHT_ARRAY_SIZE + 
				" bytes (legacy) or " + BLOCKLIGHT_ARRAY_SIZE + " bytes (RGB), got: " + blockLight.length);
		}
	}
	
	/**
	 * Helper method to set RGB at a specific block index (0-4095).
	 */
	private void setBlockLightRGBAtIndex(int blockIndex, int r, int g, int b) {
		// Pack RGB into 2 bytes: RRRRRGGG GGBBBBB
		int packed = ((r & 0x1F) << 10) | ((g & 0x1F) << 5) | (b & 0x1F);
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
	 * Get block light RED level at position (0-31).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light red level (0-31)
	 */
	public int getBlockLightR(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return (packed >> 10) & 0x1F; // Extract 5 bits for R
	}
	
	/**
	 * Get block light GREEN level at position (0-31).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light green level (0-31)
	 */
	public int getBlockLightG(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return (packed >> 5) & 0x1F; // Extract 5 bits for G
	}
	
	/**
	 * Get block light BLUE level at position (0-31).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light blue level (0-31)
	 */
	public int getBlockLightB(int x, int y, int z) {
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
		return packed & 0x1F; // Extract 5 bits for B
	}
	
	/**
	 * Get block light level at position (legacy method, returns max of RGB scaled to 0-15).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @return Block light level (0-15), maximum of RGB channels scaled down
	 * @deprecated Use getBlockLightR/G/B for RGB values
	 */
	@Deprecated
	public int getBlockLight(int x, int y, int z) {
		int r = getBlockLightR(x, y, z);
		int g = getBlockLightG(x, y, z);
		int b = getBlockLightB(x, y, z);
		int max = Math.max(r, Math.max(g, b));
		// Scale from 0-31 to 0-15
		return max / 2;
	}
	
	/**
	 * Set block light RGB levels at position.
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @param r Red level (0-31)
	 * @param g Green level (0-31)
	 * @param b Blue level (0-31)
	 */
	public void setBlockLightRGB(int x, int y, int z, int r, int g, int b) {
		if (r < 0 || r > 31 || g < 0 || g > 31 || b < 0 || b > 31) {
			throw new IllegalArgumentException("RGB light levels must be 0-31, got: " + r + ", " + g + ", " + b);
		}
		
		int blockIndex = getBlockIndex(x, y, z);
		int byteIndex = blockIndex * 2;
		
		// Pack RGB into 2 bytes: RRRRRGGG GGBBBBB
		int packed = ((r & 0x1F) << 10) | ((g & 0x1F) << 5) | (b & 0x1F);
		blockLight[byteIndex] = (byte) (packed >> 8);
		blockLight[byteIndex + 1] = (byte) (packed & 0xFF);
	}
	
	/**
	 * Set block light level at position (legacy method, sets all RGB to same value).
	 * Scales 0-15 input to 0-30 output (doubling).
	 * @param x 0-15
	 * @param y 0-15
	 * @param z 0-15
	 * @param level Light level (0-15)
	 * @deprecated Use setBlockLightRGB for RGB values
	 */
	@Deprecated
	public void setBlockLight(int x, int y, int z, int level) {
		if (level < 0 || level > 15) {
			throw new IllegalArgumentException("Light level must be 0-15, got: " + level);
		}
		// Scale from 0-15 to 0-30 (approximately 0-31)
		int rgb = level * 2;
		setBlockLightRGB(x, y, z, rgb, rgb, rgb);
	}
	
	/**
	 * Get the raw sky light array for serialization.
	 * @return Clone of sky light array (2048 bytes)
	 */
	public byte[] getSkyLightArray() {
		return skyLight.clone();
	}
	
	/**
	 * Get the raw block light RGB array for serialization.
	 * @return Clone of block light RGB array (8192 bytes)
	 */
	public byte[] getBlockLightArray() {
		return blockLight.clone();
	}
}

