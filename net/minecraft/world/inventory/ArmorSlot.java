package net.minecraft.world.inventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

class ArmorSlot extends Slot {
	private final LivingEntity owner;
	private final EquipmentSlot slot;
	@Nullable
	private final ResourceLocation emptyIcon;

	public ArmorSlot(Container container, LivingEntity livingEntity, EquipmentSlot equipmentSlot, int i, int j, int k, @Nullable ResourceLocation resourceLocation) {
		super(container, i, j, k);
		this.owner = livingEntity;
		this.slot = equipmentSlot;
		this.emptyIcon = resourceLocation;
	}

	@Override
	public void setByPlayer(ItemStack itemStack, ItemStack itemStack2) {
		this.owner.onEquipItem(this.slot, itemStack2, itemStack);
		super.setByPlayer(itemStack, itemStack2);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return this.owner.isEquippableInSlot(itemStack, this.slot);
	}

	@Override
	public boolean isActive() {
		return this.owner.canUseSlot(this.slot);
	}

	@Override
	public boolean mayPickup(Player player) {
		ItemStack itemStack = this.getItem();
		return !itemStack.isEmpty() && !player.isCreative() && EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
			? false
			: super.mayPickup(player);
	}

	@Nullable
	@Override
	public ResourceLocation getNoItemIcon() {
		return this.emptyIcon;
	}
}
