package net.sodium.client.hooks;

import net.sodium.api.blockentity.BlockEntityRenderPredicate;
import net.minecraft.hooks.BlockEntityTypeHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sodium's implementation of BlockEntityTypeHooks.
 * Manages render predicates for block entity types.
 */
public class SodiumBlockEntityTypeHook implements BlockEntityTypeHooks {
    private static final SodiumBlockEntityTypeHook INSTANCE = new SodiumBlockEntityTypeHook();
    
    // Use WeakHashMap to avoid memory leaks
    private final Map<BlockEntityType<?>, BlockEntityRenderPredicate<?>[]> renderPredicates = new WeakHashMap<>();

    private SodiumBlockEntityTypeHook() {
    }

    public static SodiumBlockEntityTypeHook getInstance() {
        return INSTANCE;
    }

    /**
     * Get render predicates for a block entity type.
     * Used by ExtendedBlockEntityType interface.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityRenderPredicate<T>[] getRenderPredicates(BlockEntityType<T> type) {
        BlockEntityRenderPredicate<?>[] predicates = INSTANCE.renderPredicates.get(type);
        if (predicates == null) {
            predicates = new BlockEntityRenderPredicate[0];
            INSTANCE.renderPredicates.put(type, predicates);
        }
        return (BlockEntityRenderPredicate<T>[]) predicates;
    }

    /**
     * Add a render predicate to a block entity type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void addRenderPredicate(BlockEntityType<T> type, BlockEntityRenderPredicate<T> predicate) {
        BlockEntityRenderPredicate<T>[] current = (BlockEntityRenderPredicate<T>[]) INSTANCE.renderPredicates.get(type);
        if (current == null) {
            current = (BlockEntityRenderPredicate<T>[]) new BlockEntityRenderPredicate[0];
        }
        BlockEntityRenderPredicate<T>[] updated = ArrayUtils.add(current, predicate);
        INSTANCE.renderPredicates.put(type, updated);
    }

    /**
     * Remove a render predicate from a block entity type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> boolean removeRenderPredicate(BlockEntityType<T> type, BlockEntityRenderPredicate<T> predicate) {
        BlockEntityRenderPredicate<T>[] current = (BlockEntityRenderPredicate<T>[]) INSTANCE.renderPredicates.get(type);
        if (current == null) {
            return false;
        }
        
        int index = ArrayUtils.indexOf(current, predicate);
        if (index == ArrayUtils.INDEX_NOT_FOUND) {
            return false;
        }
        
        BlockEntityRenderPredicate<T>[] updated = ArrayUtils.remove(current, index);
        INSTANCE.renderPredicates.put(type, updated);
        return true;
    }
}
