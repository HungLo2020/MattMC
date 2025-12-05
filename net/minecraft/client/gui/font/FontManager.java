package net.minecraft.client.gui.font;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FontDescription.AtlasSprite;
import net.minecraft.network.chat.FontDescription.PlayerSprite;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;
import net.minecraft.util.DependencySorter;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class FontManager implements PreparableReloadListener, AutoCloseable {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final String FONTS_PATH = "fonts.json";
	public static final ResourceLocation MISSING_FONT = ResourceLocation.withDefaultNamespace("missing");
	private static final FileToIdConverter FONT_DEFINITIONS = FileToIdConverter.json("font");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	final FontSet missingFontSet;
	private final List<GlyphProvider> providersToClose = new ArrayList();
	private final Map<ResourceLocation, FontSet> fontSets = new HashMap();
	private final TextureManager textureManager;
	private final FontManager.CachedFontProvider anyGlyphs = new FontManager.CachedFontProvider(false);
	private final FontManager.CachedFontProvider nonFishyGlyphs = new FontManager.CachedFontProvider(true);
	private final AtlasManager atlasManager;
	private final Map<ResourceLocation, AtlasGlyphProvider> atlasProviders = new HashMap();
	final PlayerGlyphProvider playerProvider;

	public FontManager(TextureManager textureManager, AtlasManager atlasManager, PlayerSkinRenderCache playerSkinRenderCache) {
		this.textureManager = textureManager;
		this.atlasManager = atlasManager;
		this.missingFontSet = this.createFontSet(MISSING_FONT, List.of(createFallbackProvider()), Set.of());
		this.playerProvider = new PlayerGlyphProvider(playerSkinRenderCache);
	}

	private FontSet createFontSet(ResourceLocation resourceLocation, List<GlyphProvider.Conditional> list, Set<FontOption> set) {
		GlyphStitcher glyphStitcher = new GlyphStitcher(this.textureManager, resourceLocation);
		FontSet fontSet = new FontSet(glyphStitcher);
		fontSet.reload(list, set);
		return fontSet;
	}

	private static GlyphProvider.Conditional createFallbackProvider() {
		return new GlyphProvider.Conditional(new AllMissingGlyphProvider(), FontOption.Filter.ALWAYS_PASS);
	}

	public CompletableFuture<Void> reload(SharedState sharedState, Executor executor, PreparationBarrier preparationBarrier, Executor executor2) {
		return this.prepare(sharedState.resourceManager(), executor)
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(preparation -> this.apply(preparation, Profiler.get()), executor2);
	}

	private CompletableFuture<FontManager.Preparation> prepare(ResourceManager resourceManager, Executor executor) {
		List<CompletableFuture<FontManager.UnresolvedBuilderBundle>> list = new ArrayList();

		for (Entry<ResourceLocation, List<Resource>> entry : FONT_DEFINITIONS.listMatchingResourceStacks(resourceManager).entrySet()) {
			ResourceLocation resourceLocation = FONT_DEFINITIONS.fileToId((ResourceLocation)entry.getKey());
			list.add(CompletableFuture.supplyAsync(() -> {
				List<Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional>> listx = loadResourceStack((List<Resource>)entry.getValue(), resourceLocation);
				FontManager.UnresolvedBuilderBundle unresolvedBuilderBundle = new FontManager.UnresolvedBuilderBundle(resourceLocation);

				for (Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional> pair : listx) {
					FontManager.BuilderId builderId = pair.getFirst();
					FontOption.Filter filter = pair.getSecond().filter();
					pair.getSecond().definition().unpack().ifLeft(loader -> {
						CompletableFuture<Optional<GlyphProvider>> completableFuture = this.safeLoad(builderId, loader, resourceManager, executor);
						unresolvedBuilderBundle.add(builderId, filter, completableFuture);
					}).ifRight(reference -> unresolvedBuilderBundle.add(builderId, filter, reference));
				}

				return unresolvedBuilderBundle;
			}, executor));
		}

		return Util.sequence(list)
			.thenCompose(
				listx -> {
					List<CompletableFuture<Optional<GlyphProvider>>> list2 = (List<CompletableFuture<Optional<GlyphProvider>>>)listx.stream()
						.flatMap(FontManager.UnresolvedBuilderBundle::listBuilders)
						.collect(Util.toMutableList());
					GlyphProvider.Conditional conditional = createFallbackProvider();
					list2.add(CompletableFuture.completedFuture(Optional.of(conditional.provider())));
					return Util.sequence(list2)
						.thenCompose(
							list2x -> {
								Map<ResourceLocation, List<GlyphProvider.Conditional>> map = this.resolveProviders(listx);
								CompletableFuture<?>[] completableFutures = (CompletableFuture<?>[])map.values()
									.stream()
									.map(listxxx -> CompletableFuture.runAsync(() -> this.finalizeProviderLoading(listxxx, conditional), executor))
									.toArray(CompletableFuture[]::new);
								return CompletableFuture.allOf(completableFutures).thenApply(void_ -> {
									List<GlyphProvider> list2xx = list2x.stream().flatMap(Optional::stream).toList();
									return new FontManager.Preparation(map, list2xx);
								});
							}
						);
				}
			);
	}

	private CompletableFuture<Optional<GlyphProvider>> safeLoad(
		FontManager.BuilderId builderId, GlyphProviderDefinition.Loader loader, ResourceManager resourceManager, Executor executor
	) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return Optional.of(loader.load(resourceManager));
			} catch (Exception var4) {
				LOGGER.warn("Failed to load builder {}, rejecting", builderId, var4);
				return Optional.empty();
			}
		}, executor);
	}

	private Map<ResourceLocation, List<GlyphProvider.Conditional>> resolveProviders(List<FontManager.UnresolvedBuilderBundle> list) {
		Map<ResourceLocation, List<GlyphProvider.Conditional>> map = new HashMap();
		DependencySorter<ResourceLocation, FontManager.UnresolvedBuilderBundle> dependencySorter = new DependencySorter();
		list.forEach(unresolvedBuilderBundle -> dependencySorter.addEntry(unresolvedBuilderBundle.fontId, unresolvedBuilderBundle));
		dependencySorter.orderByDependencies(
			(resourceLocation, unresolvedBuilderBundle) -> unresolvedBuilderBundle.resolve(map::get).ifPresent(listx -> map.put(resourceLocation, listx))
		);
		return map;
	}

	private void finalizeProviderLoading(List<GlyphProvider.Conditional> list, GlyphProvider.Conditional conditional) {
		list.add(0, conditional);
		IntSet intSet = new IntOpenHashSet();

		for (GlyphProvider.Conditional conditional2 : list) {
			intSet.addAll(conditional2.provider().getSupportedGlyphs());
		}

		intSet.forEach(i -> {
			if (i != 32) {
				for (GlyphProvider.Conditional conditionalx : Lists.reverse(list)) {
					if (conditionalx.provider().getGlyph(i) != null) {
						break;
					}
				}
			}
		});
	}

	private static Set<FontOption> getFontOptions(Options options) {
		Set<FontOption> set = EnumSet.noneOf(FontOption.class);
		if (options.forceUnicodeFont().get()) {
			set.add(FontOption.UNIFORM);
		}

		if (options.japaneseGlyphVariants().get()) {
			set.add(FontOption.JAPANESE_VARIANTS);
		}

		return set;
	}

	private void apply(FontManager.Preparation preparation, ProfilerFiller profilerFiller) {
		profilerFiller.push("closing");
		this.anyGlyphs.invalidate();
		this.nonFishyGlyphs.invalidate();
		this.fontSets.values().forEach(FontSet::close);
		this.fontSets.clear();
		this.providersToClose.forEach(GlyphProvider::close);
		this.providersToClose.clear();
		Set<FontOption> set = getFontOptions(Minecraft.getInstance().options);
		profilerFiller.popPush("reloading");
		preparation.fontSets()
			.forEach((resourceLocation, list) -> this.fontSets.put(resourceLocation, this.createFontSet(resourceLocation, Lists.reverse(list), set)));
		this.providersToClose.addAll(preparation.allProviders);
		profilerFiller.pop();
		if (!this.fontSets.containsKey(Minecraft.DEFAULT_FONT)) {
			throw new IllegalStateException("Default font failed to load");
		} else {
			this.atlasProviders.clear();
			this.atlasManager.forEach((resourceLocation, textureAtlas) -> this.atlasProviders.put(resourceLocation, new AtlasGlyphProvider(textureAtlas)));
		}
	}

	public void updateOptions(Options options) {
		Set<FontOption> set = getFontOptions(options);

		for (FontSet fontSet : this.fontSets.values()) {
			fontSet.reload(set);
		}
	}

	private static List<Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional>> loadResourceStack(List<Resource> list, ResourceLocation resourceLocation) {
		List<Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional>> list2 = new ArrayList();

		for (Resource resource : list) {
			try {
				Reader reader = resource.openAsReader();

				try {
					JsonElement jsonElement = GSON.fromJson(reader, JsonElement.class);
					FontManager.FontDefinitionFile fontDefinitionFile = FontManager.FontDefinitionFile.CODEC
						.parse(JsonOps.INSTANCE, jsonElement)
						.getOrThrow(JsonParseException::new);
					List<GlyphProviderDefinition.Conditional> list3 = fontDefinitionFile.providers;

					for (int i = list3.size() - 1; i >= 0; i--) {
						FontManager.BuilderId builderId = new FontManager.BuilderId(resourceLocation, resource.sourcePackId(), i);
						list2.add(Pair.of(builderId, (GlyphProviderDefinition.Conditional)list3.get(i)));
					}
				} catch (Throwable var12) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var11) {
							var12.addSuppressed(var11);
						}
					}

					throw var12;
				}

				if (reader != null) {
					reader.close();
				}
			} catch (Exception var13) {
				LOGGER.warn("Unable to load font '{}' in {} in resourcepack: '{}'", resourceLocation, "fonts.json", resource.sourcePackId(), var13);
			}
		}

		return list2;
	}

	public Font createFont() {
		return new Font(this.anyGlyphs);
	}

	public Font createFontFilterFishy() {
		return new Font(this.nonFishyGlyphs);
	}

	FontSet getFontSetRaw(ResourceLocation resourceLocation) {
		return (FontSet)this.fontSets.getOrDefault(resourceLocation, this.missingFontSet);
	}

	GlyphSource getSpriteFont(AtlasSprite atlasSprite) {
		AtlasGlyphProvider atlasGlyphProvider = (AtlasGlyphProvider)this.atlasProviders.get(atlasSprite.atlasId());
		return atlasGlyphProvider == null ? this.missingFontSet.source(false) : atlasGlyphProvider.sourceForSprite(atlasSprite.spriteId());
	}

	public void close() {
		this.anyGlyphs.close();
		this.nonFishyGlyphs.close();
		this.fontSets.values().forEach(FontSet::close);
		this.providersToClose.forEach(GlyphProvider::close);
		this.missingFontSet.close();
	}

	@Environment(EnvType.CLIENT)
	record BuilderId(ResourceLocation fontId, String pack, int index) {
		public String toString() {
			return "(" + this.fontId + ": builder #" + this.index + " from pack " + this.pack + ")";
		}
	}

	@Environment(EnvType.CLIENT)
	record BuilderResult(FontManager.BuilderId id, FontOption.Filter filter, Either<CompletableFuture<Optional<GlyphProvider>>, ResourceLocation> result) {

		public Optional<List<GlyphProvider.Conditional>> resolve(Function<ResourceLocation, List<GlyphProvider.Conditional>> function) {
			return this.result
				.map(
					completableFuture -> ((Optional)completableFuture.join()).map(glyphProvider -> List.of(new GlyphProvider.Conditional(glyphProvider, this.filter))),
					resourceLocation -> {
						List<GlyphProvider.Conditional> list = (List<GlyphProvider.Conditional>)function.apply(resourceLocation);
						if (list == null) {
							FontManager.LOGGER
								.warn("Can't find font {} referenced by builder {}, either because it's missing, failed to load or is part of loading cycle", resourceLocation, this.id);
							return Optional.empty();
						} else {
							return Optional.of(list.stream().map(this::mergeFilters).toList());
						}
					}
				);
		}

		private GlyphProvider.Conditional mergeFilters(GlyphProvider.Conditional conditional) {
			return new GlyphProvider.Conditional(conditional.provider(), this.filter.merge(conditional.filter()));
		}
	}

	@Environment(EnvType.CLIENT)
	class CachedFontProvider implements Font.Provider, AutoCloseable {
		private final boolean nonFishyOnly;
		@Nullable
		private volatile FontManager.CachedFontProvider.CachedEntry lastEntry;
		@Nullable
		private volatile EffectGlyph whiteGlyph;

		CachedFontProvider(final boolean bl) {
			this.nonFishyOnly = bl;
		}

		public void invalidate() {
			this.lastEntry = null;
			this.whiteGlyph = null;
		}

		public void close() {
			this.invalidate();
		}

		private GlyphSource getGlyphSource(FontDescription fontDescription) {
			return switch (fontDescription) {
				case net.minecraft.network.chat.FontDescription.Resource resource -> FontManager.this.getFontSetRaw(resource.id()).source(this.nonFishyOnly);
				case AtlasSprite atlasSprite -> FontManager.this.getSpriteFont(atlasSprite);
				case PlayerSprite playerSprite -> FontManager.this.playerProvider.sourceForPlayer(playerSprite);
				default -> FontManager.this.missingFontSet.source(this.nonFishyOnly);
			};
		}

		@Override
		public GlyphSource glyphs(FontDescription fontDescription) {
			FontManager.CachedFontProvider.CachedEntry cachedEntry = this.lastEntry;
			if (cachedEntry != null && fontDescription.equals(cachedEntry.description)) {
				return cachedEntry.source;
			} else {
				GlyphSource glyphSource = this.getGlyphSource(fontDescription);
				this.lastEntry = new FontManager.CachedFontProvider.CachedEntry(fontDescription, glyphSource);
				return glyphSource;
			}
		}

		@Override
		public EffectGlyph effect() {
			EffectGlyph effectGlyph = this.whiteGlyph;
			if (effectGlyph == null) {
				effectGlyph = FontManager.this.getFontSetRaw(FontDescription.DEFAULT.id()).whiteGlyph();
				this.whiteGlyph = effectGlyph;
			}

			return effectGlyph;
		}

		@Environment(EnvType.CLIENT)
		record CachedEntry(FontDescription description, GlyphSource source) {
		}
	}

	@Environment(EnvType.CLIENT)
	record FontDefinitionFile(List<GlyphProviderDefinition.Conditional> providers) {
		public static final Codec<FontManager.FontDefinitionFile> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(GlyphProviderDefinition.Conditional.CODEC.listOf().fieldOf("providers").forGetter(FontManager.FontDefinitionFile::providers))
				.apply(instance, FontManager.FontDefinitionFile::new)
		);
	}

	@Environment(EnvType.CLIENT)
	record Preparation(Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets, List<GlyphProvider> allProviders) {
	}

	@Environment(EnvType.CLIENT)
	record UnresolvedBuilderBundle(ResourceLocation fontId, List<FontManager.BuilderResult> builders, Set<ResourceLocation> dependencies)
		implements net.minecraft.util.DependencySorter.Entry<ResourceLocation> {

		public UnresolvedBuilderBundle(ResourceLocation resourceLocation) {
			this(resourceLocation, new ArrayList(), new HashSet());
		}

		public void add(FontManager.BuilderId builderId, FontOption.Filter filter, GlyphProviderDefinition.Reference reference) {
			this.builders.add(new FontManager.BuilderResult(builderId, filter, Either.right(reference.id())));
			this.dependencies.add(reference.id());
		}

		public void add(FontManager.BuilderId builderId, FontOption.Filter filter, CompletableFuture<Optional<GlyphProvider>> completableFuture) {
			this.builders.add(new FontManager.BuilderResult(builderId, filter, Either.left(completableFuture)));
		}

		private Stream<CompletableFuture<Optional<GlyphProvider>>> listBuilders() {
			return this.builders.stream().flatMap(builderResult -> builderResult.result.left().stream());
		}

		public Optional<List<GlyphProvider.Conditional>> resolve(Function<ResourceLocation, List<GlyphProvider.Conditional>> function) {
			List<GlyphProvider.Conditional> list = new ArrayList();

			for (FontManager.BuilderResult builderResult : this.builders) {
				Optional<List<GlyphProvider.Conditional>> optional = builderResult.resolve(function);
				if (!optional.isPresent()) {
					return Optional.empty();
				}

				list.addAll((Collection)optional.get());
			}

			return Optional.of(list);
		}

		public void visitRequiredDependencies(Consumer<ResourceLocation> consumer) {
			this.dependencies.forEach(consumer);
		}

		public void visitOptionalDependencies(Consumer<ResourceLocation> consumer) {
		}
	}
}
