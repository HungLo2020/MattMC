package net.minecraft.world.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.TooltipDisplay;

public class TaczAttachmentItem extends Item {
	private final String attachmentId;
	private final TaczAttachmentType type;
	private final int level;

	public TaczAttachmentItem(TaczAttachmentType type, int level, Item.Properties properties) {
		this("", type, level, properties);
	}

	public TaczAttachmentItem(String attachmentId, TaczAttachmentType type, int level, Item.Properties properties) {
		super(properties);
		this.attachmentId = attachmentId;
		this.type = type;
		this.level = level;
	}

	public String getAttachmentId() {
		return this.attachmentId;
	}

	public TaczAttachmentType getAttachmentType() {
		return this.type;
	}

	public int getAttachmentLevel() {
		return this.level;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag) {
		consumer.accept(Component.translatable("tooltip.tacz.attachment." + this.type.getSerializedName()).withStyle(ChatFormatting.GRAY));
		if (this.type == TaczAttachmentType.EXTENDED_MAG && this.level > 0) {
			consumer.accept(Component.translatable("tooltip.tacz.attachment.extended_mag_level_" + this.level).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
