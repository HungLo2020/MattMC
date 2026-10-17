package net.minecraft.world.level.gameevent;

import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValueTypes;
import net.minecraft.resources.NativeValues;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface PositionSource {
	/** Rust decodes, validates and encodes sources (`content/data/values.rs`). */
	NativeValues.ValueCodec<PositionSource> CODEC = NativeValues.codec(NativeValues.Kind.POSITION_SOURCE, PositionSource::project, PositionSource::unproject);
	StreamCodec<RegistryFriendlyByteBuf, PositionSource> STREAM_CODEC = ByteBufCodecs.registry(Registries.POSITION_SOURCE_TYPE)
		.dispatch(PositionSource::getType, PositionSourceType::streamCodec);

	/** A source from its decoded value. */
	static PositionSource project(Object value) {
		NativeData.Variant variant = (NativeData.Variant)value;
		Object[] f = variant.fields();
		return variant.index() == 0
			? new BlockPositionSource(NativeValueTypes.blockPos(f[0]))
			: EntityPositionSource.ofUuid(NativeValueTypes.uuid(f[0]), (Float)f[1]);
	}

	/** A source's decoded value. */
	static Object unproject(PositionSource source) {
		return switch (source) {
			case BlockPositionSource block -> new NativeData.Variant(0, new Object[]{NativeValueTypes.blockPos(block.pos())});
			case EntityPositionSource entity -> new NativeData.Variant(1, new Object[]{NativeValueTypes.uuid(entity.getUuid()), entity.yOffset()});
			default -> throw new IllegalArgumentException("Unknown position source " + source);
		};
	}

	Optional<Vec3> getPosition(Level level);

	PositionSourceType<? extends PositionSource> getType();
}
