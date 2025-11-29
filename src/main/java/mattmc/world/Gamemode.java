package mattmc.world;

/**
 * Represents the game modes available in MattMC.
 * This enum allows for future extension with additional game modes like Spectator.
 */
public enum Gamemode {
    SURVIVAL(0, "Survival", true),
    CREATIVE(1, "Creative", true),
    SPECTATOR(2, "Spectator", false);
    
    private final int id;
    private final String displayName;
    private final boolean selectableAsDefault;
    
    Gamemode(int id, String displayName, boolean selectableAsDefault) {
        this.id = id;
        this.displayName = displayName;
        this.selectableAsDefault = selectableAsDefault;
    }
    
    /**
     * Get the numeric ID for this gamemode.
     * Used for NBT serialization.
     */
    public int getId() {
        return id;
    }
    
    /**
     * Get the display name for this gamemode.
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if this gamemode can be selected as the default gamemode in the create world screen.
     * Spectator mode, for example, should not be selectable as a default.
     */
    public boolean isSelectableAsDefault() {
        return selectableAsDefault;
    }
    
    /**
     * Get a Gamemode by its numeric ID.
     * Returns CREATIVE if the ID is not recognized (legacy support).
     */
    public static Gamemode fromId(int id) {
        for (Gamemode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return CREATIVE; // Default to CREATIVE for legacy support
    }
    
    /**
     * Get a Gamemode by its name (case-insensitive).
     * Returns null if the name is not recognized.
     */
    public static Gamemode fromName(String name) {
        if (name == null) {
            return null;
        }
        for (Gamemode mode : values()) {
            if (mode.name().equalsIgnoreCase(name) || mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }
}
