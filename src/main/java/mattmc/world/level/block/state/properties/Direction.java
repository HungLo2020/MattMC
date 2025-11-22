package mattmc.world.level.block.state.properties;

/**
 * Enum for all six directions (North, South, East, West, Up, Down).
 * Similar to Minecraft's Direction enum.
 */
public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);
    
    private final int stepX;
    private final int stepY;
    private final int stepZ;
    
    Direction(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }
    
    public int getStepX() {
        return stepX;
    }
    
    public int getStepY() {
        return stepY;
    }
    
    public int getStepZ() {
        return stepZ;
    }
    
    // Backwards compatibility aliases
    public int getOffsetX() {
        return stepX;
    }
    
    public int getOffsetY() {
        return stepY;
    }
    
    public int getOffsetZ() {
        return stepZ;
    }
    
    /**
     * Get the nearest direction to the given vector.
     * Used by BlockMath.rotate to find the direction after transformation.
     */
    public static Direction getNearest(float x, float y, float z) {
        Direction best = Direction.NORTH;
        float bestDot = Float.NEGATIVE_INFINITY;
        
        for (Direction dir : values()) {
            float dot = x * dir.stepX + y * dir.stepY + z * dir.stepZ;
            if (dot > bestDot) {
                bestDot = dot;
                best = dir;
            }
        }
        
        return best;
    }
    
    /**
     * Get the opposite direction.
     */
    public Direction getOpposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }
    
    /**
     * Rotate this direction clockwise (when viewed from above).
     */
    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this; // UP and DOWN don't rotate
        };
    }
    
    /**
     * Rotate this direction counter-clockwise (when viewed from above).
     */
    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> this; // UP and DOWN don't rotate
        };
    }
}
