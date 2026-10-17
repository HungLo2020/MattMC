package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record ExplodeEffect(
	boolean attributeToUser,
	Optional<Holder<DamageType>> damageType,
	Optional<LevelBasedValue> knockbackMultiplier,
	Optional<HolderSet<Block>> immuneBlocks,
	Vec3 offset,
	LevelBasedValue radius,
	boolean createFire,
	Level.ExplosionInteraction blockInteraction,
	ParticleOptions smallParticle,
	ParticleOptions largeParticle,
	WeightedList<ExplosionParticleInfo> blockParticles,
	Holder<SoundEvent> sound
) implements EnchantmentEntityEffect {
	/** The native codec's `explode` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<ExplodeEffect> CODEC = (MapCodec<ExplodeEffect>)(MapCodec<?>)EnchantmentEntityEffect.CODEC.dispatchCase("minecraft:explode");

	@Override
	public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
		Vec3 vec32 = vec3.add(this.offset);
		serverLevel.explode(
			this.attributeToUser ? entity : null,
			this.getDamageSource(entity, vec32),
			new SimpleExplosionDamageCalculator(
				this.blockInteraction != Level.ExplosionInteraction.NONE,
				this.damageType.isPresent(),
				this.knockbackMultiplier.map(levelBasedValue -> levelBasedValue.calculate(i)),
				this.immuneBlocks
			),
			vec32.x(),
			vec32.y(),
			vec32.z(),
			Math.max(this.radius.calculate(i), 0.0F),
			this.createFire,
			this.blockInteraction,
			this.smallParticle,
			this.largeParticle,
			this.blockParticles,
			this.sound
		);
	}

	@Nullable
	private DamageSource getDamageSource(Entity entity, Vec3 vec3) {
		if (this.damageType.isEmpty()) {
			return null;
		} else {
			return this.attributeToUser
				? new DamageSource((Holder<DamageType>)this.damageType.get(), entity)
				: new DamageSource((Holder<DamageType>)this.damageType.get(), vec3);
		}
	}

	@Override
	public MapCodec<ExplodeEffect> codec() {
		return CODEC;
	}
}
