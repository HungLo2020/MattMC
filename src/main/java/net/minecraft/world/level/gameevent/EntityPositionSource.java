package net.minecraft.world.level.gameevent;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityPositionSource implements PositionSource {
	public static final StreamCodec<ByteBuf, EntityPositionSource> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		EntityPositionSource::getId,
		ByteBufCodecs.FLOAT,
		entityPositionSource -> entityPositionSource.yOffset,
		(integer, float_) -> new EntityPositionSource(Either.right(Either.right(integer)), float_)
	);
	private Either<Entity, Either<UUID, Integer>> entityOrUuidOrId;
	private final float yOffset;

	public EntityPositionSource(Entity entity, float f) {
		this(Either.left(entity), f);
	}

	/** A source naming its entity by UUID, as saves hold it. */
	public static EntityPositionSource ofUuid(UUID uuid, float yOffset) {
		return new EntityPositionSource(Either.right(Either.left(uuid)), yOffset);
	}

	public float yOffset() {
		return this.yOffset;
	}

	private EntityPositionSource(Either<Entity, Either<UUID, Integer>> either, float f) {
		this.entityOrUuidOrId = either;
		this.yOffset = f;
	}

	@Override
	public Optional<Vec3> getPosition(Level level) {
		if (this.entityOrUuidOrId.left().isEmpty()) {
			this.resolveEntity(level);
		}

		return this.entityOrUuidOrId.left().map(entity -> entity.position().add(0.0, this.yOffset, 0.0));
	}

	private void resolveEntity(Level level) {
		this.entityOrUuidOrId
			.<Optional<Entity>>map(
				Optional::of,
				either -> Optional.ofNullable((Entity)either.map(uUID -> level instanceof ServerLevel serverLevel ? serverLevel.getEntity(uUID) : null, level::getEntity))
			)
			.ifPresent(entity -> this.entityOrUuidOrId = Either.<Entity, Either<UUID, Integer>>left(entity));
	}

	public UUID getUuid() {
		return this.entityOrUuidOrId.map(Entity::getUUID, either -> either.map(Function.identity(), integer -> {
			throw new RuntimeException("Unable to get entityId from uuid");
		}));
	}

	private int getId() {
		return this.entityOrUuidOrId.<Integer>map(Entity::getId, either -> either.map(uUID -> {
			throw new IllegalStateException("Unable to get entityId from uuid");
		}, Function.identity()));
	}

	@Override
	public PositionSourceType<EntityPositionSource> getType() {
		return PositionSourceType.ENTITY;
	}

	public static class Type implements PositionSourceType<EntityPositionSource> {
		@Override
		@SuppressWarnings("unchecked")
		public MapCodec<EntityPositionSource> codec() {
			return (MapCodec<EntityPositionSource>)(MapCodec<?>)PositionSource.CODEC.dispatchCase("minecraft:entity");
		}

		@Override
		public StreamCodec<ByteBuf, EntityPositionSource> streamCodec() {
			return EntityPositionSource.STREAM_CODEC;
		}
	}
}
