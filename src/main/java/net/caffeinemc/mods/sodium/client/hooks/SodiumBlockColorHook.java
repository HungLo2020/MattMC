package net.caffeinemc.mods.sodium.client.hooks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.hooks.BlockColorHooks;
import net.minecraft.world.level.block.Block;

/**
 * Sodium's implementation of BlockColorHooks.
 * Tracks block color provider registrations to determine which blocks
 * have had their vanilla color providers overridden by mods.
 */
public class SodiumBlockColorHook implements BlockColorHooks {
    private static final SodiumBlockColorHook INSTANCE = new SodiumBlockColorHook();
    
    private final Reference2ReferenceMap<Block, BlockColor> blocksToColor = new Reference2ReferenceOpenHashMap<>();
    private final ReferenceSet<Block> overridenBlocks = new ReferenceOpenHashSet<>();

    private SodiumBlockColorHook() {
    }

    public static SodiumBlockColorHook getInstance() {
        return INSTANCE;
    }

    @Override
    public void onBlockColorRegistered(BlockColor provider, Block block, boolean isReplacement) {
        this.blocksToColor.put(block, provider);
        
        if (isReplacement) {
            // A mod is replacing a vanilla color provider, so we need to disable per-vertex coloring
            this.overridenBlocks.add(block);
            SodiumClientMod.logger().info(
                "Block {} had its color provider replaced with {} and will not use per-vertex coloring", 
                BuiltInRegistries.BLOCK.getKey(block), 
                provider.toString()
            );
        }
    }

    /**
     * Get all registered block color providers.
     * @return Unmodifiable map of blocks to color providers
     */
    public Reference2ReferenceMap<Block, BlockColor> getProviders() {
        return blocksToColor;
    }

    /**
     * Get blocks that have had their vanilla color providers overridden.
     * @return Unmodifiable set of overridden blocks
     */
    public ReferenceSet<Block> getOverridenVanillaBlocks() {
        return overridenBlocks;
    }
}
