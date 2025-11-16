package mattmc.world.level.block.state.properties;

/**
 * Enum for block axes (X, Y, Z).
 * Used by blocks that can be rotated along different axes like logs, pillars, and froglights.
 * Similar to Minecraft's Direction.Axis enum.
 */
public enum Axis {
    X("x"),
    Y("y"),
    Z("z");
    
    private final String name;
    
    Axis(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
