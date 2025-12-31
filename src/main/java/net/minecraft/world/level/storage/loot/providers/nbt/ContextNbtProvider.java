package net.minecraft.world.level.storage.loot.providers.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.Nullable;

public class ContextNbtProvider implements NbtProvider {
	private static final ExtraCodecs.LateBoundIdMapper<String, ContextNbtProvider.Source<?>> SOURCES = new ExtraCodecs.LateBoundIdMapper<>();
	private static final Codec<ContextNbtProvider.Source<?>> GETTER_CODEC;
	public static final MapCodec<ContextNbtProvider> MAP_CODEC;
	public static final Codec<ContextNbtProvider> INLINE_CODEC;
	private final ContextNbtProvider.Source<?> source;

	private ContextNbtProvider(ContextNbtProvider.Source<?> source) {
		this.source = source;
	}

	@Override
	public LootNbtProviderType getType() {
		return NbtProviders.CONTEXT;
	}

	@Nullable
	@Override
	public Tag get(LootContext lootContext) {
		return this.source.get(lootContext);
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(this.source.contextParam());
	}

	public static NbtProvider forContextEntity(LootContext.EntityTarget entityTarget) {
		return new ContextNbtProvider(new ContextNbtProvider.EntitySource(entityTarget.getParam()));
	}

	static {
		for (LootContext.EntityTarget entityTarget : LootContext.EntityTarget.values()) {
			SOURCES.put(entityTarget.getSerializedName(), new ContextNbtProvider.EntitySource(entityTarget.getParam()));
		}

		for (LootContext.BlockEntityTarget blockEntityTarget : LootContext.BlockEntityTarget.values()) {
			SOURCES.put(blockEntityTarget.getSerializedName(), new ContextNbtProvider.BlockEntitySource(blockEntityTarget.getParam()));
		}

		GETTER_CODEC = SOURCES.codec(Codec.STRING);
		MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(GETTER_CODEC.fieldOf("target").forGetter(contextNbtProvider -> contextNbtProvider.source))
				.apply(instance, ContextNbtProvider::new)
		);
		INLINE_CODEC = GETTER_CODEC.xmap(ContextNbtProvider::new, contextNbtProvider -> contextNbtProvider.source);
	}

	record BlockEntitySource(ContextKey<? extends BlockEntity> contextParam) implements ContextNbtProvider.Source<BlockEntity> {
		public Tag get(BlockEntity blockEntity) {
			return blockEntity.saveWithFullMetadata(blockEntity.getLevel().registryAccess());
		}
	}

	record EntitySource(ContextKey<? extends Entity> contextParam) implements ContextNbtProvider.Source<Entity> {
		public Tag get(Entity entity) {
			return NbtPredicate.getEntityTagToCompare(entity);
		}
	}

	interface Source<T> {
		ContextKey<? extends T> contextParam();

		@Nullable
		Tag get(T object);

		@Nullable
		default Tag get(LootContext lootContext) {
			T object = lootContext.getOptionalParameter((ContextKey<T>)this.contextParam());
			return object != null ? this.get(object) : null;
		}
	}
}
