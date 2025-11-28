package mattmc.world.level.chunk;

/**
 * Stores heightmap data for a 16×16 chunk column.
 * Tracks the topmost non-air block Y coordinate for each column position.
 */
public class ColumnHeightmap {
	private static final int COLUMN_SIZE = 16;
	
	// Heightmap data: stores the Y coordinate of the topmost non-air block
	// for each (x, z) position in the chunk
	private final int[][] heights;
	
	/**
	 * Create a new heightmap with default values.
	 * Default height is the minimum Y coordinate.
	 */
	public ColumnHeightmap() {
		this.heights = new int[COLUMN_SIZE][COLUMN_SIZE];
		// Initialize to minimum Y
		for (int x = 0; x < COLUMN_SIZE; x++) {
			for (int z = 0; z < COLUMN_SIZE; z++) {
				heights[x][z] = LevelChunk.MIN_Y;
			}
		}
	}
	
	/**
	 * Create a heightmap from existing data.
	 * @param data 2D array of height values (16×16)
	 * @throws IllegalArgumentException if data is not exactly 16×16
	 */
	public ColumnHeightmap(int[][] data) {
		if (data == null || data.length != COLUMN_SIZE) {
			throw new IllegalArgumentException("Heightmap data must be 16×16");
		}
		// Validate all inner arrays have correct length
		for (int x = 0; x < COLUMN_SIZE; x++) {
			if (data[x] == null || data[x].length != COLUMN_SIZE) {
				throw new IllegalArgumentException("Heightmap data must be 16×16, row " + x + " has incorrect length");
			}
		}
		this.heights = new int[COLUMN_SIZE][COLUMN_SIZE];
		for (int x = 0; x < COLUMN_SIZE; x++) {
			System.arraycopy(data[x], 0, this.heights[x], 0, COLUMN_SIZE);
		}
	}
	
	/**
	 * Get the height at the given column position.
	 * @param x Column X coordinate (0-15)
	 * @param z Column Z coordinate (0-15)
	 * @return The Y coordinate of the topmost non-air block
	 */
	public int getHeight(int x, int z) {
		if (x < 0 || x >= COLUMN_SIZE || z < 0 || z >= COLUMN_SIZE) {
			throw new IllegalArgumentException("Column coordinates out of bounds: (" + x + ", " + z + ")");
		}
		return heights[x][z];
	}
	
	/**
	 * Set the height at the given column position.
	 * @param x Column X coordinate (0-15)
	 * @param z Column Z coordinate (0-15)
	 * @param height The Y coordinate of the topmost non-air block
	 */
	public void setHeight(int x, int z, int height) {
		if (x < 0 || x >= COLUMN_SIZE || z < 0 || z >= COLUMN_SIZE) {
			throw new IllegalArgumentException("Column coordinates out of bounds: (" + x + ", " + z + ")");
		}
		heights[x][z] = height;
	}
	
	/**
	 * Get the raw heightmap data.
	 * @return 2D array of height values (16×16)
	 */
	public int[][] getData() {
		int[][] copy = new int[COLUMN_SIZE][COLUMN_SIZE];
		for (int x = 0; x < COLUMN_SIZE; x++) {
			System.arraycopy(heights[x], 0, copy[x], 0, COLUMN_SIZE);
		}
		return copy;
	}
}
