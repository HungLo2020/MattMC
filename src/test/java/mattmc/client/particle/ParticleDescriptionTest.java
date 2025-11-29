package mattmc.client.particle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mattmc.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ParticleDescription JSON parsing.
 */
class ParticleDescriptionTest {
    
    @Test
    void testParseEmptyTextures() {
        String json = "{}";
        JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
        ParticleDescription desc = ParticleDescription.fromJson(jsonObj);
        
        assertTrue(desc.getTextures().isEmpty());
    }
    
    @Test
    void testParseSingleTexture() {
        String json = """
            {
                "textures": ["mattmc:flame"]
            }
            """;
        JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
        ParticleDescription desc = ParticleDescription.fromJson(jsonObj);
        
        assertEquals(1, desc.getTextures().size());
        assertEquals(new ResourceLocation("mattmc:flame"), desc.getTextures().get(0));
    }
    
    @Test
    void testParseMultipleTextures() {
        String json = """
            {
                "textures": [
                    "mattmc:generic_0",
                    "mattmc:generic_1",
                    "mattmc:generic_2"
                ]
            }
            """;
        JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
        ParticleDescription desc = ParticleDescription.fromJson(jsonObj);
        
        assertEquals(3, desc.getTextures().size());
        assertEquals("generic_0", desc.getTextures().get(0).getPath());
        assertEquals("generic_1", desc.getTextures().get(1).getPath());
        assertEquals("generic_2", desc.getTextures().get(2).getPath());
    }
    
    @Test
    void testEmptyFactory() {
        ParticleDescription desc = ParticleDescription.empty();
        assertNotNull(desc);
        assertTrue(desc.getTextures().isEmpty());
    }
}
