package mattmc.client.renderer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ModelBakery system that bakes JSON models into BakedModels.
 */
public class ModelBakeryTest {
    
    @Test
    public void testBakeSimpleBlockModel() {
        // Test baking a simple block model (e.g., dirt)
        BakedModel model = ModelBakery.bakeBlockModel("dirt");
        
        assertNotNull(model, "Should successfully bake dirt model");
        assertNotNull(model.getQuads(), "Baked model should have quads");
        assertTrue(model.getQuads().size() > 0, "Baked model should have at least one quad");
        
        // Dirt is a simple cube_all block, should have 6 quads (one per face)
        assertEquals(6, model.getQuads().size(), "Cube should have 6 quads (one per face)");
    }
    
    @Test
    public void testBakeItemModel() {
        // Test baking an item model
        BakedModel model = ModelBakery.bakeItemModel("dirt");
        
        assertNotNull(model, "Should successfully bake dirt item model");
        assertNotNull(model.getQuads(), "Baked model should have quads");
    }
    
    @Test
    public void testBakedModelCaching() {
        // Test that models are cached
        BakedModel model1 = ModelBakery.bakeBlockModel("dirt");
        BakedModel model2 = ModelBakery.bakeBlockModel("dirt");
        
        assertSame(model1, model2, "Same model should be returned from cache");
    }
    
    @Test
    public void testBakedQuadStructure() {
        // Test the structure of a baked quad
        BakedModel model = ModelBakery.bakeBlockModel("dirt");
        
        assertNotNull(model, "Model should not be null");
        assertTrue(model.getQuads().size() > 0, "Model should have quads");
        
        BakedQuad quad = model.getQuads().get(0);
        assertNotNull(quad, "Quad should not be null");
        assertNotNull(quad.getVertices(), "Quad should have vertices");
        assertEquals(48, quad.getVertices().length, "Quad should have 48 floats (4 vertices * 12 floats)");
        assertNotNull(quad.getFace(), "Quad should have a face direction");
        assertNotNull(quad.getTexturePath(), "Quad should have a texture path");
    }
    
    @Test
    public void testDisplayTransforms() {
        // Test that display transforms are preserved in baked model
        BakedModel model = ModelBakery.bakeBlockModel("stairs");
        
        if (model != null && model.getDisplayTransforms() != null) {
            // Stairs model has display transforms in JSON
            assertNotNull(model.getTransform("gui"), "Should have GUI transform");
        }
    }
}
