package com.github.alexthe666.citadel.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;

// Stub accessor - we have direct source access, no mixin needed
public class BlockBehaviourAccessor {
    public static boolean invokeIsAir(BlockBehaviour.BlockStateBase state) {
        return state.isAir();
    }
}
