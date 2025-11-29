package mattmc.world.level.block;

import mattmc.world.level.Level;
import mattmc.world.phys.shapes.VoxelShape;

import java.util.Random;

/**
 * Represents a torch block in the world.
 * Based on MattMC's TorchBlock class.
 * 
 * Torches are non-solid blocks that emit light and have a small collision shape.
 * They can only be placed on solid surfaces below them.
 * Torches also spawn flame and smoke particles.
 */
public class TorchBlock extends Block {
    // Torch collision shape - small box in the center
    private static final VoxelShape TORCH_SHAPE = VoxelShape.box(0.375, 0.0, 0.375, 0.625, 0.625, 0.625);
    
    /**
     * Create a new torch block with full light emission parameters.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmission Overall light emission level (0-15), used for intensity
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public TorchBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB);
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     */
    protected TorchBlock(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB, String identifier) {
        super(solid, lightEmission, lightEmissionR, lightEmissionG, lightEmissionB, identifier);
    }
    
    @Override
    public Block withIdentifier(String identifier) {
        return new TorchBlock(isSolid(), computeLightEmission(), getLightEmissionR(), getLightEmissionG(), getLightEmissionB(), identifier);
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
    
    /**
     * Torches spawn flame and smoke particles.
     */
    @Override
    public boolean hasRandomTick() {
        return true;
    }
    
    /**
     * Called periodically client-side to spawn flame and smoke particles.
     * Mirrors Minecraft's TorchBlock.animateTick.
     */
    @Override
    public void animateTick(Level level, int x, int y, int z, Random random,
                           ParticleSpawner particleSpawner) {
        // Spawn at the top center of the torch
        double px = x + 0.5;
        double py = y + 0.7;
        double pz = z + 0.5;
        
        // Spawn smoke particle
        particleSpawner.spawn("smoke", px, py, pz, 0.0, 0.0, 0.0);
        
        // Spawn flame particle
        particleSpawner.spawn("flame", px, py, pz, 0.0, 0.0, 0.0);
    }
}
