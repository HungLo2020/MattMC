package net.minecraft.commands.synchronization;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class SuggestionProviders {
	private static final Map<ResourceLocation, SuggestionProvider<SharedSuggestionProvider>> PROVIDERS_BY_NAME = new HashMap();
	private static final ResourceLocation ID_ASK_SERVER = ResourceLocation.withDefaultNamespace("ask_server");
	public static final SuggestionProvider<SharedSuggestionProvider> ASK_SERVER = register(
		ID_ASK_SERVER, (commandContext, suggestionsBuilder) -> commandContext.getSource().customSuggestion(commandContext)
	);
	public static final SuggestionProvider<SharedSuggestionProvider> AVAILABLE_SOUNDS = register(
		ResourceLocation.withDefaultNamespace("available_sounds"),
		(commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(commandContext.getSource().getAvailableSounds(), suggestionsBuilder)
	);
	public static final SuggestionProvider<SharedSuggestionProvider> SUMMONABLE_ENTITIES = register(
		ResourceLocation.withDefaultNamespace("summonable_entities"),
		(commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(
			BuiltInRegistries.ENTITY_TYPE.stream().filter(entityType -> entityType.isEnabled(commandContext.getSource().enabledFeatures()) && entityType.canSummon()),
			suggestionsBuilder,
			EntityType::getKey,
			EntityType::getDescription
		)
	);

	public static <S extends SharedSuggestionProvider> SuggestionProvider<S> register(
		ResourceLocation resourceLocation, SuggestionProvider<SharedSuggestionProvider> suggestionProvider
	) {
		SuggestionProvider<SharedSuggestionProvider> suggestionProvider2 = (SuggestionProvider<SharedSuggestionProvider>)PROVIDERS_BY_NAME.putIfAbsent(
			resourceLocation, suggestionProvider
		);
		if (suggestionProvider2 != null) {
			throw new IllegalArgumentException("A command suggestion provider is already registered with the name '" + resourceLocation + "'");
		} else {
			return new SuggestionProviders.RegisteredSuggestion(resourceLocation, suggestionProvider);
		}
	}

	public static <S extends SharedSuggestionProvider> SuggestionProvider<S> cast(SuggestionProvider<SharedSuggestionProvider> suggestionProvider) {
		return (SuggestionProvider<S>)suggestionProvider;
	}

	public static <S extends SharedSuggestionProvider> SuggestionProvider<S> getProvider(ResourceLocation resourceLocation) {
		return cast((SuggestionProvider<SharedSuggestionProvider>)PROVIDERS_BY_NAME.getOrDefault(resourceLocation, ASK_SERVER));
	}

	public static ResourceLocation getName(SuggestionProvider<?> suggestionProvider) {
		return suggestionProvider instanceof SuggestionProviders.RegisteredSuggestion registeredSuggestion ? registeredSuggestion.name : ID_ASK_SERVER;
	}

	record RegisteredSuggestion(ResourceLocation name, SuggestionProvider<SharedSuggestionProvider> delegate)
		implements SuggestionProvider<SharedSuggestionProvider> {

		@Override
		public CompletableFuture<Suggestions> getSuggestions(CommandContext<SharedSuggestionProvider> commandContext, SuggestionsBuilder suggestionsBuilder) throws CommandSyntaxException {
			return this.delegate.getSuggestions(commandContext, suggestionsBuilder);
		}
	}
}
