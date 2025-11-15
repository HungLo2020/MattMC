package mattmc.client.resources;

import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.BlockState;
import mattmc.client.resources.model.BlockStateVariant;
import mattmc.client.resources.model.ModelElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the model loading and resolution system.
 */
class ModelLoadingTest {
    
    @Test
    void testLoadCobblestoneBlockState() {
        BlockState blockState = ResourceManager.loadBlockState("cobblestone");
        assertNotNull(blockState, "Cobblestone blockstate should load");
        
        List<BlockStateVariant> variants = blockState.getDefaultVariants();
        assertNotNull(variants, "Should have default variants");
        assertFalse(variants.isEmpty(), "Should have at least one variant");
        
        BlockStateVariant variant = variants.get(0);
        assertEquals("mattmc:block/cobblestone", variant.getModel(), "Should reference correct model");
    }
    
    @Test
    void testLoadCobblestoneModel() {
        BlockModel model = ResourceManager.loadBlockModel("cobblestone");
        assertNotNull(model, "Cobblestone model should load");
        
        // Check that parent was resolved
        assertNull(model.getParent(), "Parent should be resolved and removed");
        
        // Check textures were inherited and resolved
        assertNotNull(model.getTextures(), "Should have textures");
        assertTrue(model.getTextures().containsKey("all"), "Should have 'all' texture");
        
        // Should have elements from cube_all parent
        assertNotNull(model.getElements(), "Should have elements from parent");
        assertFalse(model.getElements().isEmpty(), "Should have at least one element");
    }
    
    @Test
    void testLoadStairsModel() {
        BlockModel model = ResourceManager.loadBlockModel("birch_stairs");
        assertNotNull(model, "Birch stairs model should load");
        
        // Check textures were resolved
        assertNotNull(model.getTextures(), "Should have textures");
        assertTrue(model.getTextures().containsKey("top") || 
                   model.getTextures().containsKey("bottom") || 
                   model.getTextures().containsKey("side"), "Should have stair textures");
        
        // Should have elements from stairs parent
        assertNotNull(model.getElements(), "Should have elements from parent");
        assertEquals(2, model.getElements().size(), "Stairs should have 2 elements");
    }
    
    @Test
    void testTextureVariableResolution() {
        BlockModel model = ResourceManager.loadBlockModel("cobblestone");
        assertNotNull(model, "Model should load");
        
        // Texture variables like #all should be resolved in elements
        if (model.getElements() != null) {
            for (ModelElement element : model.getElements()) {
                if (element.getFaces() != null) {
                    for (ModelElement.ElementFace face : element.getFaces().values()) {
                        String texture = face.getTexture();
                        if (texture != null) {
                            assertFalse(texture.startsWith("#"), 
                                "Texture variables should be resolved: " + texture);
                        }
                    }
                }
            }
        }
    }
    
    @Test
    void testGetBlockTexturePaths() {
        Map<String, String> paths = ResourceManager.getBlockTexturePaths("cobblestone");
        assertNotNull(paths, "Should get texture paths");
        assertFalse(paths.isEmpty(), "Should have at least one texture");
        
        // Check that namespace prefix is stripped
        for (String path : paths.values()) {
            assertFalse(path.contains(":"), "Texture paths should not contain namespace: " + path);
            assertTrue(path.startsWith("assets/textures/"), "Path should start with assets/textures/");
            assertTrue(path.endsWith(".png"), "Path should end with .png");
        }
    }
    
    @Test
    void testGetItemTexturePaths() {
        Map<String, String> paths = ResourceManager.getItemTexturePaths("cobblestone");
        assertNotNull(paths, "Should get item texture paths");
        assertFalse(paths.isEmpty(), "Should have at least one texture");
    }
    
    @Test
    void testBirchStairsBlockState() {
        BlockState blockState = ResourceManager.loadBlockState("birch_stairs");
        assertNotNull(blockState, "Birch stairs blockstate should load");
        
        Map<String, List<BlockStateVariant>> variants = blockState.getParsedVariants();
        assertNotNull(variants, "Should have parsed variants");
        assertFalse(variants.isEmpty(), "Should have variants");
        
        // Should have multiple variants for different states
        assertTrue(variants.size() > 1, "Stairs should have multiple variants");
        
        // Check one specific variant
        List<BlockStateVariant> northBottomStraight = blockState.getVariantsForState("facing=north,half=bottom,shape=straight");
        assertNotNull(northBottomStraight, "Should have variant for facing=north,half=bottom,shape=straight");
        assertFalse(northBottomStraight.isEmpty(), "Variant list should not be empty");
        
        BlockStateVariant variant = northBottomStraight.get(0);
        assertEquals("mattmc:block/birch_stairs", variant.getModel(), "Should reference correct model");
        assertEquals(270, variant.getY(), "Should have Y rotation");
        assertTrue(variant.getUvlock(), "Should have uvlock");
    }
    
    @Test
    void testInnerStairsModel() {
        BlockModel model = ResourceManager.loadBlockModel("birch_stairs_inner");
        assertNotNull(model, "Inner stairs model should load");
        
        // Should inherit from inner_stairs parent
        assertNotNull(model.getElements(), "Should have elements");
        assertEquals(3, model.getElements().size(), "Inner stairs should have 3 elements");
    }
    
    @Test
    void testOuterStairsModel() {
        BlockModel model = ResourceManager.loadBlockModel("birch_stairs_outer");
        assertNotNull(model, "Outer stairs model should load");
        
        // Should inherit from outer_stairs parent
        assertNotNull(model.getElements(), "Should have elements");
        assertEquals(2, model.getElements().size(), "Outer stairs should have 2 elements");
    }
}
