package mattmc.world.level.block.state.properties;

/**
 * Central registry of commonly used block state properties.
 * Similar to Minecraft's BlockStateProperties class.
 * 
 * This class provides pre-defined property instances that can be shared
 * across multiple block types, ensuring consistency and reducing memory usage.
 */
public final class BlockStateProperties {
    
    // Private constructor to prevent instantiation
    private BlockStateProperties() {}
    
    // ==================== Direction Properties ====================
    
    /**
     * Horizontal facing direction (NORTH, SOUTH, EAST, WEST).
     * Used by: stairs, trapdoors, doors, buttons, etc.
     */
    public static final EnumProperty<Direction> HORIZONTAL_FACING = 
        EnumProperty.create("facing", Direction.class);
    
    // ==================== Stairs Properties ====================
    
    /**
     * Whether stairs are in the top or bottom half.
     * Used by: stairs, slabs, trapdoors
     * Default: BOTTOM (right-side up)
     */
    public static final EnumProperty<Half> HALF = 
        new EnumProperty<>("half", Half.class, java.util.Arrays.asList(Half.values()), Half.BOTTOM);
    
    /**
     * The shape of stairs (straight, corner shapes).
     * Used by: stairs
     */
    public static final EnumProperty<StairsShape> STAIRS_SHAPE = 
        EnumProperty.create("shape", StairsShape.class);
    
    // ==================== Axis Properties ====================
    
    /**
     * The axis a block is oriented along (X, Y, Z).
     * Used by: logs, pillars, froglights
     */
    public static final EnumProperty<Axis> AXIS = 
        EnumProperty.create("axis", Axis.class);
    
    // ==================== Boolean Properties ====================
    
    /**
     * Whether a block contains water.
     * Used by: slabs, stairs, fences, walls, etc.
     */
    public static final BooleanProperty WATERLOGGED = 
        BooleanProperty.create("waterlogged");
    
    /**
     * Whether a block is lit (emitting light).
     * Used by: furnace, redstone torch, campfire
     */
    public static final BooleanProperty LIT = 
        BooleanProperty.create("lit");
    
    /**
     * Whether a block is receiving a redstone signal.
     * Used by: levers, buttons, pressure plates, repeaters
     */
    public static final BooleanProperty POWERED = 
        BooleanProperty.create("powered");
    
    /**
     * Whether a door/trapdoor/fence gate is open.
     * Used by: doors, trapdoors, fence gates
     */
    public static final BooleanProperty OPEN = 
        BooleanProperty.create("open");
    
    /**
     * Whether a block is attached to another (e.g., tripwire).
     * Used by: tripwire, bell
     */
    public static final BooleanProperty ATTACHED = 
        BooleanProperty.create("attached");
    
    // ==================== Integer Properties ====================
    
    /**
     * Redstone signal strength (0-15).
     * Used by: daylight detector, weighted pressure plate
     */
    public static final IntegerProperty POWER = 
        IntegerProperty.create("power", 0, 15);
    
    /**
     * Growth stage for crops (0-7).
     * Used by: wheat, carrots, potatoes, beetroots
     */
    public static final IntegerProperty AGE_7 = 
        IntegerProperty.create("age", 0, 7);
    
    /**
     * Growth stage for some crops (0-3).
     * Used by: nether wart, beetroot
     */
    public static final IntegerProperty AGE_3 = 
        IntegerProperty.create("age", 0, 3);
    
    /**
     * Growth stage for bamboo, kelp (0-25).
     * Used by: bamboo, kelp
     */
    public static final IntegerProperty AGE_25 = 
        IntegerProperty.create("age", 0, 25);
    
    /**
     * Moisture level for farmland (0-7).
     * Used by: farmland
     */
    public static final IntegerProperty MOISTURE = 
        IntegerProperty.create("moisture", 0, 7);
    
    /**
     * Fluid level for cauldrons (0-3).
     * Used by: cauldron
     */
    public static final IntegerProperty LEVEL_CAULDRON = 
        IntegerProperty.create("level", 0, 3);
    
    /**
     * Layer count for snow (1-8).
     * Used by: snow
     */
    public static final IntegerProperty LAYERS = 
        IntegerProperty.create("layers", 1, 8);
    
    /**
     * Note for note blocks (0-24).
     * Used by: note block
     */
    public static final IntegerProperty NOTE = 
        IntegerProperty.create("note", 0, 24);
    
    /**
     * Distance from log for leaves (1-7).
     * Used by: leaves
     */
    public static final IntegerProperty DISTANCE = 
        IntegerProperty.create("distance", 1, 7);
    
    /**
     * Rotation for signs, banners, skulls (0-15).
     * Used by: standing signs, banners, skulls
     */
    public static final IntegerProperty ROTATION_16 = 
        IntegerProperty.create("rotation", 0, 15);
}
