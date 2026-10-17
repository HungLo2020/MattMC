package net.minecraft.core.particles;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValueTypes;
import net.minecraft.resources.NativeValues;
import net.minecraft.world.level.gameevent.PositionSource;

/**
 * Particle options as projections of Rust's decoded values
 * (`content/data/values.rs` {@code PARTICLE}): a case per particle type, in
 * registry order, which Rust's case names must match.
 */
public final class NativeParticles {
	private static final ParticleType<?>[] TYPES = types();

	private NativeParticles() {
	}

	private static ParticleType<?>[] types() {
		List<String> names = NativeValues.caseNames(NativeValues.Kind.PARTICLE);
		if (names.size() != BuiltInRegistries.PARTICLE_TYPE.size()) {
			throw new IllegalStateException("Rust has " + names.size() + " particle types, Java " + BuiltInRegistries.PARTICLE_TYPE.size());
		}
		ParticleType<?>[] types = new ParticleType<?>[names.size()];
		for (int i = 0; i < types.length; i++) {
			types[i] = BuiltInRegistries.PARTICLE_TYPE.byId(i);
			String java = BuiltInRegistries.PARTICLE_TYPE.getKey(types[i]).toString();
			if (!java.equals("minecraft:" + names.get(i))) {
				throw new IllegalStateException("Particle type " + i + " is " + java + " in Java, minecraft:" + names.get(i) + " in Rust");
			}
		}
		return types;
	}

	@SuppressWarnings("unchecked")
	public static ParticleOptions project(Object value, NativeData.Context context) {
		NativeData.Variant variant = (NativeData.Variant)value;
		ParticleType<?> type = TYPES[variant.index()];
		Object[] f = variant.fields();
		if (type instanceof SimpleParticleType simple) {
			return simple;
		}
		if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER || type == ParticleTypes.FALLING_DUST
			|| type == ParticleTypes.DUST_PILLAR || type == ParticleTypes.BLOCK_CRUMBLE) {
			return new BlockParticleOption((ParticleType<BlockParticleOption>)type, NativeValueTypes.blockState(f[0]));
		}
		if (type == ParticleTypes.BULLET_HOLE) {
			return new TaczBulletHoleParticleOptions(
				NativeData.named(Direction.class, f[0]), NativeValueTypes.blockPos(f[1]), (String)f[2], (String)f[3], (String)f[4]
			);
		}
		if (type == ParticleTypes.DRAGON_BREATH) {
			return PowerParticleOption.create((ParticleType<PowerParticleOption>)type, (Float)f[0]);
		}
		if (type == ParticleTypes.DUST) {
			return new DustParticleOptions(NativeValueTypes.color(f[0]), (Float)f[1]);
		}
		if (type == ParticleTypes.DUST_COLOR_TRANSITION) {
			return new DustColorTransitionOptions(NativeValueTypes.color(f[0]), NativeValueTypes.color(f[1]), (Float)f[2]);
		}
		if (type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT) {
			return SpellParticleOption.create((ParticleType<SpellParticleOption>)type, NativeValueTypes.color(f[0]), (Float)f[1]);
		}
		if (type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.TINTED_LEAVES || type == ParticleTypes.FLASH) {
			return ColorParticleOption.create((ParticleType<ColorParticleOption>)type, NativeValueTypes.color(f[0]));
		}
		if (type == ParticleTypes.SCULK_CHARGE) {
			return new SculkChargeParticleOptions((Float)f[0]);
		}
		if (type == ParticleTypes.ITEM) {
			return new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.NativeItemStacks.projectSingle(((NativeData.Variant)f[0]).fields()[0], context));
		}
		if (type == ParticleTypes.VIBRATION) {
			return new VibrationParticleOption(PositionSource.project(f[0]), (Integer)f[1]);
		}
		if (type == ParticleTypes.TRAIL) {
			return new TrailParticleOption(NativeValueTypes.vec3(f[0]), NativeValueTypes.color(f[1]), (Integer)f[2]);
		}
		if (type == ParticleTypes.SHRIEK) {
			return new ShriekParticleOption((Integer)f[0]);
		}
		throw new IllegalStateException("No projection for particle type " + BuiltInRegistries.PARTICLE_TYPE.getKey(type));
	}

	public static Object unproject(ParticleOptions options, NativeData.Builder builder) {
		int index = BuiltInRegistries.PARTICLE_TYPE.getId(options.getType());
		Object[] fields = switch (options) {
			case SimpleParticleType simple -> new Object[0];
			case BlockParticleOption o -> new Object[]{NativeValueTypes.blockStateOrName(o.getState())};
			case TaczBulletHoleParticleOptions o -> new Object[]{o.direction().getSerializedName(), NativeValueTypes.blockPos(o.pos()), o.ammoId(), o.gunId(), o.gunDisplayId()};
			case PowerParticleOption o -> new Object[]{o.getPower()};
			case DustParticleOptions o -> new Object[]{NativeValueTypes.color(o.color()), o.getScale()};
			case DustColorTransitionOptions o -> new Object[]{NativeValueTypes.color(o.fromColor()), NativeValueTypes.color(o.toColor()), o.getScale()};
			case SpellParticleOption o -> new Object[]{NativeValueTypes.color(o.color()), o.getPower()};
			case ColorParticleOption o -> new Object[]{NativeValueTypes.color(o.color())};
			case SculkChargeParticleOptions o -> new Object[]{o.roll()};
			case ItemParticleOption o -> new Object[]{new NativeData.Variant(0, new Object[]{net.minecraft.world.item.NativeItemStacks.unprojectSingle(o.getItem(), builder)})};
			case VibrationParticleOption o -> new Object[]{PositionSource.unproject(o.getDestination()), o.getArrivalInTicks()};
			case TrailParticleOption o -> new Object[]{NativeValueTypes.vec3(o.target()), NativeValueTypes.color(o.color()), o.duration()};
			case ShriekParticleOption o -> new Object[]{o.getDelay()};
			default -> throw new IllegalArgumentException("Unknown particle options " + options);
		};
		return new NativeData.Variant(index, fields);
	}
}
