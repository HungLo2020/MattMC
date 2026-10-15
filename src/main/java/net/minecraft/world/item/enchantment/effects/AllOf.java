package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface AllOf {
	static AllOf.EntityEffects entityEffects(EnchantmentEntityEffect... enchantmentEntityEffects) {
		return new AllOf.EntityEffects(List.of(enchantmentEntityEffects));
	}

	static AllOf.LocationBasedEffects locationBasedEffects(EnchantmentLocationBasedEffect... enchantmentLocationBasedEffects) {
		return new AllOf.LocationBasedEffects(List.of(enchantmentLocationBasedEffects));
	}

	static AllOf.ValueEffects valueEffects(EnchantmentValueEffect... enchantmentValueEffects) {
		return new AllOf.ValueEffects(List.of(enchantmentValueEffects));
	}

	public record EntityEffects(List<EnchantmentEntityEffect> effects) implements EnchantmentEntityEffect {
		@SuppressWarnings("unchecked")
		public static final MapCodec<AllOf.EntityEffects> CODEC = (MapCodec<AllOf.EntityEffects>)(MapCodec<?>)EnchantmentEntityEffect.CODEC.dispatchCase("minecraft:all_of");

		@Override
		public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
			for (EnchantmentEntityEffect enchantmentEntityEffect : this.effects) {
				enchantmentEntityEffect.apply(serverLevel, i, enchantedItemInUse, entity, vec3);
			}
		}

		@Override
		public MapCodec<AllOf.EntityEffects> codec() {
			return CODEC;
		}
	}

	public record LocationBasedEffects(List<EnchantmentLocationBasedEffect> effects) implements EnchantmentLocationBasedEffect {
		@SuppressWarnings("unchecked")
		public static final MapCodec<AllOf.LocationBasedEffects> CODEC = (MapCodec<AllOf.LocationBasedEffects>)(MapCodec<?>)EnchantmentLocationBasedEffect.CODEC.dispatchCase("minecraft:all_of");

		@Override
		public void onChangedBlock(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3, boolean bl) {
			for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : this.effects) {
				enchantmentLocationBasedEffect.onChangedBlock(serverLevel, i, enchantedItemInUse, entity, vec3, bl);
			}
		}

		@Override
		public void onDeactivated(EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3, int i) {
			for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : this.effects) {
				enchantmentLocationBasedEffect.onDeactivated(enchantedItemInUse, entity, vec3, i);
			}
		}

		@Override
		public MapCodec<AllOf.LocationBasedEffects> codec() {
			return CODEC;
		}
	}

	public record ValueEffects(List<EnchantmentValueEffect> effects) implements EnchantmentValueEffect {
		@SuppressWarnings("unchecked")
		public static final MapCodec<AllOf.ValueEffects> CODEC = (MapCodec<AllOf.ValueEffects>)(MapCodec<?>)EnchantmentValueEffect.CODEC.dispatchCase("minecraft:all_of");

		@Override
		public float process(int i, RandomSource randomSource, float f) {
			for (EnchantmentValueEffect enchantmentValueEffect : this.effects) {
				f = enchantmentValueEffect.process(i, randomSource, f);
			}

			return f;
		}

		@Override
		public MapCodec<AllOf.ValueEffects> codec() {
			return CODEC;
		}
	}
}
