package mattmc.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mattmc.util.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed particle definition from a JSON file.
 * 
 * <p>Particle definitions are located at assets/&lt;namespace&gt;/particles/&lt;name&gt;.json
 * and specify which textures a particle type uses.
 * 
 * <p>Example JSON:
 * <pre>
 * {
 *   "textures": [
 *     "mattmc:generic_0",
 *     "mattmc:generic_1",
 *     "mattmc:generic_2"
 *   ]
 * }
 * </pre>
 * 
 * <p>Mirrors Minecraft's ParticleDescription.
 */
public class ParticleDescription {
    private static final Logger logger = LoggerFactory.getLogger(ParticleDescription.class);
    
    private final List<ResourceLocation> textures;
    
    private ParticleDescription(List<ResourceLocation> textures) {
        this.textures = textures;
    }
    
    /**
     * Get the list of texture resource locations for this particle.
     */
    public List<ResourceLocation> getTextures() {
        return textures;
    }
    
    /**
     * Parse a particle description from JSON.
     * 
     * @param json the JSON object
     * @return the parsed description
     */
    public static ParticleDescription fromJson(JsonObject json) {
        List<ResourceLocation> textures = new ArrayList<>();
        
        if (json.has("textures")) {
            JsonArray texturesArray = json.getAsJsonArray("textures");
            for (int i = 0; i < texturesArray.size(); i++) {
                String texturePath = texturesArray.get(i).getAsString();
                ResourceLocation location = ResourceLocation.tryParse(texturePath);
                if (location != null) {
                    textures.add(location);
                } else {
                    logger.warn("Invalid texture path in particle definition: {}", texturePath);
                }
            }
        }
        
        return new ParticleDescription(textures);
    }
    
    /**
     * Create an empty particle description.
     */
    public static ParticleDescription empty() {
        return new ParticleDescription(List.of());
    }
}
