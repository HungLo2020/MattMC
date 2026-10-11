package net.minecraft.client.resources.model;

import net.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ModelManager implements PreparableReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
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

	public boolean hasLoadedModels() {
		return this.missingModels != null;
	}

	public ClientItem.Properties getItemProperties(ResourceLocation resourceLocation) {
		return (ClientItem.Properties)this.itemProperties.getOrDefault(resourceLocation, ClientItem.Properties.DEFAULT);
	}

	public BlockModelShaper getBlockModelShaper() {
		return this.blockModelShaper;
	}

	public final CompletableFuture<Void> reload(SharedState sharedState, Executor executor, PreparationBarrier preparationBarrier, Executor executor2) {
		ResourceManager resourceManager = sharedState.resourceManager();
		MultiPackResourceManager nativeManager = MultiPackResourceManager.nativeManager(resourceManager);
		CompletableFuture<EntityModelSet> completableFuture = CompletableFuture.supplyAsync(EntityModelSet::vanilla, executor);
		CompletableFuture<SpecialBlockModelRenderer> completableFuture2 = completableFuture.thenApplyAsync(
			entityModelSet -> SpecialBlockModelRenderer.vanilla(
				new SpecialModelRenderer.BakingContext.Simple(entityModelSet, this.atlasManager, this.playerSkinRenderCache)
			),
			executor
		);
		// Rust loads model files and blockstates (`assets/models`) while Java decodes item definitions.
		CompletableFuture<NativeModels> completableFuture3 = CompletableFuture.supplyAsync(() -> NativeModels.load(nativeManager), executor);
		CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> completableFuture5 = ClientItemInfoLoader.scheduleLoad(resourceManager, executor);
		CompletableFuture<NativeModels.Discovered> completableFuture6 = completableFuture3.thenCombineAsync(
			completableFuture5, (nativeModels, loadedClientInfos) -> nativeModels.discover(itemRoots(loadedClientInfos)), executor
		);
		CompletableFuture<Object2IntMap<BlockState>> completableFuture7 = completableFuture3.thenApplyAsync(
			nativeModels -> buildModelGroups(this.blockColors, nativeModels), executor
		);
		CompletableFuture<SpriteLoader.Preparations> completableFuture8 = ((AtlasManager.PendingStitchResults)sharedState.get(AtlasManager.PENDING_STITCH))
			.get(AtlasIds.BLOCKS);
		return CompletableFuture.allOf(completableFuture8, completableFuture6, completableFuture7, completableFuture5, completableFuture, completableFuture2)
			.thenComposeAsync(
				void_ -> {
					NativeModels.Baked baked;
					try (NativeModels nativeModels = completableFuture3.join()) {
						Zone zone = Profiler.get().zone("bake");
						try {
							baked = nativeModels.bake((SpriteLoader.Preparations)completableFuture8.join(), (NativeModels.Discovered)completableFuture6.join());
						} finally {
							if (zone != null) {
								zone.close();
							}
						}
					}
					NativeModels.MissingSprites missingSprites = baked.missingSprites();
					ModelBakery modelBakery = new ModelBakery(
						(EntityModelSet)completableFuture.join(),
						this.atlasManager,
						this.playerSkinRenderCache,
						((ClientItemInfoLoader.LoadedClientInfos)completableFuture5.join()).contents(),
						baked
					);
					Object2IntMap<BlockState> object2IntMap = (Object2IntMap<BlockState>)completableFuture7.join();
					return modelBakery.bakeModels(executor)
						.thenApply(
							bakingResult -> {
								missingSprites.log();
								return new ModelManager.ReloadState(
									bakingResult,
									object2IntMap,
									createBlockStateToModelDispatch(bakingResult.blockStateModels(), bakingResult.missingModels().block()),
									(EntityModelSet)completableFuture.join(),
									(SpecialBlockModelRenderer)completableFuture2.join()
								);
							}
						);
				},
				executor
			)
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(this::apply, executor2);
	}

	/** The model ids item definitions reference, in discovery order (`ModelDiscovery.addRoot`). */
	private static List<ResourceLocation> itemRoots(ClientItemInfoLoader.LoadedClientInfos loadedClientInfos) {
		List<ResourceLocation> roots = new ArrayList<>();
		loadedClientInfos.contents().values().forEach(clientItem -> clientItem.model().resolveDependencies(roots::add));
		return roots;
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

	private static Object2IntMap<BlockState> buildModelGroups(BlockColors blockColors, NativeModels nativeModels) {
		Zone zone = Profiler.get().zone("block groups");

		Object2IntMap var3;
		try {
			var3 = ModelGroupCollector.build(blockColors, nativeModels);
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

}
