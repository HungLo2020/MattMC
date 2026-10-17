package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record SetBlockProperties(BlockItemStateProperties properties, Vec3i offset, Optional<Holder<GameEvent>> triggerGameEvent)
	implements EnchantmentEntityEffect {
	/** The native codec's `set_block_properties` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<SetBlockProperties> CODEC = (MapCodec<SetBlockProperties>)(MapCodec<?>)EnchantmentEntityEffect.CODEC.dispatchCase("minecraft:set_block_properties");

	public SetBlockProperties(BlockItemStateProperties blockItemStateProperties) {
		this(blockItemStateProperties, Vec3i.ZERO, Optional.of(GameEvent.BLOCK_CHANGE));
	}

	@Override
	public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
		BlockPos blockPos = BlockPos.containing(vec3).offset(this.offset);
		BlockState blockState = entity.level().getBlockState(blockPos);
		BlockState blockState2 = this.properties.apply(blockState);
		if (blockState != blockState2 && entity.level().setBlock(blockPos, blockState2, 3)) {
			this.triggerGameEvent.ifPresent(holder -> serverLevel.gameEvent(entity, holder, blockPos));
		}
	}

	@Override
	public MapCodec<SetBlockProperties> codec() {
		return CODEC;
	}
}
