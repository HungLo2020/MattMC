package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceBlock(Vec3i offset, Optional<BlockPredicate> predicate, BlockStateProvider blockState, Optional<Holder<GameEvent>> triggerGameEvent)
	implements EnchantmentEntityEffect {
	/** The native codec's `replace_block` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<ReplaceBlock> CODEC = (MapCodec<ReplaceBlock>)(MapCodec<?>)EnchantmentEntityEffect.CODEC.dispatchCase("minecraft:replace_block");

	@Override
	public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
		BlockPos blockPos = BlockPos.containing(vec3).offset(this.offset);
		if ((Boolean)this.predicate.map(blockPredicate -> blockPredicate.test(serverLevel, blockPos)).orElse(true)
			&& serverLevel.setBlockAndUpdate(blockPos, this.blockState.getState(entity.getRandom(), blockPos))) {
			this.triggerGameEvent.ifPresent(holder -> serverLevel.gameEvent(entity, holder, blockPos));
		}
	}

	@Override
	public MapCodec<ReplaceBlock> codec() {
		return CODEC;
	}
}
