package net.sodium.client.render.chunk;

import net.sodium.client.hooks.SodiumBlockEntityTypeHook;
import net.sodium.api.blockentity.BlockEntityRenderPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

@SuppressWarnings("unchecked")
public interface ExtendedBlockEntityType<T extends BlockEntity> {
    BlockEntityRenderPredicate<T>[] sodium$getRenderPredicates();

    void sodium$addRenderPredicate(BlockEntityRenderPredicate<T> shouldAddRenderer);

    boolean sodium$removeRenderPredicate(BlockEntityRenderPredicate<T> shouldAddRenderer);

    static <T extends BlockEntity> boolean shouldRender(BlockEntityType<? extends T> type, BlockGetter blockGetter, BlockPos blockPos, T entity) {
       BlockEntityRenderPredicate<T>[] predicates = SodiumBlockEntityTypeHook.getRenderPredicates((BlockEntityType<T>) type);

        for (int i = 0; i < predicates.length; i++) {
            if (!predicates[i].shouldRender(blockGetter, blockPos, entity)) {
                return false;
            }
        }

        return true;
    }

    static <T extends BlockEntity> void addRenderPredicate(BlockEntityType<T> type, BlockEntityRenderPredicate<T> predicate) {
        SodiumBlockEntityTypeHook.addRenderPredicate(type, predicate);
    }

    static <T extends BlockEntity> boolean removeRenderPredicate(BlockEntityType<T> type, BlockEntityRenderPredicate<T> predicate) {
        return SodiumBlockEntityTypeHook.removeRenderPredicate(type, predicate);
    }
}
