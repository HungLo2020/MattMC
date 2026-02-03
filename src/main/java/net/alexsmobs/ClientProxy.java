package net.alexsmobs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Minimal ClientProxy for AlexsMobs entity rendering
 */
public class ClientProxy {
    /**
     * List of entity UUIDs that should not be rendered
     * Used for baby entities riding parents
     */
    public static final List<UUID> currentUnrenderedEntities = new ArrayList<>();
}
