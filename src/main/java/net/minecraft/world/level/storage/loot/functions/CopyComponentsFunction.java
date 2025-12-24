package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.Nullable;

public class CopyComponentsFunction extends LootItemConditionalFunction {
	private static final ExtraCodecs.LateBoundIdMapper<String, CopyComponentsFunction.Source<?>> SOURCES = new ExtraCodecs.LateBoundIdMapper<>();
	public static final MapCodec<CopyComponentsFunction> CODEC;
	private final CopyComponentsFunction.Source<?> source;
	private final Optional<List<DataComponentType<?>>> include;
	private final Optional<List<DataComponentType<?>>> exclude;
	private final Predicate<DataComponentType<?>> bakedPredicate;

	CopyComponentsFunction(
		List<LootItemCondition> list,
		CopyComponentsFunction.Source<?> source,
		Optional<List<DataComponentType<?>>> optional,
		Optional<List<DataComponentType<?>>> optional2
	) {
		super(list);
		this.source = source;
		this.include = optional.map(List::copyOf);
		this.exclude = optional2.map(List::copyOf);
		List<Predicate<DataComponentType<?>>> list2 = new ArrayList(2);
		optional2.ifPresent(list2x -> list2.add((Predicate)dataComponentType -> !list2x.contains(dataComponentType)));
		optional.ifPresent(list2x -> list2.add(list2x::contains));
		this.bakedPredicate = Util.allOf(list2);
	}

	@Override
	public LootItemFunctionType<CopyComponentsFunction> getType() {
		return LootItemFunctions.COPY_COMPONENTS;
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(this.source.contextParam());
	}

	@Override
	public ItemStack run(ItemStack itemStack, LootContext lootContext) {
		DataComponentGetter dataComponentGetter = this.source.get(lootContext);
		if (dataComponentGetter != null) {
			if (dataComponentGetter instanceof DataComponentMap dataComponentMap) {
				itemStack.applyComponents(dataComponentMap.filter(this.bakedPredicate));
			} else {
				Collection<DataComponentType<?>> collection = (Collection<DataComponentType<?>>)this.exclude.orElse(List.of());
				Stream<DataComponentType<?>> stream = this.include.map(Collection::stream).orElse(BuiltInRegistries.DATA_COMPONENT_TYPE.listElements().map(Holder::value));
				stream.forEach(dataComponentType -> {
						if (!collection.contains(dataComponentType)) {
							TypedDataComponent<?> typedDataComponent = dataComponentGetter.getTyped(dataComponentType);
							if (typedDataComponent != null) {
								itemStack.set(typedDataComponent);
							}
						}
					});
			}
		}

		return itemStack;
	}

	public static CopyComponentsFunction.Builder copyComponentsFromEntity(ContextKey<? extends Entity> contextKey) {
		return new CopyComponentsFunction.Builder(new CopyComponentsFunction.EntitySource(contextKey));
	}

	public static CopyComponentsFunction.Builder copyComponentsFromBlockEntity(ContextKey<? extends BlockEntity> contextKey) {
		return new CopyComponentsFunction.Builder(new CopyComponentsFunction.BlockEntitySource(contextKey));
	}

	static {
		for (LootContext.EntityTarget entityTarget : LootContext.EntityTarget.values()) {
			SOURCES.put(entityTarget.getSerializedName(), new CopyComponentsFunction.EntitySource(entityTarget.getParam()));
		}

		for (LootContext.BlockEntityTarget blockEntityTarget : LootContext.BlockEntityTarget.values()) {
			SOURCES.put(blockEntityTarget.getSerializedName(), new CopyComponentsFunction.BlockEntitySource(blockEntityTarget.getParam()));
		}

		for (LootContext.ItemStackTarget itemStackTarget : LootContext.ItemStackTarget.values()) {
			SOURCES.put(itemStackTarget.getSerializedName(), new CopyComponentsFunction.ItemStackSource(itemStackTarget.getParam()));
		}

		CODEC = RecordCodecBuilder.mapCodec(
			instance -> commonFields(instance)
				.<CopyComponentsFunction.Source<?>, Optional<List<DataComponentType<?>>>, Optional<List<DataComponentType<?>>>>and(
					instance.group(
						SOURCES.codec(Codec.STRING).fieldOf("source").forGetter(copyComponentsFunction -> copyComponentsFunction.source),
						DataComponentType.CODEC.listOf().optionalFieldOf("include").forGetter(copyComponentsFunction -> copyComponentsFunction.include),
						DataComponentType.CODEC.listOf().optionalFieldOf("exclude").forGetter(copyComponentsFunction -> copyComponentsFunction.exclude)
					)
				)
				.apply(instance, CopyComponentsFunction::new)
		);
	}

	record BlockEntitySource(ContextKey<? extends BlockEntity> contextParam) implements CopyComponentsFunction.Source<BlockEntity> {
		public DataComponentGetter get(BlockEntity blockEntity) {
			return blockEntity.collectComponents();
		}
	}

	public static class Builder extends LootItemConditionalFunction.Builder<CopyComponentsFunction.Builder> {
		private final CopyComponentsFunction.Source<?> source;
		private Optional<ImmutableList.Builder<DataComponentType<?>>> include = Optional.empty();
		private Optional<ImmutableList.Builder<DataComponentType<?>>> exclude = Optional.empty();

		Builder(CopyComponentsFunction.Source<?> source) {
			this.source = source;
		}

		public CopyComponentsFunction.Builder include(DataComponentType<?> dataComponentType) {
			if (this.include.isEmpty()) {
				this.include = Optional.of(ImmutableList.builder());
			}

			((ImmutableList.Builder)this.include.get()).add(dataComponentType);
			return this;
		}

		public CopyComponentsFunction.Builder exclude(DataComponentType<?> dataComponentType) {
			if (this.exclude.isEmpty()) {
				this.exclude = Optional.of(ImmutableList.builder());
			}

			((ImmutableList.Builder)this.exclude.get()).add(dataComponentType);
			return this;
		}

		protected CopyComponentsFunction.Builder getThis() {
			return this;
		}

		@Override
		public LootItemFunction build() {
			return new CopyComponentsFunction(
				this.getConditions(), this.source, this.include.map(ImmutableList.Builder::build), this.exclude.map(ImmutableList.Builder::build)
			);
		}
	}

	record EntitySource(ContextKey<? extends Entity> contextParam) implements CopyComponentsFunction.Source<Entity> {
		public DataComponentGetter get(Entity entity) {
			return entity;
		}
	}

	record ItemStackSource(ContextKey<? extends ItemStack> contextParam) implements CopyComponentsFunction.Source<ItemStack> {
		public DataComponentGetter get(ItemStack itemStack) {
			return itemStack.getComponents();
		}
	}

	public interface Source<T> {
		ContextKey<? extends T> contextParam();

		DataComponentGetter get(T object);

		@Nullable
		default DataComponentGetter get(LootContext lootContext) {
			T object = lootContext.getOptionalParameter((ContextKey<T>)this.contextParam());
			return object != null ? this.get(object) : null;
		}
	}
}
