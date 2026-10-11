package net.minecraft.client.resources.model;

import net.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
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
	private final Map<ResourceLocation, ClientItem> clientInfos;
	private final NativeModels.Baked baked;

	/** Item model baking over the block state and geometry bake Rust made (`NativeModels`). */
	public ModelBakery(
		EntityModelSet entityModelSet, MaterialSet materialSet, PlayerSkinRenderCache playerSkinRenderCache, Map<ResourceLocation, ClientItem> map,
		NativeModels.Baked baked
	) {
		this.entityModelSet = entityModelSet;
		this.materials = materialSet;
		this.playerSkinRenderCache = playerSkinRenderCache;
		this.clientInfos = map;
		this.baked = baked;
	}

	public CompletableFuture<ModelBakery.BakingResult> bakeModels(Executor executor) {
		ModelBakery.MissingModels missingModels = this.baked.missingModels();
		ModelBaker modelBaker = this.baked.itemBaker();
		CompletableFuture<Map<ResourceLocation, ItemModel>> completableFuture = ParallelMapTransform.schedule(
			this.clientInfos,
			(resourceLocation, clientItem) -> {
				try {
					return clientItem.model()
						.bake(
							new ItemModel.BakingContext(
								modelBaker, this.entityModelSet, this.materials, this.playerSkinRenderCache, missingModels.item, clientItem.registrySwapper()
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
		return completableFuture.thenApply(map3 -> new ModelBakery.BakingResult(missingModels, this.baked.blockStateModels(), map3, map));
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
	}
}
