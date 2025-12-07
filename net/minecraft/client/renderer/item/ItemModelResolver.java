package net.minecraft.client.renderer.item;

import java.util.function.Function;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ItemModelResolver {
	private final Function<ResourceLocation, ItemModel> modelGetter;
	private final Function<ResourceLocation, ClientItem.Properties> clientProperties;

	public ItemModelResolver(ModelManager modelManager) {
		this.modelGetter = modelManager::getItemModel;
		this.clientProperties = modelManager::getItemProperties;
	}

	public void updateForLiving(ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemDisplayContext itemDisplayContext, LivingEntity livingEntity) {
		this.updateForTopItem(
			itemStackRenderState, itemStack, itemDisplayContext, livingEntity.level(), livingEntity, livingEntity.getId() + itemDisplayContext.ordinal()
		);
	}

	public void updateForNonLiving(ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemDisplayContext itemDisplayContext, Entity entity) {
		this.updateForTopItem(itemStackRenderState, itemStack, itemDisplayContext, entity.level(), null, entity.getId());
	}

	public void updateForTopItem(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemDisplayContext itemDisplayContext,
		@Nullable Level level,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		itemStackRenderState.clear();
		if (!itemStack.isEmpty()) {
			itemStackRenderState.displayContext = itemDisplayContext;
			this.appendItemLayers(itemStackRenderState, itemStack, itemDisplayContext, level, itemOwner, i);
		}
	}

	public void appendItemLayers(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemDisplayContext itemDisplayContext,
		@Nullable Level level,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		ResourceLocation resourceLocation = (ResourceLocation)itemStack.get(DataComponents.ITEM_MODEL);
		if (resourceLocation != null) {
			itemStackRenderState.setOversizedInGui(((ClientItem.Properties)this.clientProperties.apply(resourceLocation)).oversizedInGui());
			((ItemModel)this.modelGetter.apply(resourceLocation))
				.update(itemStackRenderState, itemStack, this, itemDisplayContext, level instanceof ClientLevel clientLevel ? clientLevel : null, itemOwner, i);
		}
	}

	public boolean shouldPlaySwapAnimation(ItemStack itemStack) {
		ResourceLocation resourceLocation = (ResourceLocation)itemStack.get(DataComponents.ITEM_MODEL);
		return resourceLocation == null ? true : ((ClientItem.Properties)this.clientProperties.apply(resourceLocation)).handAnimationOnSwap();
	}
}
