package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemPredicateArgument extends ParserBasedArgument<ItemPredicateArgument.Result> {
	private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");
	static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("argument.item.id.invalid", object)
	);
	static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("arguments.item.tag.unknown", object)
	);
	static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("arguments.item.component.unknown", object)
	);
	static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
		(object, object2) -> Component.translatableEscape("arguments.item.component.malformed", object, object2)
	);
	static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("arguments.item.predicate.unknown", object)
	);
	static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE = new Dynamic2CommandExceptionType(
		(object, object2) -> Component.translatableEscape("arguments.item.predicate.malformed", object, object2)
	);
	private static final ResourceLocation COUNT_ID = ResourceLocation.withDefaultNamespace("count");
	static final Map<ResourceLocation, ItemPredicateArgument.ComponentWrapper> PSEUDO_COMPONENTS = (Map<ResourceLocation, ItemPredicateArgument.ComponentWrapper>)Stream.of(
			new ItemPredicateArgument.ComponentWrapper(COUNT_ID, itemStack -> true, MinMaxBounds.Ints.CODEC.map(ints -> itemStack -> ints.matches(itemStack.getCount())))
		)
		.collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.ComponentWrapper::id, componentWrapper -> componentWrapper));
	static final Map<ResourceLocation, ItemPredicateArgument.PredicateWrapper> PSEUDO_PREDICATES = (Map<ResourceLocation, ItemPredicateArgument.PredicateWrapper>)Stream.of(
			new ItemPredicateArgument.PredicateWrapper(COUNT_ID, MinMaxBounds.Ints.CODEC.map(ints -> itemStack -> ints.matches(itemStack.getCount())))
		)
		.collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.PredicateWrapper::id, predicateWrapper -> predicateWrapper));

	public ItemPredicateArgument(CommandBuildContext commandBuildContext) {
		super(ComponentPredicateParser.createGrammar(new ItemPredicateArgument.Context(commandBuildContext)).mapResult(list -> Util.allOf(list)::test));
	}

	public static ItemPredicateArgument itemPredicate(CommandBuildContext commandBuildContext) {
		return new ItemPredicateArgument(commandBuildContext);
	}

	public static ItemPredicateArgument.Result getItemPredicate(CommandContext<CommandSourceStack> commandContext, String string) {
		return commandContext.getArgument(string, ItemPredicateArgument.Result.class);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	record ComponentWrapper(ResourceLocation id, Predicate<ItemStack> presenceChecker, Decoder<? extends Predicate<ItemStack>> valueChecker) {

		public static <T> ItemPredicateArgument.ComponentWrapper create(
			ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation, DataComponentType<T> dataComponentType
		) throws CommandSyntaxException {
			Codec<T> codec = dataComponentType.codec();
			if (codec == null) {
				throw ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(immutableStringReader, resourceLocation);
			} else {
				return new ItemPredicateArgument.ComponentWrapper(resourceLocation, itemStack -> itemStack.has(dataComponentType), codec.map(object -> itemStack -> {
					T object2 = itemStack.get(dataComponentType);
					return Objects.equals(object, object2);
				}));
			}
		}

		public Predicate<ItemStack> decode(ImmutableStringReader immutableStringReader, Dynamic<?> dynamic) throws CommandSyntaxException {
			DataResult<? extends Predicate<ItemStack>> dataResult = this.valueChecker.parse(dynamic);
			return (Predicate<ItemStack>)dataResult.getOrThrow(
				string -> ItemPredicateArgument.ERROR_MALFORMED_COMPONENT.createWithContext(immutableStringReader, this.id.toString(), string)
			);
		}
	}

	static class Context
		implements ComponentPredicateParser.Context<Predicate<ItemStack>, ItemPredicateArgument.ComponentWrapper, ItemPredicateArgument.PredicateWrapper> {
		private final HolderLookup.Provider registries;
		private final HolderLookup.RegistryLookup<Item> items;
		private final HolderLookup.RegistryLookup<DataComponentType<?>> components;
		private final HolderLookup.RegistryLookup<DataComponentPredicate.Type<?>> predicates;

		Context(HolderLookup.Provider provider) {
			this.registries = provider;
			this.items = provider.lookupOrThrow(Registries.ITEM);
			this.components = provider.lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
			this.predicates = provider.lookupOrThrow(Registries.DATA_COMPONENT_PREDICATE_TYPE);
		}

		public Predicate<ItemStack> forElementType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
			Holder.Reference<Item> reference = (Holder.Reference<Item>)this.items
				.get(ResourceKey.create(Registries.ITEM, resourceLocation))
				.orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_ITEM.createWithContext(immutableStringReader, resourceLocation));
			return itemStack -> itemStack.is(reference);
		}

		public Predicate<ItemStack> forTagType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
			HolderSet<Item> holderSet = (HolderSet<Item>)this.items
				.get(TagKey.create(Registries.ITEM, resourceLocation))
				.orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_TAG.createWithContext(immutableStringReader, resourceLocation));
			return itemStack -> itemStack.is(holderSet);
		}

		public ItemPredicateArgument.ComponentWrapper lookupComponentType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
			ItemPredicateArgument.ComponentWrapper componentWrapper = (ItemPredicateArgument.ComponentWrapper)ItemPredicateArgument.PSEUDO_COMPONENTS
				.get(resourceLocation);
			if (componentWrapper != null) {
				return componentWrapper;
			} else {
				DataComponentType<?> dataComponentType = (DataComponentType<?>)this.components
					.get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, resourceLocation))
					.map(Holder::value)
					.orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(immutableStringReader, resourceLocation));
				return ItemPredicateArgument.ComponentWrapper.create(immutableStringReader, resourceLocation, dataComponentType);
			}
		}

		public Predicate<ItemStack> createComponentTest(
			ImmutableStringReader immutableStringReader, ItemPredicateArgument.ComponentWrapper componentWrapper, Dynamic<?> dynamic
		) throws CommandSyntaxException {
			return componentWrapper.decode(immutableStringReader, RegistryOps.injectRegistryContext(dynamic, this.registries));
		}

		public Predicate<ItemStack> createComponentTest(ImmutableStringReader immutableStringReader, ItemPredicateArgument.ComponentWrapper componentWrapper) {
			return componentWrapper.presenceChecker;
		}

		public ItemPredicateArgument.PredicateWrapper lookupPredicateType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
			ItemPredicateArgument.PredicateWrapper predicateWrapper = (ItemPredicateArgument.PredicateWrapper)ItemPredicateArgument.PSEUDO_PREDICATES
				.get(resourceLocation);
			return predicateWrapper != null
				? predicateWrapper
				: (ItemPredicateArgument.PredicateWrapper)this.predicates
					.get(ResourceKey.create(Registries.DATA_COMPONENT_PREDICATE_TYPE, resourceLocation))
					.map(ItemPredicateArgument.PredicateWrapper::new)
					.orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_PREDICATE.createWithContext(immutableStringReader, resourceLocation));
		}

		public Predicate<ItemStack> createPredicateTest(
			ImmutableStringReader immutableStringReader, ItemPredicateArgument.PredicateWrapper predicateWrapper, Dynamic<?> dynamic
		) throws CommandSyntaxException {
			return predicateWrapper.decode(immutableStringReader, RegistryOps.injectRegistryContext(dynamic, this.registries));
		}

		@Override
		public Stream<ResourceLocation> listElementTypes() {
			return this.items.listElementIds().map(ResourceKey::location);
		}

		@Override
		public Stream<ResourceLocation> listTagTypes() {
			return this.items.listTagIds().map(TagKey::location);
		}

		@Override
		public Stream<ResourceLocation> listComponentTypes() {
			return Stream.concat(
				ItemPredicateArgument.PSEUDO_COMPONENTS.keySet().stream(),
				this.components.listElements().filter(reference -> !((DataComponentType)reference.value()).isTransient()).map(reference -> reference.key().location())
			);
		}

		@Override
		public Stream<ResourceLocation> listPredicateTypes() {
			return Stream.concat(ItemPredicateArgument.PSEUDO_PREDICATES.keySet().stream(), this.predicates.listElementIds().map(ResourceKey::location));
		}

		public Predicate<ItemStack> negate(Predicate<ItemStack> predicate) {
			return predicate.negate();
		}

		public Predicate<ItemStack> anyOf(List<Predicate<ItemStack>> list) {
			return Util.anyOf(list);
		}
	}

	record PredicateWrapper(ResourceLocation id, Decoder<? extends Predicate<ItemStack>> type) {
		public PredicateWrapper(Holder.Reference<DataComponentPredicate.Type<?>> reference) {
			this(reference.key().location(), reference.value().codec().map(dataComponentPredicate -> dataComponentPredicate::matches));
		}

		public Predicate<ItemStack> decode(ImmutableStringReader immutableStringReader, Dynamic<?> dynamic) throws CommandSyntaxException {
			DataResult<? extends Predicate<ItemStack>> dataResult = this.type.parse(dynamic);
			return (Predicate<ItemStack>)dataResult.getOrThrow(
				string -> ItemPredicateArgument.ERROR_MALFORMED_PREDICATE.createWithContext(immutableStringReader, this.id.toString(), string)
			);
		}
	}

	public interface Result extends Predicate<ItemStack> {
	}
}
