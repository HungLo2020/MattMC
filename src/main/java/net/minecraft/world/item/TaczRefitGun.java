package net.minecraft.world.item;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;

public interface TaczRefitGun {
	String ATTACHMENTS_KEY = "TaczAttachments";

	Set<TaczAttachmentType> supportedAttachmentTypes(ItemStack gunStack);

	default boolean allowAttachmentType(ItemStack gunStack, TaczAttachmentType type) {
		return type != TaczAttachmentType.NONE && this.supportedAttachmentTypes(gunStack).contains(type);
	}

	default boolean allowAttachment(ItemStack gunStack, ItemStack attachmentStack) {
		return attachmentStack.getItem() instanceof TaczAttachmentItem attachment && this.allowAttachmentType(gunStack, attachment.getAttachmentType());
	}

	default ItemStack getAttachment(ItemStack gunStack, TaczAttachmentType type) {
		return getStoredAttachment(gunStack, type);
	}

	default boolean installAttachment(ItemStack gunStack, ItemStack attachmentStack) {
		if (!this.allowAttachment(gunStack, attachmentStack) || !(attachmentStack.getItem() instanceof TaczAttachmentItem attachment)) {
			return false;
		}

		storeAttachment(gunStack, attachment.getAttachmentType(), attachmentStack);
		return true;
	}

	default ItemStack removeAttachment(ItemStack gunStack, TaczAttachmentType type) {
		ItemStack existing = getStoredAttachment(gunStack, type);
		if (existing.isEmpty()) {
			return ItemStack.EMPTY;
		}

		CustomData.update(DataComponents.CUSTOM_DATA, gunStack, tag -> {
			CompoundTag attachments = tag.getCompoundOrEmpty(ATTACHMENTS_KEY).copy();
			attachments.remove(type.getSerializedName());
			tag.put(ATTACHMENTS_KEY, attachments);
		});
		return existing;
	}

	static Set<TaczAttachmentType> only(TaczAttachmentType... types) {
		EnumSet<TaczAttachmentType> set = EnumSet.noneOf(TaczAttachmentType.class);
		for (TaczAttachmentType type : types) {
			if (type != TaczAttachmentType.NONE) {
				set.add(type);
			}
		}
		return Set.copyOf(set);
	}

	static ItemStack getStoredAttachment(ItemStack gunStack, TaczAttachmentType type) {
		CompoundTag tag = gunStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag attachments = tag.getCompoundOrEmpty(ATTACHMENTS_KEY);
		String itemId = attachments.getString(type.getSerializedName()).orElse("");
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}

		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item instanceof TaczAttachmentItem ? new ItemStack(item) : ItemStack.EMPTY;
	}

	static void storeAttachment(ItemStack gunStack, TaczAttachmentType type, ItemStack attachmentStack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(attachmentStack.getItem());
		CustomData.update(DataComponents.CUSTOM_DATA, gunStack, tag -> {
			CompoundTag attachments = tag.getCompoundOrEmpty(ATTACHMENTS_KEY).copy();
			attachments.putString(type.getSerializedName(), id.toString());
			tag.put(ATTACHMENTS_KEY, attachments);
		});
	}
}
