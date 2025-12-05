package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ModelManager implements PreparableReloadListener, FabricBakedModelManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
	private Map<ResourceLocation, ItemModel> bakedItemStackModels = Map.of();
	private Map<ResourceLocation, ClientItem.Properties> itemProperties = Map.of();
	private final AtlasManager atlasManager;
	private final PlayerSkinRenderCache playerSkinRenderCache;
	private final BlockModelShaper blockModelShaper;
	private final BlockColors blockColors;
	private EntityModelSet entityModelSet = EntityModelSet.EMPTY;
	private SpecialBlockModelRenderer specialBlockModelRenderer = SpecialBlockModelRenderer.EMPTY;
	private ModelBakery.MissingModels missingModels;
	private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();

	public ModelManager(BlockColors blockColors, AtlasManager atlasManager, PlayerSkinRenderCache playerSkinRenderCache) {
		this.blockColors = blockColors;
		this.atlasManager = atlasManager;
		this.playerSkinRenderCache = playerSkinRenderCache;
		this.blockModelShaper = new BlockModelShaper(this);
	}

	public BlockStateModel getMissingBlockStateModel() {
		return this.missingModels.block();
	}

	public ItemModel getItemModel(ResourceLocation resourceLocation) {
		return (ItemModel)this.bakedItemStackModels.getOrDefault(resourceLocation, this.missingModels.item());
	}

	public ClientItem.Properties getItemProperties(ResourceLocation resourceLocation) {
		return (ClientItem.Properties)this.itemProperties.getOrDefault(resourceLocation, ClientItem.Properties.DEFAULT);
	}

	public BlockModelShaper getBlockModelShaper() {
		return this.blockModelShaper;
	}

	public final CompletableFuture<Void> reload(SharedState sharedState, Executor executor, PreparationBarrier preparationBarrier, Executor executor2) {
		ResourceManager resourceManager = sharedState.resourceManager();
		CompletableFuture<EntityModelSet> completableFuture = CompletableFuture.supplyAsync(EntityModelSet::vanilla, executor);
		CompletableFuture<SpecialBlockModelRenderer> completableFuture2 = completableFuture.thenApplyAsync(
			entityModelSet -> SpecialBlockModelRenderer.vanilla(
				new SpecialModelRenderer.BakingContext.Simple(entityModelSet, this.atlasManager, this.playerSkinRenderCache)
			),
			executor
		);
		CompletableFuture<Map<ResourceLocation, UnbakedModel>> completableFuture3 = loadBlockModels(resourceManager, executor);
		CompletableFuture<BlockStateModelLoader.LoadedModels> completableFuture4 = BlockStateModelLoader.loadBlockStates(resourceManager, executor);
		CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> completableFuture5 = ClientItemInfoLoader.scheduleLoad(resourceManager, executor);
		CompletableFuture<ModelManager.ResolvedModels> completableFuture6 = CompletableFuture.allOf(completableFuture3, completableFuture4, completableFuture5)
			.thenApplyAsync(
				void_ -> discoverModelDependencies(
					(Map<ResourceLocation, UnbakedModel>)completableFuture3.join(),
					(BlockStateModelLoader.LoadedModels)completableFuture4.join(),
					(ClientItemInfoLoader.LoadedClientInfos)completableFuture5.join()
				),
				executor
			);
		CompletableFuture<Object2IntMap<BlockState>> completableFuture7 = completableFuture4.thenApplyAsync(
			loadedModels -> buildModelGroups(this.blockColors, loadedModels), executor
		);
		CompletableFuture<SpriteLoader.Preparations> completableFuture8 = ((AtlasManager.PendingStitchResults)sharedState.get(AtlasManager.PENDING_STITCH))
			.get(AtlasIds.BLOCKS);
		return CompletableFuture.allOf(
				completableFuture8,
				completableFuture6,
				completableFuture7,
				completableFuture4,
				completableFuture5,
				completableFuture,
				completableFuture2,
				completableFuture3
			)
			.thenComposeAsync(
				void_ -> {
					SpriteLoader.Preparations preparations = (SpriteLoader.Preparations)completableFuture8.join();
					ModelManager.ResolvedModels resolvedModels = (ModelManager.ResolvedModels)completableFuture6.join();
					Object2IntMap<BlockState> object2IntMap = (Object2IntMap<BlockState>)completableFuture7.join();
					Set<ResourceLocation> set = Sets.<ResourceLocation>difference(((Map)completableFuture3.join()).keySet(), resolvedModels.models.keySet());
					if (!set.isEmpty()) {
						LOGGER.debug("Unreferenced models: \n{}", set.stream().sorted().map(resourceLocation -> "\t" + resourceLocation + "\n").collect(Collectors.joining()));
					}

					ModelBakery modelBakery = new ModelBakery(
						(EntityModelSet)completableFuture.join(),
						this.atlasManager,
						this.playerSkinRenderCache,
						((BlockStateModelLoader.LoadedModels)completableFuture4.join()).models(),
						((ClientItemInfoLoader.LoadedClientInfos)completableFuture5.join()).contents(),
						resolvedModels.models(),
						resolvedModels.missing()
					);
					return loadModels(
						preparations, modelBakery, object2IntMap, (EntityModelSet)completableFuture.join(), (SpecialBlockModelRenderer)completableFuture2.join(), executor
					);
				},
				executor
			)
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(this::apply, executor2);
	}

	private static CompletableFuture<Map<ResourceLocation, UnbakedModel>> loadBlockModels(ResourceManager resourceManager, Executor executor) {
		return CompletableFuture.supplyAsync(() -> MODEL_LISTER.listMatchingResources(resourceManager), executor)
			.thenCompose(
				map -> {
					List<CompletableFuture<Pair<ResourceLocation, BlockModel>>> list = new ArrayList(map.size());

					for (Entry<ResourceLocation, Resource> entry : map.entrySet()) {
						list.add(CompletableFuture.supplyAsync(() -> {
							ResourceLocation resourceLocation = MODEL_LISTER.fileToId((ResourceLocation)entry.getKey());

							try {
								Reader reader = ((Resource)entry.getValue()).openAsReader();

								Pair var3;
								try {
									var3 = Pair.of(resourceLocation, BlockModel.fromStream(reader));
								} catch (Throwable var6) {
									if (reader != null) {
										try {
											reader.close();
										} catch (Throwable var5) {
											var6.addSuppressed(var5);
										}
									}

									throw var6;
								}

								if (reader != null) {
									reader.close();
								}

								return var3;
							} catch (Exception var7) {
								LOGGER.error("Failed to load model {}", entry.getKey(), var7);
								return null;
							}
						}, executor));
					}

					return Util.sequence(list)
						.thenApply(listx -> (Map)listx.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
				}
			);
	}

	private static ModelManager.ResolvedModels discoverModelDependencies(
		Map<ResourceLocation, UnbakedModel> map, BlockStateModelLoader.LoadedModels loadedModels, ClientItemInfoLoader.LoadedClientInfos loadedClientInfos
	) {
		Zone zone = Profiler.get().zone("dependencies");

		ModelManager.ResolvedModels var5;
		try {
			ModelDiscovery modelDiscovery = new ModelDiscovery(map, MissingBlockModel.missingModel());
			modelDiscovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
			loadedModels.models().values().forEach(modelDiscovery::addRoot);
			loadedClientInfos.contents().values().forEach(clientItem -> modelDiscovery.addRoot(clientItem.model()));
			var5 = new ModelManager.ResolvedModels(modelDiscovery.missingModel(), modelDiscovery.resolve());
		} catch (Throwable var7) {
			if (zone != null) {
				try {
					zone.close();
				} catch (Throwable var6) {
					var7.addSuppressed(var6);
				}
			}

			throw var7;
		}

		if (zone != null) {
			zone.close();
		}

		return var5;
	}

	private static CompletableFuture<ModelManager.ReloadState> loadModels(
		SpriteLoader.Preparations preparations,
		ModelBakery modelBakery,
		Object2IntMap<BlockState> object2IntMap,
		EntityModelSet entityModelSet,
		SpecialBlockModelRenderer specialBlockModelRenderer,
		Executor executor
	) {
		final Multimap<String, Material> multimap = Multimaps.synchronizedMultimap(HashMultimap.create());
		final Multimap<String, String> multimap2 = Multimaps.synchronizedMultimap(HashMultimap.create());
		return modelBakery.bakeModels(new SpriteGetter() {
				private final TextureAtlasSprite missingSprite = preparations.missing();

				@Override
				public TextureAtlasSprite get(Material material, ModelDebugName modelDebugName) {
					if (material.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
						TextureAtlasSprite textureAtlasSprite = preparations.getSprite(material.texture());
						if (textureAtlasSprite != null) {
							return textureAtlasSprite;
						}
					}

					multimap.put(modelDebugName.debugName(), material);
					return this.missingSprite;
				}

				@Override
				public TextureAtlasSprite reportMissingReference(String string, ModelDebugName modelDebugName) {
					multimap2.put(modelDebugName.debugName(), string);
					return this.missingSprite;
				}
			}, executor)
			.thenApply(
				bakingResult -> {
					multimap.asMap()
						.forEach(
							(string, collection) -> LOGGER.warn(
								"Missing textures in model {}:\n{}",
								string,
								collection.stream()
									.sorted(Material.COMPARATOR)
									.map(material -> "    " + material.atlasLocation() + ":" + material.texture())
									.collect(Collectors.joining("\n"))
							)
						);
					multimap2.asMap()
						.forEach(
							(string, collection) -> LOGGER.warn(
								"Missing texture references in model {}:\n{}", string, collection.stream().sorted().map(stringx -> "    " + stringx).collect(Collectors.joining("\n"))
							)
						);
					Map<BlockState, BlockStateModel> map = createBlockStateToModelDispatch(bakingResult.blockStateModels(), bakingResult.missingModels().block());
					return new ModelManager.ReloadState(bakingResult, object2IntMap, map, entityModelSet, specialBlockModelRenderer);
				}
			);
	}

	private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(Map<BlockState, BlockStateModel> map, BlockStateModel blockStateModel) {
		Zone zone = Profiler.get().zone("block state dispatch");

		Object var8;
		try {
			Map<BlockState, BlockStateModel> map2 = new IdentityHashMap(map);

			for (Block block : BuiltInRegistries.BLOCK) {
				block.getStateDefinition().getPossibleStates().forEach(blockState -> {
					if (map.putIfAbsent(blockState, blockStateModel) == null) {
						LOGGER.warn("Missing model for variant: '{}'", blockState);
					}
				});
			}

			var8 = map2;
		} catch (Throwable var7) {
			if (zone != null) {
				try {
					zone.close();
				} catch (Throwable var6) {
					var7.addSuppressed(var6);
				}
			}

			throw var7;
		}

		if (zone != null) {
			zone.close();
		}

		return (Map<BlockState, BlockStateModel>)var8;
	}

	private static Object2IntMap<BlockState> buildModelGroups(BlockColors blockColors, BlockStateModelLoader.LoadedModels loadedModels) {
		Zone zone = Profiler.get().zone("block groups");

		Object2IntMap var3;
		try {
			var3 = ModelGroupCollector.build(blockColors, loadedModels);
		} catch (Throwable var6) {
			if (zone != null) {
				try {
					zone.close();
				} catch (Throwable var5) {
					var6.addSuppressed(var5);
				}
			}

			throw var6;
		}

		if (zone != null) {
			zone.close();
		}

		return var3;
	}

	private void apply(ModelManager.ReloadState reloadState) {
		ModelBakery.BakingResult bakingResult = reloadState.bakedModels;
		this.bakedItemStackModels = bakingResult.itemStackModels();
		this.itemProperties = bakingResult.itemProperties();
		this.modelGroups = reloadState.modelGroups;
		this.missingModels = bakingResult.missingModels();
		this.blockModelShaper.replaceCache(reloadState.modelCache);
		this.specialBlockModelRenderer = reloadState.specialBlockModelRenderer;
		this.entityModelSet = reloadState.entityModelSet;
	}

	public boolean requiresRender(BlockState blockState, BlockState blockState2) {
		if (blockState == blockState2) {
			return false;
		} else {
			int i = this.modelGroups.getInt(blockState);
			if (i != -1) {
				int j = this.modelGroups.getInt(blockState2);
				if (i == j) {
					FluidState fluidState = blockState.getFluidState();
					FluidState fluidState2 = blockState2.getFluidState();
					return fluidState != fluidState2;
				}
			}

			return true;
		}
	}

	public Supplier<SpecialBlockModelRenderer> specialBlockModelRenderer() {
		return () -> this.specialBlockModelRenderer;
	}

	public Supplier<EntityModelSet> entityModels() {
		return () -> this.entityModelSet;
	}

	@Environment(EnvType.CLIENT)
	record ReloadState(
		ModelBakery.BakingResult bakedModels,
		Object2IntMap<BlockState> modelGroups,
		Map<BlockState, BlockStateModel> modelCache,
		EntityModelSet entityModelSet,
		SpecialBlockModelRenderer specialBlockModelRenderer
	) {
	}

	@Environment(EnvType.CLIENT)
	record ResolvedModels(ResolvedModel missing, Map<ResourceLocation, ResolvedModel> models) {
	}
}
