package mattmc.world.level.chunk;

/**
 * Stores per-voxel light data for a chunk section.
 * Each voxel stores 1 byte: high nibble = skyLight (0-15), low nibble = blockLight (0-15).
 * 
 * For a 16×16×16 section, this requires 4096 bytes.
 */
public class LightStorage {
	private static final int SECTION_SIZE = 16;
	private static final int TOTAL_BLOCKS = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
	
	// Light data: 1 byte per block (high nibble = sky, low nibble = block)
	private final byte[] lightNibbles;
	
	/**
	 * Create a new light storage with default values.
	 * Sky light defaults to 15 (full brightness), block light to 0.
	 */
	public LightStorage() {
		this.lightNibbles = new byte[TOTAL_BLOCKS];
		// Initialize sky light to maximum (15)
		for (int i = 0; i < TOTAL_BLOCKS; i++) {
			lightNibbles[i] = (byte) 0xF0; // Sky = 15, Block = 0
		}
	}
	
	/**
	 * Create a light storage from existing data.
	 * @param data The light data array (must be 4096 bytes)
	 */
	public LightStorage(byte[] data) {
		if (data.length != TOTAL_BLOCKS) {
			throw new IllegalArgumentException("Light data must be exactly " + TOTAL_BLOCKS + " bytes");
		}
		this.lightNibbles = data.clone();
	}
	
	/**
	 * Get the array index for the given coordinates.
	 */
	private int getIndex(int x, int y, int z) {
		if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
			throw new IllegalArgumentException("Coordinates out of bounds: (" + x + ", " + y + ", " + z + ")");
		}
		return x + z * SECTION_SIZE + y * SECTION_SIZE * SECTION_SIZE;
	}
	
	/**
	 * Get sky light level at the given position (0-15).
	 */
	public int getSky(int x, int y, int z) {
		int index = getIndex(x, y, z);
		return (lightNibbles[index] >> 4) & 0xF;
	}
	
	/**
	 * Get block light level at the given position (0-15).
	 */
	public int getBlock(int x, int y, int z) {
		int index = getIndex(x, y, z);
		return lightNibbles[index] & 0xF;
	}
	
	/**
	 * Set sky light level at the given position (0-15).
	 */
	public void setSky(int x, int y, int z, int level) {
		if (level < 0 || level > 15) {
			throw new IllegalArgumentException("Sky light must be 0-15, got: " + level);
		}
		int index = getIndex(x, y, z);
		// Clear high nibble and set new value
		lightNibbles[index] = (byte) ((lightNibbles[index] & 0x0F) | (level << 4));
	}
	
	/**
	 * Set block light level at the given position (0-15).
	 */
	public void setBlock(int x, int y, int z, int level) {
		if (level < 0 || level > 15) {
			throw new IllegalArgumentException("Block light must be 0-15, got: " + level);
		}
		int index = getIndex(x, y, z);
		// Clear low nibble and set new value
		lightNibbles[index] = (byte) ((lightNibbles[index] & 0xF0) | level);
	}
	
	/**
	 * Get the raw light data array.
	 */
	public byte[] getData() {
		return lightNibbles.clone();
	}
}
