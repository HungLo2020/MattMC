package net.minecraft.client.renderer.shader.pack;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Metadata for a shader pack discovered in the game resources.
 * In MattMC, shader packs are baked into the JAR and discovered dynamically at runtime.
 */
@Environment(EnvType.CLIENT)
public record ShaderPackMetadata(
    String name,
    String description,
    String author,
    String version,
    String resourcePath,  // Path within resources, e.g., "complementary_reimagined"
    boolean isResource    // Always true for MattMC (baked into JAR)
) {
    /**
     * Creates a default metadata entry for a shader pack found in resources.
     */
    public static ShaderPackMetadata createDefault(String packName) {
        return new ShaderPackMetadata(
            packName,
            "Shader Pack: " + formatName(packName),
            "Unknown",
            "1.0",
            packName,
            true
        );
    }
    
    /**
     * Formats a shader pack directory name into a readable display name.
     */
    private static String formatName(String packName) {
        // Convert snake_case or kebab-case to Title Case
        String spaced = packName.replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : spaced.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    /**
     * Gets the full resource path for shader files in this pack.
     */
    public String getShaderPath() {
        return "shaders/" + resourcePath;
    }
}
