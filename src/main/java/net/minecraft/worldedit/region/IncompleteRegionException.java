package net.minecraft.worldedit.region;

/**
 * Thrown when a region selection is incomplete.
 */
public class IncompleteRegionException extends Exception {
    public IncompleteRegionException() {
        super("Selection is incomplete");
    }
    
    public IncompleteRegionException(String message) {
        super(message);
    }
}
