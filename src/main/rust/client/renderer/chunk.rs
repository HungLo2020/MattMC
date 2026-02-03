use super::cube::CubeVertex;

/// Chunk dimensions matching Minecraft
pub const CHUNK_WIDTH: usize = 16;
pub const CHUNK_HEIGHT: usize = 384;
pub const SURFACE_Y: usize = 70;

/// Block types in our simple world
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Block {
    Air,
    Stone,
}

impl Block {
    /// Get the color for this block type
    pub fn color(&self) -> [f32; 3] {
        match self {
            Block::Air => [0.0, 0.0, 0.0],      // Transparent (won't be rendered)
            Block::Stone => [0.5, 0.5, 0.5],    // Gray
        }
    }

    /// Check if this block is solid (opaque)
    pub fn is_solid(&self) -> bool {
        match self {
            Block::Air => false,
            Block::Stone => true,
        }
    }
}

/// A chunk represents a 16x16x384 block section of the world
pub struct Chunk {
    blocks: [[[Block; CHUNK_WIDTH]; CHUNK_HEIGHT]; CHUNK_WIDTH],
}

impl Chunk {
    /// Create a new chunk with default terrain generation
    /// Surface at y=70, everything below is stone, everything above is air
    pub fn new() -> Self {
        let mut blocks = [[[Block::Air; CHUNK_WIDTH]; CHUNK_HEIGHT]; CHUNK_WIDTH];

        // Fill blocks based on height
        for x in 0..CHUNK_WIDTH {
            for y in 0..CHUNK_HEIGHT {
                for z in 0..CHUNK_WIDTH {
                    blocks[x][y][z] = if y < SURFACE_Y {
                        Block::Stone
                    } else {
                        Block::Air
                    };
                }
            }
        }

        Self { blocks }
    }

    /// Get block at position (x, y, z) within the chunk
    pub fn get_block(&self, x: usize, y: usize, z: usize) -> Block {
        if x < CHUNK_WIDTH && y < CHUNK_HEIGHT && z < CHUNK_WIDTH {
            self.blocks[x][y][z]
        } else {
            Block::Air
        }
    }

    /// Check if a block face should be rendered (i.e., it's adjacent to air or chunk boundary)
    fn should_render_face(&self, x: i32, y: i32, z: i32) -> bool {
        // Check chunk boundaries
        if x < 0 || x >= CHUNK_WIDTH as i32 || 
           y < 0 || y >= CHUNK_HEIGHT as i32 || 
           z < 0 || z >= CHUNK_WIDTH as i32 {
            return true; // Render faces at chunk boundaries
        }

        // Check if adjacent block is air (not solid)
        let block = self.get_block(x as usize, y as usize, z as usize);
        !block.is_solid()
    }

    /// Generate vertices for the chunk with face culling
    pub fn generate_vertices(&self) -> Vec<CubeVertex> {
        let mut vertices = Vec::new();

        for x in 0..CHUNK_WIDTH {
            for y in 0..CHUNK_HEIGHT {
                for z in 0..CHUNK_WIDTH {
                    let block = self.get_block(x, y, z);
                    
                    // Skip air blocks
                    if !block.is_solid() {
                        continue;
                    }

                    let color = block.color();
                    let fx = x as f32;
                    let fy = y as f32;
                    let fz = z as f32;

                    // Add vertices for each face that should be rendered
                    // Using face culling - only render faces adjacent to air

                    // Front face (+Z)
                    if self.should_render_face(x as i32, y as i32, (z + 1) as i32) {
                        vertices.extend_from_slice(&[
                            CubeVertex { position: [fx, fy, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx, fy, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx, fy + 1.0, fz + 1.0], color },
                        ]);
                    }

                    // Back face (-Z)
                    if self.should_render_face(x as i32, y as i32, z as i32 - 1) {
                        vertices.extend_from_slice(&[
                            CubeVertex { position: [fx + 1.0, fy, fz], color },
                            CubeVertex { position: [fx, fy, fz], color },
                            CubeVertex { position: [fx, fy + 1.0, fz], color },
                            CubeVertex { position: [fx + 1.0, fy, fz], color },
                            CubeVertex { position: [fx, fy + 1.0, fz], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz], color },
                        ]);
                    }

                    // Top face (+Y)
                    if self.should_render_face(x as i32, (y + 1) as i32, z as i32) {
                        vertices.extend_from_slice(&[
                            CubeVertex { position: [fx, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz], color },
                            CubeVertex { position: [fx, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz], color },
                            CubeVertex { position: [fx, fy + 1.0, fz], color },
                        ]);
                    }

                    // Bottom face (-Y)
                    if self.should_render_face(x as i32, y as i32 - 1, z as i32) {
                        vertices.extend_from_slice(&[
                            CubeVertex { position: [fx, fy, fz], color },
                            CubeVertex { position: [fx + 1.0, fy, fz], color },
                            CubeVertex { position: [fx + 1.0, fy, fz + 1.0], color },
                            CubeVertex { position: [fx, fy, fz], color },
                            CubeVertex { position: [fx + 1.0, fy, fz + 1.0], color },
                            CubeVertex { position: [fx, fy, fz + 1.0], color },
                        ]);
                    }

                    // Right face (+X)
                    if self.should_render_face((x + 1) as i32, y as i32, z as i32) {
                        vertices.extend_from_slice(&[
                            CubeVertex { position: [fx + 1.0, fy, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy, fz], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz], color },
                            CubeVertex { position: [fx + 1.0, fy, fz + 1.0], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz], color },
                            CubeVertex { position: [fx + 1.0, fy + 1.0, fz + 1.0], color },
                        ]);
                    }

                    // Left face (-X)
                    if self.should_render_face(x as i32 - 1, y as i32, z as i32) {
                        vertices.extend_from_slice(&[
                            CubeVertex { position: [fx, fy, fz], color },
                            CubeVertex { position: [fx, fy, fz + 1.0], color },
                            CubeVertex { position: [fx, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx, fy, fz], color },
                            CubeVertex { position: [fx, fy + 1.0, fz + 1.0], color },
                            CubeVertex { position: [fx, fy + 1.0, fz], color },
                        ]);
                    }
                }
            }
        }

        vertices
    }
}
