package net.minecraft.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class BlockModelWrapper implements ItemModel {
	private final List<ItemTintSource> tints;
	private final List<BakedQuad> quads;
	private final Supplier<Vector3f[]> extents;
	private final ModelRenderProperties properties;
	private final boolean animated;

	public BlockModelWrapper(List<ItemTintSource> list, List<BakedQuad> list2, ModelRenderProperties modelRenderProperties) {
		this.tints = list;
		this.quads = list2;
		this.properties = modelRenderProperties;
		this.extents = Suppliers.memoize(() -> computeExtents(this.quads));
		boolean bl = false;

		for (BakedQuad bakedQuad : list2) {
			if (bakedQuad.sprite().contents().isAnimated()) {
				bl = true;
				break;
			}
		}

		this.animated = bl;
	}

	public static Vector3f[] computeExtents(List<BakedQuad> list) {
		Set<Vector3f> set = new HashSet();

		for (BakedQuad bakedQuad : list) {
			FaceBakery.extractPositions(bakedQuad.vertices(), set::add);
		}

		return (Vector3f[])set.toArray(Vector3f[]::new);
	}

	@Override
	public void update(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemModelResolver itemModelResolver,
		ItemDisplayContext itemDisplayContext,
		@Nullable ClientLevel clientLevel,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		itemStackRenderState.appendModelIdentityElement(this);
		ItemStackRenderState.LayerRenderState layerRenderState = itemStackRenderState.newLayer();
		if (itemStack.hasFoil()) {
			ItemStackRenderState.FoilType foilType = hasSpecialAnimatedTexture(itemStack)
				? ItemStackRenderState.FoilType.SPECIAL
				: ItemStackRenderState.FoilType.STANDARD;
			layerRenderState.setFoilType(foilType);
			itemStackRenderState.setAnimated();
			itemStackRenderState.appendModelIdentityElement(foilType);
		}

		int j = this.tints.size();
		int[] is = layerRenderState.prepareTintLayers(j);

		for (int k = 0; k < j; k++) {
			int l = ((ItemTintSource)this.tints.get(k)).calculate(itemStack, clientLevel, itemOwner == null ? null : itemOwner.asLivingEntity());
			is[k] = l;
			itemStackRenderState.appendModelIdentityElement(l);
		}

		layerRenderState.setExtents(this.extents);
		layerRenderState.setRenderType(ItemBlockRenderTypes.getRenderType(itemStack));
		this.properties.applyToLayer(layerRenderState, itemDisplayContext);
		layerRenderState.prepareQuadList().addAll(this.quads);
		if (this.animated) {
			itemStackRenderState.setAnimated();
		}
	}

	private static boolean hasSpecialAnimatedTexture(ItemStack itemStack) {
		return itemStack.is(ItemTags.COMPASSES) || itemStack.is(Items.CLOCK);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(ResourceLocation model, List<ItemTintSource> tints) implements ItemModel.Unbaked {
		public static final MapCodec<BlockModelWrapper.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ResourceLocation.CODEC.fieldOf("model").forGetter(BlockModelWrapper.Unbaked::model),
					ItemTintSources.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(BlockModelWrapper.Unbaked::tints)
				)
				.apply(instance, BlockModelWrapper.Unbaked::new)
		);

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.model);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext bakingContext) {
			ModelBaker modelBaker = bakingContext.blockModelBaker();
			ResolvedModel resolvedModel = modelBaker.getModel(this.model);
			TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
			List<BakedQuad> list = resolvedModel.bakeTopGeometry(textureSlots, modelBaker, BlockModelRotation.X0_Y0).getAll();
			ModelRenderProperties modelRenderProperties = ModelRenderProperties.fromResolvedModel(modelBaker, resolvedModel, textureSlots);
			return new BlockModelWrapper(this.tints, list, modelRenderProperties);
		}

		@Override
		public MapCodec<BlockModelWrapper.Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
