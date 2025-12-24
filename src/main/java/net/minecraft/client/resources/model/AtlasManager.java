package net.minecraft.client.resources.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;
import net.minecraft.server.packs.resources.PreparableReloadListener.StateKey;

@Environment(EnvType.CLIENT)
public class AtlasManager implements PreparableReloadListener, MaterialSet, AutoCloseable {
	private static final List<AtlasManager.AtlasConfig> KNOWN_ATLASES = List.of(
		new AtlasManager.AtlasConfig(Sheets.ARMOR_TRIMS_SHEET, AtlasIds.ARMOR_TRIMS, false),
		new AtlasManager.AtlasConfig(Sheets.BANNER_SHEET, AtlasIds.BANNER_PATTERNS, false),
		new AtlasManager.AtlasConfig(Sheets.BED_SHEET, AtlasIds.BEDS, false),
		new AtlasManager.AtlasConfig(TextureAtlas.LOCATION_BLOCKS, AtlasIds.BLOCKS, true),
		new AtlasManager.AtlasConfig(Sheets.CHEST_SHEET, AtlasIds.CHESTS, false),
		new AtlasManager.AtlasConfig(Sheets.DECORATED_POT_SHEET, AtlasIds.DECORATED_POT, false),
		new AtlasManager.AtlasConfig(Sheets.GUI_SHEET, AtlasIds.GUI, false, Set.of(GuiMetadataSection.TYPE)),
		new AtlasManager.AtlasConfig(Sheets.MAP_DECORATIONS_SHEET, AtlasIds.MAP_DECORATIONS, false),
		new AtlasManager.AtlasConfig(Sheets.PAINTINGS_SHEET, AtlasIds.PAINTINGS, false),
		new AtlasManager.AtlasConfig(TextureAtlas.LOCATION_PARTICLES, AtlasIds.PARTICLES, false),
		new AtlasManager.AtlasConfig(Sheets.SHIELD_SHEET, AtlasIds.SHIELD_PATTERNS, false),
		new AtlasManager.AtlasConfig(Sheets.SHULKER_SHEET, AtlasIds.SHULKER_BOXES, false),
		new AtlasManager.AtlasConfig(Sheets.SIGN_SHEET, AtlasIds.SIGNS, false)
	);
	public static final StateKey<AtlasManager.PendingStitchResults> PENDING_STITCH = new StateKey();
	private final Map<ResourceLocation, AtlasManager.AtlasEntry> atlasByTexture = new HashMap();
	private final Map<ResourceLocation, AtlasManager.AtlasEntry> atlasById = new HashMap();
	private Map<Material, TextureAtlasSprite> materialLookup = Map.of();
	private int maxMipmapLevels;

	public AtlasManager(TextureManager textureManager, int i) {
		for (AtlasManager.AtlasConfig atlasConfig : KNOWN_ATLASES) {
			TextureAtlas textureAtlas = new TextureAtlas(atlasConfig.textureId);
			textureManager.register(atlasConfig.textureId, textureAtlas);
			AtlasManager.AtlasEntry atlasEntry = new AtlasManager.AtlasEntry(textureAtlas, atlasConfig);
			this.atlasByTexture.put(atlasConfig.textureId, atlasEntry);
			this.atlasById.put(atlasConfig.definitionLocation, atlasEntry);
		}

		this.maxMipmapLevels = i;
	}

	public TextureAtlas getAtlasOrThrow(ResourceLocation resourceLocation) {
		AtlasManager.AtlasEntry atlasEntry = (AtlasManager.AtlasEntry)this.atlasById.get(resourceLocation);
		if (atlasEntry == null) {
			throw new IllegalArgumentException("Invalid atlas id: " + resourceLocation);
		} else {
			return atlasEntry.atlas();
		}
	}

	public void forEach(BiConsumer<ResourceLocation, TextureAtlas> biConsumer) {
		this.atlasById.forEach((resourceLocation, atlasEntry) -> biConsumer.accept(resourceLocation, atlasEntry.atlas));
	}

	public void updateMaxMipLevel(int i) {
		this.maxMipmapLevels = i;
	}

	public void close() {
		this.materialLookup = Map.of();
		this.atlasById.values().forEach(AtlasManager.AtlasEntry::close);
		this.atlasById.clear();
		this.atlasByTexture.clear();
	}

	@Override
	public TextureAtlasSprite get(Material material) {
		TextureAtlasSprite textureAtlasSprite = (TextureAtlasSprite)this.materialLookup.get(material);
		if (textureAtlasSprite != null) {
			return textureAtlasSprite;
		} else {
			ResourceLocation resourceLocation = material.atlasLocation();
			AtlasManager.AtlasEntry atlasEntry = (AtlasManager.AtlasEntry)this.atlasByTexture.get(resourceLocation);
			if (atlasEntry == null) {
				throw new IllegalArgumentException("Invalid atlas texture id: " + resourceLocation);
			} else {
				return atlasEntry.atlas().missingSprite();
			}
		}
	}

