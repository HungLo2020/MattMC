package mattmc.util;

import java.util.Objects;

/**
 * Represents a namespaced identifier for game resources.
 * 
 * <p>Format: {@code namespace:path} (e.g., "mattmc:smoke" or "mattmc:textures/particle/flame.png")
 * 
 * <p>This class mirrors Minecraft's ResourceLocation for compatibility when porting
 * particle and resource definitions.
 */
public class ResourceLocation implements Comparable<ResourceLocation> {
    public static final String DEFAULT_NAMESPACE = "mattmc";
    private static final char NAMESPACE_SEPARATOR = ':';
    
    private final String namespace;
    private final String path;
    
    /**
     * Create a ResourceLocation from a namespace and path.
     * 
     * @param namespace the namespace (e.g., "mattmc")
     * @param path the path (e.g., "smoke" or "textures/particle/flame.png")
     */
    public ResourceLocation(String namespace, String path) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        validateNamespace(namespace);
        validatePath(path);
    }
    
    /**
     * Create a ResourceLocation from a string.
     * 
     * <p>If no namespace is provided, defaults to "mattmc".
     * 
     * @param location the location string (e.g., "mattmc:smoke" or "smoke")
     */
    public ResourceLocation(String location) {
        Objects.requireNonNull(location, "location cannot be null");
        
        int separatorIndex = location.indexOf(NAMESPACE_SEPARATOR);
        if (separatorIndex >= 0) {
            this.namespace = location.substring(0, separatorIndex);
            this.path = location.substring(separatorIndex + 1);
        } else {
            this.namespace = DEFAULT_NAMESPACE;
            this.path = location;
        }
        
        validateNamespace(namespace);
        validatePath(path);
    }
    
    /**
     * Parse a ResourceLocation from a string, returning null if invalid.
     */
    public static ResourceLocation tryParse(String location) {
        try {
            return new ResourceLocation(location);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Create a ResourceLocation with the default namespace.
     */
    public static ResourceLocation withDefaultNamespace(String path) {
        return new ResourceLocation(DEFAULT_NAMESPACE, path);
    }
    
    private void validateNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!isValidNamespaceChar(c)) {
                throw new IllegalArgumentException("Invalid character '" + c + "' in namespace: " + namespace);
            }
        }
    }
    
    private void validatePath(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!isValidPathChar(c)) {
                throw new IllegalArgumentException("Invalid character '" + c + "' in path: " + path);
            }
        }
    }
    
    private boolean isValidNamespaceChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
    }
    
    private boolean isValidPathChar(char c) {
        return isValidNamespaceChar(c) || c == '/';
    }
    
    /**
     * Get the namespace (e.g., "mattmc").
     */
    public String getNamespace() {
        return namespace;
    }
    
    /**
     * Get the path (e.g., "smoke" or "textures/particle/flame.png").
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Convert to a file path for loading resources.
     * 
     * <p>The namespace is not included in the file path since resources are organized
     * directly under assets/ without namespace subdirectories.
     * 
     * @param prefix prefix to prepend (e.g., "/assets/")
     * @param suffix suffix to append (e.g., ".json" or ".png")
     * @return the full file path
     */
    public String toFilePath(String prefix, String suffix) {
        return prefix + path + suffix;
    }
    
    @Override
    public String toString() {
        return namespace + NAMESPACE_SEPARATOR + path;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ResourceLocation other)) return false;
        return namespace.equals(other.namespace) && path.equals(other.path);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }
    
    @Override
    public int compareTo(ResourceLocation other) {
        int result = this.path.compareTo(other.path);
        if (result == 0) {
            result = this.namespace.compareTo(other.namespace);
        }
        return result;
    }
}
