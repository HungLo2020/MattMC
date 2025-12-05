package net.minecraft.client.resources.model;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.thread.ParallelMapTransform;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ModelBakery {
	public static final Material FIRE_0 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("fire_0");
	public static final Material FIRE_1 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("fire_1");
	public static final Material LAVA_FLOW = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("lava_flow");
	public static final Material WATER_FLOW = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("water_flow");
	public static final Material WATER_OVERLAY = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("water_overlay");
	public static final Material BANNER_BASE = new Material(Sheets.BANNER_SHEET, ResourceLocation.withDefaultNamespace("entity/banner_base"));
	public static final Material SHIELD_BASE = new Material(Sheets.SHIELD_SHEET, ResourceLocation.withDefaultNamespace("entity/shield_base"));
	public static final Material NO_PATTERN_SHIELD = new Material(Sheets.SHIELD_SHEET, ResourceLocation.withDefaultNamespace("entity/shield_base_nopattern"));
	public static final int DESTROY_STAGE_COUNT = 10;
	public static final List<ResourceLocation> DESTROY_STAGES = (List<ResourceLocation>)IntStream.range(0, 10)
		.mapToObj(i -> ResourceLocation.withDefaultNamespace("block/destroy_stage_" + i))
		.collect(Collectors.toList());
	public static final List<ResourceLocation> BREAKING_LOCATIONS = (List<ResourceLocation>)DESTROY_STAGES.stream()
		.map(resourceLocation -> resourceLocation.withPath(string -> "textures/" + string + ".png"))
		.collect(Collectors.toList());
	public static final List<RenderType> DESTROY_TYPES = (List<RenderType>)BREAKING_LOCATIONS.stream().map(RenderType::crumbling).collect(Collectors.toList());
	static final Logger LOGGER = LogUtils.getLogger();
	private final EntityModelSet entityModelSet;
	private final MaterialSet materials;
	private final PlayerSkinRenderCache playerSkinRenderCache;
	private final Map<BlockState, BlockStateModel.UnbakedRoot> unbakedBlockStateModels;
	private final Map<ResourceLocation, ClientItem> clientInfos;
	final Map<ResourceLocation, ResolvedModel> resolvedModels;
	final ResolvedModel missingModel;

	public ModelBakery(
		EntityModelSet entityModelSet,
		MaterialSet materialSet,
		PlayerSkinRenderCache playerSkinRenderCache,
		Map<BlockState, BlockStateModel.UnbakedRoot> map,
		Map<ResourceLocation, ClientItem> map2,
		Map<ResourceLocation, ResolvedModel> map3,
		ResolvedModel resolvedModel
	) {
		this.entityModelSet = entityModelSet;
		this.materials = materialSet;
		this.playerSkinRenderCache = playerSkinRenderCache;
		this.unbakedBlockStateModels = map;
		this.clientInfos = map2;
		this.resolvedModels = map3;
		this.missingModel = resolvedModel;
	}

	public CompletableFuture<ModelBakery.BakingResult> bakeModels(SpriteGetter spriteGetter, Executor executor) {
		ModelBakery.MissingModels missingModels = ModelBakery.MissingModels.bake(this.missingModel, spriteGetter);
		ModelBakery.ModelBakerImpl modelBakerImpl = new ModelBakery.ModelBakerImpl(spriteGetter);
		CompletableFuture<Map<BlockState, BlockStateModel>> completableFuture = ParallelMapTransform.schedule(
			this.unbakedBlockStateModels, (blockState, unbakedRoot) -> {
				try {
					return unbakedRoot.bake(blockState, modelBakerImpl);
				} catch (Exception var4x) {
					LOGGER.warn("Unable to bake model: '{}': {}", blockState, var4x);
					return null;
				}
			}, executor
		);
		CompletableFuture<Map<ResourceLocation, ItemModel>> completableFuture2 = ParallelMapTransform.schedule(
			this.clientInfos,
			(resourceLocation, clientItem) -> {
				try {
					return clientItem.model()
						.bake(
							new ItemModel.BakingContext(
								modelBakerImpl, this.entityModelSet, this.materials, this.playerSkinRenderCache, missingModels.item, clientItem.registrySwapper()
							)
						);
				} catch (Exception var6x) {
					LOGGER.warn("Unable to bake item model: '{}'", resourceLocation, var6x);
					return null;
				}
			},
			executor
		);
		Map<ResourceLocation, ClientItem.Properties> map = new HashMap(this.clientInfos.size());
		this.clientInfos.forEach((resourceLocation, clientItem) -> {
			ClientItem.Properties properties = clientItem.properties();
			if (!properties.equals(ClientItem.Properties.DEFAULT)) {
				map.put(resourceLocation, properties);
			}
		});
		return completableFuture.thenCombine(completableFuture2, (map2, map3) -> new ModelBakery.BakingResult(missingModels, map2, map3, map));
	}

	@Environment(EnvType.CLIENT)
	public record BakingResult(
		ModelBakery.MissingModels missingModels,
		Map<BlockState, BlockStateModel> blockStateModels,
		Map<ResourceLocation, ItemModel> itemStackModels,
		Map<ResourceLocation, ClientItem.Properties> itemProperties
	) {
	}

	@Environment(EnvType.CLIENT)
	public record MissingModels(BlockStateModel block, ItemModel item) {

		public static ModelBakery.MissingModels bake(ResolvedModel resolvedModel, SpriteGetter spriteGetter) {
			ModelBaker modelBaker = new ModelBaker() {
				@Override
				public ResolvedModel getModel(ResourceLocation resourceLocation) {
					throw new IllegalStateException("Missing model can't have dependencies, but asked for " + resourceLocation);
				}

				@Override
				public <T> T compute(ModelBaker.SharedOperationKey<T> sharedOperationKey) {
					return sharedOperationKey.compute(this);
				}

				@Override
				public SpriteGetter sprites() {
					return spriteGetter;
				}
			};
			TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
			boolean bl = resolvedModel.getTopAmbientOcclusion();
			boolean bl2 = resolvedModel.getTopGuiLight().lightLikeBlock();
			ItemTransforms itemTransforms = resolvedModel.getTopTransforms();
			QuadCollection quadCollection = resolvedModel.bakeTopGeometry(textureSlots, modelBaker, BlockModelRotation.X0_Y0);
			TextureAtlasSprite textureAtlasSprite = resolvedModel.resolveParticleSprite(textureSlots, modelBaker);
			BlockStateModel blockStateModel = new SingleVariant(new SimpleModelWrapper(quadCollection, bl, textureAtlasSprite));
			ItemModel itemModel = new MissingItemModel(quadCollection.getAll(), new ModelRenderProperties(bl2, textureAtlasSprite, itemTransforms));
			return new ModelBakery.MissingModels(blockStateModel, itemModel);
		}
	}

	@Environment(EnvType.CLIENT)
	class ModelBakerImpl implements ModelBaker {
		private final SpriteGetter sprites;
		private final Map<ModelBaker.SharedOperationKey<Object>, Object> operationCache = new ConcurrentHashMap();
		private final Function<ModelBaker.SharedOperationKey<Object>, Object> cacheComputeFunction = sharedOperationKey -> sharedOperationKey.compute(this);

		ModelBakerImpl(final SpriteGetter spriteGetter) {
			this.sprites = spriteGetter;
		}

		@Override
		public SpriteGetter sprites() {
			return this.sprites;
		}

		@Override
		public ResolvedModel getModel(ResourceLocation resourceLocation) {
			ResolvedModel resolvedModel = (ResolvedModel)ModelBakery.this.resolvedModels.get(resourceLocation);
			if (resolvedModel == null) {
				ModelBakery.LOGGER.warn("Requested a model that was not discovered previously: {}", resourceLocation);
				return ModelBakery.this.missingModel;
			} else {
				return resolvedModel;
			}
		}

		@Override
		public <T> T compute(ModelBaker.SharedOperationKey<T> sharedOperationKey) {
			return (T)this.operationCache.computeIfAbsent(sharedOperationKey, this.cacheComputeFunction);
		}
	}
}
