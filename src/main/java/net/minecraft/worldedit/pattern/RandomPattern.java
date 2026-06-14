package net.minecraft.worldedit.pattern;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pattern that randomly selects from multiple block types
 */
public class RandomPattern implements Pattern {
    private static class WeightedBlock {
        BlockState state;
        double weight;
        
        WeightedBlock(BlockState state, double weight) {
            this.state = state;
            this.weight = weight;
        }
    }
    
    private final List<WeightedBlock> blocks;
    private final Random random;
    private double totalWeight;
    
    public RandomPattern() {
        this.blocks = new ArrayList<>();
        this.random = new Random();
        this.totalWeight = 0;
    }
    
    /**
     * Add a block type with weight
     */
    public void add(BlockState state, double weight) {
        blocks.add(new WeightedBlock(state, weight));
        totalWeight += weight;
    }

    int getEntryCount() {
        return blocks.size();
    }

    double getTotalWeight() {
        return totalWeight;
    }
    
    @Override
    public BlockState apply(BlockVector3 position) {
        if (blocks.isEmpty()) {
            return null;
        }
        
        double value = random.nextDouble() * totalWeight;
        double current = 0;
        
        for (WeightedBlock block : blocks) {
            current += block.weight;
            if (value <= current) {
                return block.state;
            }
        }
        
        return blocks.get(blocks.size() - 1).state;
    }
}
