package mattmc.world.level.block;

import mattmc.world.phys.shapes.VoxelShape;

/**
 * Represents a torch block in the world.
 * Based on Minecraft's TorchBlock class.
 * 
 * Torches are non-solid blocks that emit light and have a small collision shape.
 * They can only be placed on solid surfaces below them.
 */
public class TorchBlock extends Block {
    // Torch collision shape - small box in the center
    private static final VoxelShape TORCH_SHAPE = VoxelShape.box(0.375, 0.0, 0.375, 0.625, 0.625, 0.625);
    
    /**
     * Create a new torch block with RGB light emission.
     * 
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public TorchBlock(int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        super(false, lightEmissionR, lightEmissionG, lightEmissionB);
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    TorchBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB, String identifier) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB, identifier);
    }
    
    /**
     * Get the collision shape for torch.
     * Returns a small box in the center of the block.
     */
    @Override
    public VoxelShape getCollisionShape() {
        return TORCH_SHAPE;
    }
    
    /**
     * Torches use custom rendering with VBO-generated geometry.
     */
    @Override
    public boolean hasCustomRendering() {
        return true;
    }
}