	public void prepareSharedState(SharedState sharedState) {
		int i = this.atlasById.size();
		List<AtlasManager.PendingStitch> list = new ArrayList(i);
		Map<ResourceLocation, CompletableFuture<SpriteLoader.Preparations>> map = new HashMap(i);
		List<CompletableFuture<?>> list2 = new ArrayList(i);
		this.atlasById.forEach((resourceLocation, atlasEntry) -> {
			CompletableFuture<SpriteLoader.Preparations> completableFuturex = new CompletableFuture();
			map.put(resourceLocation, completableFuturex);
			list.add(new AtlasManager.PendingStitch(atlasEntry, completableFuturex));
			list2.add(completableFuturex.thenCompose(SpriteLoader.Preparations::readyForUpload));
		});
		CompletableFuture<?> completableFuture = CompletableFuture.allOf((CompletableFuture[])list2.toArray(CompletableFuture[]::new));
		sharedState.set(PENDING_STITCH, new AtlasManager.PendingStitchResults(list, map, completableFuture));
	}

	public CompletableFuture<Void> reload(SharedState sharedState, Executor executor, PreparationBarrier preparationBarrier, Executor executor2) {
		AtlasManager.PendingStitchResults pendingStitchResults = (AtlasManager.PendingStitchResults)sharedState.get(PENDING_STITCH);
		ResourceManager resourceManager = sharedState.resourceManager();
		pendingStitchResults.pendingStitches
			.forEach(pendingStitch -> pendingStitch.entry.scheduleLoad(resourceManager, executor, this.maxMipmapLevels).whenComplete((preparations, throwable) -> {
				if (preparations != null) {
					pendingStitch.preparations.complete(preparations);
				} else {
					pendingStitch.preparations.completeExceptionally(throwable);
				}
			}));
		return pendingStitchResults.allReadyToUpload
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(object -> this.materialLookup = pendingStitchResults.joinAndUpload(), executor2);
	}

	@Environment(EnvType.CLIENT)
	public record AtlasConfig(
		ResourceLocation textureId, ResourceLocation definitionLocation, boolean createMipmaps, Set<MetadataSectionType<?>> additionalMetadata
	) {

		public AtlasConfig(ResourceLocation resourceLocation, ResourceLocation resourceLocation2, boolean bl) {
			this(resourceLocation, resourceLocation2, bl, Set.of());
		}
	}

	@Environment(EnvType.CLIENT)
	record AtlasEntry(TextureAtlas atlas, AtlasManager.AtlasConfig config) implements AutoCloseable {

		public void close() {
			this.atlas.clearTextureData();
		}

		CompletableFuture<SpriteLoader.Preparations> scheduleLoad(ResourceManager resourceManager, Executor executor, int i) {
			return SpriteLoader.create(this.atlas)
				.loadAndStitch(resourceManager, this.config.definitionLocation, this.config.createMipmaps ? i : 0, executor, this.config.additionalMetadata);
		}
	}

	@Environment(EnvType.CLIENT)
	record PendingStitch(AtlasManager.AtlasEntry entry, CompletableFuture<SpriteLoader.Preparations> preparations) {

		public void joinAndUpload(Map<Material, TextureAtlasSprite> map) {
			SpriteLoader.Preparations preparations = (SpriteLoader.Preparations)this.preparations.join();
			this.entry.atlas.upload(preparations);
			preparations.regions()
				.forEach((resourceLocation, textureAtlasSprite) -> map.put(new Material(this.entry.config.textureId, resourceLocation), textureAtlasSprite));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class PendingStitchResults {
		final List<AtlasManager.PendingStitch> pendingStitches;
		private final Map<ResourceLocation, CompletableFuture<SpriteLoader.Preparations>> stitchFuturesById;
		final CompletableFuture<?> allReadyToUpload;

		PendingStitchResults(
			List<AtlasManager.PendingStitch> list, Map<ResourceLocation, CompletableFuture<SpriteLoader.Preparations>> map, CompletableFuture<?> completableFuture
		) {
			this.pendingStitches = list;
			this.stitchFuturesById = map;
			this.allReadyToUpload = completableFuture;
		}

		public Map<Material, TextureAtlasSprite> joinAndUpload() {
			Map<Material, TextureAtlasSprite> map = new HashMap();
			this.pendingStitches.forEach(pendingStitch -> pendingStitch.joinAndUpload(map));
			return map;
		}

		public CompletableFuture<SpriteLoader.Preparations> get(ResourceLocation resourceLocation) {
			return (CompletableFuture<SpriteLoader.Preparations>)Objects.requireNonNull((CompletableFuture)this.stitchFuturesById.get(resourceLocation));
		}
	}
}
