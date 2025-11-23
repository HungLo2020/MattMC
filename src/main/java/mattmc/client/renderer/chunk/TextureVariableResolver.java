package mattmc.client.renderer.chunk;

import mattmc.client.resources.model.BlockModel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves texture variable references in block models.
 * Converts texture references like "#torch" to actual texture paths like "block/torch"
 * by recursively looking up variables in the model's texture map.
 * 
 * This is separated from ModelElementRenderer to keep texture resolution logic isolated
 * and testable, following Minecraft's separation of concerns pattern.
 */
public class TextureVariableResolver {
    
    /**
     * Resolve texture variable reference to actual texture path.
     * Recursively resolves if the resolved value is also a variable.
     * 
     * @param textureRef The texture reference (e.g., "#torch" or "block/torch")
     * @param model The model containing the texture variable definitions
     * @return The resolved texture path, or the original reference if resolution fails
     */
    public static String resolveTexture(String textureRef, BlockModel model) {
        return resolveTexture(textureRef, model, new HashSet<>());
    }
    
    /**
     * Resolve a texture reference with circular reference protection.
     * 
     * @param textureRef The texture reference to resolve
     * @param model The model containing texture definitions
     * @param visited Set of already visited variable names to prevent infinite loops
     * @return The resolved texture path
     */
    private static String resolveTexture(String textureRef, BlockModel model, Set<String> visited) {
        if (!textureRef.startsWith("#")) {
            return textureRef; // Already a direct reference
        }
        
        // Resolve variable (e.g., "#torch" -> actual path)
        String varName = textureRef.substring(1);
        
        Map<String, String> textures = model.getTextures();
        if (textures == null || !textures.containsKey(varName)) {
            // Variable doesn't exist in this model - return textureRef as-is
            return textureRef;
        }
        
        String resolved = textures.get(varName);
        if (resolved == null) {
            // Resolved to null - return textureRef as-is
            return textureRef;
        }
        
        // Check for circular reference
        if (visited.contains(varName)) {
            // Circular reference - return textureRef as-is
            return textureRef;
        }
        
        // Add to visited set before recursing
        visited.add(varName);
        
        // Recursively resolve if the resolved value is also a variable
        if (resolved.startsWith("#")) {
            return resolveTexture(resolved, model, visited);
        }
        
        return resolved;
    }
}
