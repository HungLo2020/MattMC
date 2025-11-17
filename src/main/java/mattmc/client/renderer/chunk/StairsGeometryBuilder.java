package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureAtlas;

/**
 * Handles geometry generation for stairs blocks.
 * Stairs consist of a bottom slab and a top step, rotated based on facing direction.
 * 
 * Extracted from MeshBuilder as part of refactoring to single-purpose classes.
 */
public class StairsGeometryBuilder {
    
    private final VertexLightSampler lightSampler;
    private final UVMapper uvMapper;
    
    /**
     * Create a stairs geometry builder.
     */
    public StairsGeometryBuilder(VertexLightSampler lightSampler, UVMapper uvMapper) {
        this.lightSampler = lightSampler;
        this.uvMapper = uvMapper;
    }
    
    /**
     * Add stairs geometry to the provided vertex and index lists.
     * 
     * @param face The face data containing block, position, and state
     * @param vertices The vertex list to add to
     * @param indices The index list to add to
     * @param currentVertex The current vertex index (will be updated)
     * @return The new current vertex index after adding stairs geometry
     */
    public int addStairsGeometry(BlockFaceCollector.FaceData face, 
                                  FloatList vertices, 
                                  IntList indices, 
                                  int currentVertex) {
        float x = face.x;
        float y = face.y;
        float z = face.z;
        mattmc.world.level.block.Block block = face.block;
        mattmc.world.level.block.state.BlockState state = face.blockState;
        
        // Get texture path and UV mapping for stairs
        String texturePath = block.getTexturePath("side");
        if (texturePath == null) {
            texturePath = block.getTexturePath();
        }
        
        // Create a temporary FaceData for UV mapping
        BlockFaceCollector.FaceData tempFace = new BlockFaceCollector.FaceData(
            face.x, face.y, face.z, face.color, face.brightness, face.colorBrightness,
            block, "side", null, state, face.chunk, face.cx, face.cy, face.cz
        );
        TextureAtlas.UVMapping uvMapping = uvMapper.getUVMapping(tempFace);
        
        // Face colors with appropriate brightness
        FaceColors colors = new FaceColors();
        
        // Get facing and half from blockstate (default to NORTH and BOTTOM if no state)
        mattmc.world.level.block.state.properties.Direction facing = 
            state != null ? state.getDirection("facing") : mattmc.world.level.block.state.properties.Direction.NORTH;
        mattmc.world.level.block.state.properties.Half half = 
            state != null ? state.getHalf("half") : mattmc.world.level.block.state.properties.Half.BOTTOM;
        
        // Render based on half (top or bottom)
        if (half == mattmc.world.level.block.state.properties.Half.BOTTOM) {
            // Bottom stairs: slab on bottom, step on top
            currentVertex = addStairsBottomSlabFace(face, x, y, z, 0.5f, facing, colors, uvMapping, vertices, indices, currentVertex);
            currentVertex = addStairsTopStepFace(face, x, y + 0.5f, z, facing, colors, uvMapping, vertices, indices, currentVertex);
        } else {
            // Top stairs: slab on top, step on bottom  
            currentVertex = addStairsBottomSlabFace(face, x, y + 0.5f, z, 0.5f, facing, colors, uvMapping, vertices, indices, currentVertex);
            currentVertex = addStairsTopStepFace(face, x, y, z, facing, colors, uvMapping, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Add bottom slab faces for stairs.
     * The facing parameter determines which direction the step faces.
     */
    private int addStairsBottomSlabFace(BlockFaceCollector.FaceData face, float x, float y, float z, float height,
                                         mattmc.world.level.block.state.properties.Direction facing,
                                         FaceColors colors,
                                         TextureAtlas.UVMapping uvMapping,
                                         FloatList vertices,
                                         IntList indices,
                                         int currentVertex) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + height;
        float z0 = z, z1 = z + 1;
        
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u1 = uvMapping.u1;
            v1 = uvMapping.v1;
        }
        
        // Calculate mid-point for half-height textures
        float v05 = (v0 + v1) / 2.0f;
        
        // Top face of slab (full texture)
        float[] light0 = lightSampler.sampleVertexLight(face, 0, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 0, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 0, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 0, 3);
        
        int base = currentVertex;
        addVertex(vertices, x0, y1, z0, u0, v0, colors.top, 0, 1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y1, z1, u0, v1, colors.top, 0, 1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z1, u1, v1, colors.top, 0, 1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z0, u1, v0, colors.top, 0, 1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // Bottom face (full texture)
        light0 = lightSampler.sampleVertexLight(face, 1, 0);
        light1 = lightSampler.sampleVertexLight(face, 1, 1);
        light2 = lightSampler.sampleVertexLight(face, 1, 2);
        light3 = lightSampler.sampleVertexLight(face, 1, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z0, u0, v0, colors.bottom, 0, -1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z0, u1, v0, colors.bottom, 0, -1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y0, z1, u1, v1, colors.bottom, 0, -1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y0, z1, u0, v1, colors.bottom, 0, -1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // Side faces - all full width/depth, half height
        // North face
        light0 = lightSampler.sampleVertexLight(face, 2, 0);
        light1 = lightSampler.sampleVertexLight(face, 2, 1);
        light2 = lightSampler.sampleVertexLight(face, 2, 2);
        light3 = lightSampler.sampleVertexLight(face, 2, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z0, u1, v1, colors.north, 0, 0, -1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z0, u0, v1, colors.north, 0, 0, -1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z0, u0, v05, colors.north, 0, 0, -1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z0, u1, v05, colors.north, 0, 0, -1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // South face
        light0 = lightSampler.sampleVertexLight(face, 3, 0);
        light1 = lightSampler.sampleVertexLight(face, 3, 1);
        light2 = lightSampler.sampleVertexLight(face, 3, 2);
        light3 = lightSampler.sampleVertexLight(face, 3, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z1, u0, v1, colors.south, 0, 0, 1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z1, u1, v1, colors.south, 0, 0, 1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z1, u1, v05, colors.south, 0, 0, 1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z1, u0, v05, colors.south, 0, 0, 1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // West face
        light0 = lightSampler.sampleVertexLight(face, 4, 0);
        light1 = lightSampler.sampleVertexLight(face, 4, 1);
        light2 = lightSampler.sampleVertexLight(face, 4, 2);
        light3 = lightSampler.sampleVertexLight(face, 4, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z0, u0, v1, colors.west, -1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z1, u1, v1, colors.west, -1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z1, u1, v05, colors.west, -1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z0, u0, v05, colors.west, -1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // East face
        light0 = lightSampler.sampleVertexLight(face, 5, 0);
        light1 = lightSampler.sampleVertexLight(face, 5, 1);
        light2 = lightSampler.sampleVertexLight(face, 5, 2);
        light3 = lightSampler.sampleVertexLight(face, 5, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z1, u1, v1, colors.east, 1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z0, u0, v1, colors.east, 1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z0, u0, v05, colors.east, 1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z1, u1, v05, colors.east, 1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        return currentVertex;
    }
    
    /**
     * Add top step faces for stairs (based on facing direction).
     */
    private int addStairsTopStepFace(BlockFaceCollector.FaceData face, float x, float y, float z,
                                      mattmc.world.level.block.state.properties.Direction facing,
                                      FaceColors colors,
                                      TextureAtlas.UVMapping uvMapping,
                                      FloatList vertices,
                                      IntList indices,
                                      int currentVertex) {
        float x0 = x, x05 = x + 0.5f, x1 = x + 1;
        float y0 = y, y1 = y + 0.5f;
        float z0 = z, z05 = z + 0.5f, z1 = z + 1;
        
        float u0 = 0, v0 = 0, u05 = 0.5f, u1 = 1, v05 = 0.5f, v1 = 1;
        if (uvMapping != null) {
            u0 = uvMapping.u0;
            v0 = uvMapping.v0;
            u05 = (uvMapping.u0 + uvMapping.u1) / 2;
            u1 = uvMapping.u1;
            v05 = (uvMapping.v0 + uvMapping.v1) / 2;
            v1 = uvMapping.v1;
        }
        
        // Render step based on facing direction
        switch (facing) {
            case NORTH:
                return addStairsStepNorth(face, x0, x1, y0, y1, z0, z05, u0, u05, u1, v0, v05, v1, 
                                          colors, vertices, indices, currentVertex);
            case SOUTH:
                return addStairsStepSouth(face, x0, x1, y0, y1, z05, z1, u0, u05, u1, v0, v05, v1,
                                          colors, vertices, indices, currentVertex);
            case WEST:
                return addStairsStepWest(face, x0, x05, y0, y1, z0, z1, u0, u05, u1, v0, v05, v1,
                                         colors, vertices, indices, currentVertex);
            case EAST:
                return addStairsStepEast(face, x05, x1, y0, y1, z0, z1, u0, u05, u1, v0, v05, v1,
                                         colors, vertices, indices, currentVertex);
        }
        
        return currentVertex;
    }
    
    /**
     * Add step geometry for north-facing stairs (step in north half).
     */
    private int addStairsStepNorth(BlockFaceCollector.FaceData face, float x0, float x1, float y0, float y1, float z0, float z05,
                                    float u0, float u05, float u1, float v0, float v05, float v1,
                                    FaceColors colors,
                                    FloatList vertices,
                                    IntList indices,
                                    int currentVertex) {
        // Top face (north half)
        float[] light0 = lightSampler.sampleVertexLight(face, 0, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 0, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 0, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 0, 3);
        
        int base = currentVertex;
        addVertex(vertices, x0, y1, z0, u0, v0, colors.top, 0, 1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y1, z05, u0, v05, colors.top, 0, 1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z05, u1, v05, colors.top, 0, 1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z0, u1, v0, colors.top, 0, 1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // Bottom face (north half)
        light0 = lightSampler.sampleVertexLight(face, 1, 0);
        light1 = lightSampler.sampleVertexLight(face, 1, 1);
        light2 = lightSampler.sampleVertexLight(face, 1, 2);
        light3 = lightSampler.sampleVertexLight(face, 1, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z0, u0, v0, colors.bottom, 0, -1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z0, u1, v0, colors.bottom, 0, -1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y0, z05, u1, v05, colors.bottom, 0, -1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y0, z05, u0, v05, colors.bottom, 0, -1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // North face (front of step)
        light0 = lightSampler.sampleVertexLight(face, 2, 0);
        light1 = lightSampler.sampleVertexLight(face, 2, 1);
        light2 = lightSampler.sampleVertexLight(face, 2, 2);
        light3 = lightSampler.sampleVertexLight(face, 2, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z0, u1, v05, colors.north, 0, 0, -1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z0, u0, v05, colors.north, 0, 0, -1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z0, u0, v0, colors.north, 0, 0, -1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z0, u1, v0, colors.north, 0, 0, -1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // South face (inner vertical)
        light0 = lightSampler.sampleVertexLight(face, 3, 0);
        light1 = lightSampler.sampleVertexLight(face, 3, 1);
        light2 = lightSampler.sampleVertexLight(face, 3, 2);
        light3 = lightSampler.sampleVertexLight(face, 3, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z05, u0, v05, colors.south, 0, 0, 1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z05, u1, v05, colors.south, 0, 0, 1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z05, u1, v0, colors.south, 0, 0, 1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z05, u0, v0, colors.south, 0, 0, 1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // West face (left side, half depth)
        light0 = lightSampler.sampleVertexLight(face, 4, 0);
        light1 = lightSampler.sampleVertexLight(face, 4, 1);
        light2 = lightSampler.sampleVertexLight(face, 4, 2);
        light3 = lightSampler.sampleVertexLight(face, 4, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z0, u0, v05, colors.west, -1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z05, u05, v05, colors.west, -1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z05, u05, v0, colors.west, -1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z0, u0, v0, colors.west, -1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // East face (right side, half depth)
        light0 = lightSampler.sampleVertexLight(face, 5, 0);
        light1 = lightSampler.sampleVertexLight(face, 5, 1);
        light2 = lightSampler.sampleVertexLight(face, 5, 2);
        light3 = lightSampler.sampleVertexLight(face, 5, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z05, u05, v05, colors.east, 1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z0, u0, v05, colors.east, 1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z0, u0, v0, colors.east, 1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z05, u05, v0, colors.east, 1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        return currentVertex;
    }
    
    /**
     * Add step geometry for south-facing stairs (step in south half).
     */
    private int addStairsStepSouth(BlockFaceCollector.FaceData face, float x0, float x1, float y0, float y1, float z05, float z1,
                                    float u0, float u05, float u1, float v0, float v05, float v1,
                                    FaceColors colors,
                                    FloatList vertices,
                                    IntList indices,
                                    int currentVertex) {
        // Top face (south half)
        float[] light0 = lightSampler.sampleVertexLight(face, 0, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 0, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 0, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 0, 3);
        
        int base = currentVertex;
        addVertex(vertices, x0, y1, z05, u0, v05, colors.top, 0, 1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y1, z1, u0, v1, colors.top, 0, 1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z1, u1, v1, colors.top, 0, 1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z05, u1, v05, colors.top, 0, 1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // Bottom face (south half)
        light0 = lightSampler.sampleVertexLight(face, 1, 0);
        light1 = lightSampler.sampleVertexLight(face, 1, 1);
        light2 = lightSampler.sampleVertexLight(face, 1, 2);
        light3 = lightSampler.sampleVertexLight(face, 1, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z05, u0, v05, colors.bottom, 0, -1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z05, u1, v05, colors.bottom, 0, -1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y0, z1, u1, v1, colors.bottom, 0, -1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y0, z1, u0, v1, colors.bottom, 0, -1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // North face (inner vertical)
        light0 = lightSampler.sampleVertexLight(face, 2, 0);
        light1 = lightSampler.sampleVertexLight(face, 2, 1);
        light2 = lightSampler.sampleVertexLight(face, 2, 2);
        light3 = lightSampler.sampleVertexLight(face, 2, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z05, u1, v05, colors.north, 0, 0, -1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z05, u0, v05, colors.north, 0, 0, -1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z05, u0, v0, colors.north, 0, 0, -1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z05, u1, v0, colors.north, 0, 0, -1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // South face (front of step)
        light0 = lightSampler.sampleVertexLight(face, 3, 0);
        light1 = lightSampler.sampleVertexLight(face, 3, 1);
        light2 = lightSampler.sampleVertexLight(face, 3, 2);
        light3 = lightSampler.sampleVertexLight(face, 3, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z1, u0, v05, colors.south, 0, 0, 1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z1, u1, v05, colors.south, 0, 0, 1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z1, u1, v0, colors.south, 0, 0, 1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z1, u0, v0, colors.south, 0, 0, 1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // West face (left side, half depth)
        light0 = lightSampler.sampleVertexLight(face, 4, 0);
        light1 = lightSampler.sampleVertexLight(face, 4, 1);
        light2 = lightSampler.sampleVertexLight(face, 4, 2);
        light3 = lightSampler.sampleVertexLight(face, 4, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z05, u05, v05, colors.west, -1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z1, u1, v05, colors.west, -1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z1, u1, v0, colors.west, -1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z05, u05, v0, colors.west, -1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // East face (right side, half depth)
        light0 = lightSampler.sampleVertexLight(face, 5, 0);
        light1 = lightSampler.sampleVertexLight(face, 5, 1);
        light2 = lightSampler.sampleVertexLight(face, 5, 2);
        light3 = lightSampler.sampleVertexLight(face, 5, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z1, u1, v05, colors.east, 1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z05, u05, v05, colors.east, 1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z05, u05, v0, colors.east, 1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z1, u1, v0, colors.east, 1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        return currentVertex;
    }
    
    /**
     * Add step geometry for west-facing stairs (step in west half).
     */
    private int addStairsStepWest(BlockFaceCollector.FaceData face, float x0, float x05, float y0, float y1, float z0, float z1,
                                   float u0, float u05, float u1, float v0, float v05, float v1,
                                   FaceColors colors,
                                   FloatList vertices,
                                   IntList indices,
                                   int currentVertex) {
        // Top face (west half)
        float[] light0 = lightSampler.sampleVertexLight(face, 0, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 0, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 0, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 0, 3);
        
        int base = currentVertex;
        addVertex(vertices, x0, y1, z0, u0, v0, colors.top, 0, 1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y1, z1, u0, v1, colors.top, 0, 1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x05, y1, z1, u05, v1, colors.top, 0, 1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x05, y1, z0, u05, v0, colors.top, 0, 1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // Bottom face (west half)
        light0 = lightSampler.sampleVertexLight(face, 1, 0);
        light1 = lightSampler.sampleVertexLight(face, 1, 1);
        light2 = lightSampler.sampleVertexLight(face, 1, 2);
        light3 = lightSampler.sampleVertexLight(face, 1, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z0, u0, v0, colors.bottom, 0, -1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x05, y0, z0, u05, v0, colors.bottom, 0, -1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x05, y0, z1, u05, v1, colors.bottom, 0, -1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y0, z1, u0, v1, colors.bottom, 0, -1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // North face (left side, half width)
        light0 = lightSampler.sampleVertexLight(face, 2, 0);
        light1 = lightSampler.sampleVertexLight(face, 2, 1);
        light2 = lightSampler.sampleVertexLight(face, 2, 2);
        light3 = lightSampler.sampleVertexLight(face, 2, 3);
        
        base = currentVertex;
        addVertex(vertices, x05, y0, z0, u05, v05, colors.north, 0, 0, -1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z0, u0, v05, colors.north, 0, 0, -1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z0, u0, v0, colors.north, 0, 0, -1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x05, y1, z0, u05, v0, colors.north, 0, 0, -1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // South face (right side, half width)
        light0 = lightSampler.sampleVertexLight(face, 3, 0);
        light1 = lightSampler.sampleVertexLight(face, 3, 1);
        light2 = lightSampler.sampleVertexLight(face, 3, 2);
        light3 = lightSampler.sampleVertexLight(face, 3, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z1, u0, v05, colors.south, 0, 0, 1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x05, y0, z1, u05, v05, colors.south, 0, 0, 1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x05, y1, z1, u05, v0, colors.south, 0, 0, 1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z1, u0, v0, colors.south, 0, 0, 1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // West face (front of step)
        light0 = lightSampler.sampleVertexLight(face, 4, 0);
        light1 = lightSampler.sampleVertexLight(face, 4, 1);
        light2 = lightSampler.sampleVertexLight(face, 4, 2);
        light3 = lightSampler.sampleVertexLight(face, 4, 3);
        
        base = currentVertex;
        addVertex(vertices, x0, y0, z0, u0, v05, colors.west, -1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x0, y0, z1, u1, v05, colors.west, -1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x0, y1, z1, u1, v0, colors.west, -1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x0, y1, z0, u0, v0, colors.west, -1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // East face (inner vertical)
        light0 = lightSampler.sampleVertexLight(face, 5, 0);
        light1 = lightSampler.sampleVertexLight(face, 5, 1);
        light2 = lightSampler.sampleVertexLight(face, 5, 2);
        light3 = lightSampler.sampleVertexLight(face, 5, 3);
        
        base = currentVertex;
        addVertex(vertices, x05, y0, z1, u1, v05, colors.east, 1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x05, y0, z0, u0, v05, colors.east, 1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x05, y1, z0, u0, v0, colors.east, 1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x05, y1, z1, u1, v0, colors.east, 1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        return currentVertex;
    }
    
    /**
     * Add step geometry for east-facing stairs (step in east half).
     */
    private int addStairsStepEast(BlockFaceCollector.FaceData face, float x05, float x1, float y0, float y1, float z0, float z1,
                                   float u0, float u05, float u1, float v0, float v05, float v1,
                                   FaceColors colors,
                                   FloatList vertices,
                                   IntList indices,
                                   int currentVertex) {
        // Top face (east half)
        float[] light0 = lightSampler.sampleVertexLight(face, 0, 0);
        float[] light1 = lightSampler.sampleVertexLight(face, 0, 1);
        float[] light2 = lightSampler.sampleVertexLight(face, 0, 2);
        float[] light3 = lightSampler.sampleVertexLight(face, 0, 3);
        
        int base = currentVertex;
        addVertex(vertices, x05, y1, z0, u05, v0, colors.top, 0, 1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x05, y1, z1, u05, v1, colors.top, 0, 1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z1, u1, v1, colors.top, 0, 1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z0, u1, v0, colors.top, 0, 1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // Bottom face (east half)
        light0 = lightSampler.sampleVertexLight(face, 1, 0);
        light1 = lightSampler.sampleVertexLight(face, 1, 1);
        light2 = lightSampler.sampleVertexLight(face, 1, 2);
        light3 = lightSampler.sampleVertexLight(face, 1, 3);
        
        base = currentVertex;
        addVertex(vertices, x05, y0, z0, u05, v0, colors.bottom, 0, -1, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z0, u1, v0, colors.bottom, 0, -1, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y0, z1, u1, v1, colors.bottom, 0, -1, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x05, y0, z1, u05, v1, colors.bottom, 0, -1, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // North face (right side, half width)
        light0 = lightSampler.sampleVertexLight(face, 2, 0);
        light1 = lightSampler.sampleVertexLight(face, 2, 1);
        light2 = lightSampler.sampleVertexLight(face, 2, 2);
        light3 = lightSampler.sampleVertexLight(face, 2, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z0, u1, v05, colors.north, 0, 0, -1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x05, y0, z0, u05, v05, colors.north, 0, 0, -1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x05, y1, z0, u05, v0, colors.north, 0, 0, -1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z0, u1, v0, colors.north, 0, 0, -1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // South face (left side, half width)
        light0 = lightSampler.sampleVertexLight(face, 3, 0);
        light1 = lightSampler.sampleVertexLight(face, 3, 1);
        light2 = lightSampler.sampleVertexLight(face, 3, 2);
        light3 = lightSampler.sampleVertexLight(face, 3, 3);
        
        base = currentVertex;
        addVertex(vertices, x05, y0, z1, u05, v05, colors.south, 0, 0, 1, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z1, u1, v05, colors.south, 0, 0, 1, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z1, u1, v0, colors.south, 0, 0, 1, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x05, y1, z1, u05, v0, colors.south, 0, 0, 1, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // West face (inner vertical)
        light0 = lightSampler.sampleVertexLight(face, 4, 0);
        light1 = lightSampler.sampleVertexLight(face, 4, 1);
        light2 = lightSampler.sampleVertexLight(face, 4, 2);
        light3 = lightSampler.sampleVertexLight(face, 4, 3);
        
        base = currentVertex;
        addVertex(vertices, x05, y0, z0, u0, v05, colors.west, -1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x05, y0, z1, u1, v05, colors.west, -1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x05, y1, z1, u1, v0, colors.west, -1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x05, y1, z0, u0, v0, colors.west, -1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        // East face (front of step)
        light0 = lightSampler.sampleVertexLight(face, 5, 0);
        light1 = lightSampler.sampleVertexLight(face, 5, 1);
        light2 = lightSampler.sampleVertexLight(face, 5, 2);
        light3 = lightSampler.sampleVertexLight(face, 5, 3);
        
        base = currentVertex;
        addVertex(vertices, x1, y0, z1, u1, v05, colors.east, 1, 0, 0, light0[0], light0[1], light0[2], light0[3], light0[4]);
        addVertex(vertices, x1, y0, z0, u0, v05, colors.east, 1, 0, 0, light1[0], light1[1], light1[2], light1[3], light1[4]);
        addVertex(vertices, x1, y1, z0, u0, v0, colors.east, 1, 0, 0, light2[0], light2[1], light2[2], light2[3], light2[4]);
        addVertex(vertices, x1, y1, z1, u1, v0, colors.east, 1, 0, 0, light3[0], light3[1], light3[2], light3[3], light3[4]);
        addQuadIndices(indices, base);
        currentVertex += 4;
        
        return currentVertex;
    }
    
    /**
     * Add a single vertex to the vertex list with normal and light data.
     */
    private void addVertex(FloatList vertices, float x, float y, float z, float u, float v, float[] color,
                          float nx, float ny, float nz, 
                          float skyLight, float blockLightR, float blockLightG, float blockLightB, float ao) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(u);
        vertices.add(v);
        vertices.add(color[0]);
        vertices.add(color[1]);
        vertices.add(color[2]);
        vertices.add(color[3]);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(skyLight);
        vertices.add(blockLightR);
        vertices.add(blockLightG);
        vertices.add(blockLightB);
        vertices.add(ao);
    }
    
    /**
     * Add indices for a quad (2 triangles).
     */
    private void addQuadIndices(IntList indices, int baseVertex) {
        indices.add(baseVertex + 0);
        indices.add(baseVertex + 1);
        indices.add(baseVertex + 2);
        indices.add(baseVertex + 0);
        indices.add(baseVertex + 2);
        indices.add(baseVertex + 3);
    }
    
    /**
     * Helper class to hold face colors with appropriate brightness levels.
     */
    private static class FaceColors {
        final float[] top = {1.0f, 1.0f, 1.0f, 1.0f};
        final float[] bottom = {0.5f, 0.5f, 0.5f, 1.0f};
        final float[] north = {0.8f, 0.8f, 0.8f, 1.0f};
        final float[] south = {0.8f, 0.8f, 0.8f, 1.0f};
        final float[] west = {0.6f, 0.6f, 0.6f, 1.0f};
        final float[] east = {0.6f, 0.6f, 0.6f, 1.0f};
    }
}
