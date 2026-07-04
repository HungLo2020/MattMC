package net.minecraft.client.tacz;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.TaczRefitC2SPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczAttachmentItem;
import net.minecraft.world.item.TaczAttachmentType;
import net.minecraft.world.item.TaczMvpGunItem;
import net.minecraft.world.item.TaczRefitGun;

public class TaczGunRefitScreen extends Screen {
	private static final int PANEL_WIDTH = 184;
	private static final int BUTTON_HEIGHT = 18;
	private static final int GAP = 4;
	private String lastState = "";
	private int pendingRebuildTicks;

	public TaczGunRefitScreen() {
		super(Component.translatable("gui.tacz.gun_refit.title"));
		TaczRefitTransform.open();
	}

	@Override
	protected void init() {
		this.clearWidgets();
		LocalPlayer player = this.minecraft.player;
		if (player == null || !(player.getMainHandItem().getItem() instanceof TaczRefitGun gun)) {
			this.onClose();
			return;
		}

		ItemStack gunStack = player.getMainHandItem();
		this.lastState = this.buildState(player, gun, gunStack);
		int x = this.width - PANEL_WIDTH - 10;
		int y = 12;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.tacz.gun_refit.overview"), button -> {
			if (TaczRefitTransform.changeView(TaczAttachmentType.NONE)) {
				this.init();
			}
		}).bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + GAP;

		for (TaczAttachmentType type : TaczAttachmentType.values()) {
			if (type == TaczAttachmentType.NONE) {
				continue;
			}

			boolean allowed = gun.allowAttachmentType(gunStack, type);
			ItemStack installed = gun.getAttachment(gunStack, type);
			Component label = installed.isEmpty()
				? Component.translatable("tooltip.tacz.attachment." + type.getSerializedName())
				: Component.translatable("gui.tacz.gun_refit.slot.installed", Component.translatable("tooltip.tacz.attachment." + type.getSerializedName()), installed.getHoverName());
			Button button = Button.builder(label, clicked -> {
				if (allowed && TaczRefitTransform.changeView(type)) {
					this.init();
				}
			}).bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build();
			button.active = allowed;
			if (!allowed) {
				button.setTooltip(Tooltip.create(Component.translatable("gui.tacz.gun_refit.slot.locked")));
			}
			this.addRenderableWidget(button);
			y += BUTTON_HEIGHT + GAP;
		}

		TaczAttachmentType selected = TaczRefitTransform.currentType();
		if (selected != TaczAttachmentType.NONE && gun.allowAttachmentType(gunStack, selected)) {
			this.addSelectedSlotWidgets(player, gun, gunStack, x, y + 6, selected);
		}
	}

	private void addSelectedSlotWidgets(LocalPlayer player, TaczRefitGun gun, ItemStack gunStack, int x, int y, TaczAttachmentType selected) {
		ItemStack installed = gun.getAttachment(gunStack, selected);
		if (!installed.isEmpty()) {
			this.addRenderableWidget(Button.builder(Component.translatable("gui.tacz.gun_refit.unload", installed.getHoverName()), button -> {
				ClientPlayNetworking.send(new TaczRefitC2SPayload(TaczRefitC2SPayload.Action.UNINSTALL, -1, selected));
				this.scheduleRebuild();
			}).bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
			y += BUTTON_HEIGHT + GAP;
		}

		Inventory inventory = player.getInventory();
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!(stack.getItem() instanceof TaczAttachmentItem attachment) || attachment.getAttachmentType() != selected || !gun.allowAttachment(gunStack, stack)) {
				continue;
			}

			int slotIndex = slot;
			Button button = Button.builder(stack.getHoverName(), clicked -> {
				ClientPlayNetworking.send(new TaczRefitC2SPayload(TaczRefitC2SPayload.Action.INSTALL, slotIndex, selected));
				this.scheduleRebuild();
			}).bounds(x, y + count * (BUTTON_HEIGHT + GAP), PANEL_WIDTH, BUTTON_HEIGHT).build();
			button.setTooltip(Tooltip.create(Component.translatable("gui.tacz.gun_refit.install.tooltip", stack.getHoverName())));
			this.addRenderableWidget(button);
			count++;
			if (count >= 8) {
				break;
			}
		}
	}

	private void scheduleRebuild() {
		this.pendingRebuildTicks = 4;
	}

	@Override
	public void tick() {
		LocalPlayer player = this.minecraft.player;
		if (player == null || !(player.getMainHandItem().getItem() instanceof TaczRefitGun gun)) {
			this.onClose();
			return;
		}

		ItemStack gunStack = player.getMainHandItem();
		String state = this.buildState(player, gun, gunStack);
		if (!state.equals(this.lastState) || this.pendingRebuildTicks > 0 && --this.pendingRebuildTicks == 0) {
			this.init();
		}
	}

	private String buildState(LocalPlayer player, TaczRefitGun gun, ItemStack gunStack) {
		StringBuilder builder = new StringBuilder();
		builder.append(TaczRefitTransform.currentType().getSerializedName()).append('|');
		for (TaczAttachmentType type : TaczAttachmentType.values()) {
			if (type == TaczAttachmentType.NONE || !gun.allowAttachmentType(gunStack, type)) {
				continue;
			}
			builder.append(type.getSerializedName()).append('=').append(gun.getAttachment(gunStack, type).getItem()).append(';');
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof TaczAttachmentItem attachment && gun.allowAttachment(gunStack, stack)) {
				builder.append(slot).append(':').append(attachment.getAttachmentType().getSerializedName()).append(':').append(stack.getItem()).append(':').append(stack.getCount()).append(';');
			}
		}
		return builder.toString();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderPanel(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		this.renderDetails(guiGraphics);
	}

	private void renderPanel(GuiGraphics guiGraphics) {
		int x = this.width - PANEL_WIDTH - 14;
		guiGraphics.fill(x, 8, this.width - 6, this.height - 8, 0xA0101010);
		guiGraphics.fill(x, 8, x + 1, this.height - 8, 0x80FFFFFF);
		guiGraphics.drawString(this.font, this.title, x + 8, this.height - 24, 0xFFE6E6E6);
	}

	private void renderDetails(GuiGraphics guiGraphics) {
		LocalPlayer player = this.minecraft.player;
		if (player == null || !(player.getMainHandItem().getItem() instanceof TaczRefitGun gun)) {
			return;
		}

		ItemStack gunStack = player.getMainHandItem();
		int x = 12;
		int y = 12;
		guiGraphics.fill(x - 4, y - 4, x + 220, y + 76, 0xA0101010);
		guiGraphics.drawString(this.font, gunStack.getHoverName(), x, y, 0xFFFFFFFF);
		y += 14;
		if (gunStack.getItem() instanceof TaczMvpGunItem) {
			guiGraphics.drawString(
				this.font,
				Component.translatable("item.minecraft.glock_17.ammo", TaczMvpGunItem.getAmmo(gunStack), TaczMvpGunItem.getMagazineSize(gunStack)),
				x,
				y,
				0xFFE6E6E6
			);
			y += 12;
		}

		TaczAttachmentType selected = TaczRefitTransform.currentType();
		Component selectedText = selected == TaczAttachmentType.NONE
			? Component.translatable("gui.tacz.gun_refit.overview")
			: Component.translatable("tooltip.tacz.attachment." + selected.getSerializedName());
		guiGraphics.drawString(this.font, Component.translatable("gui.tacz.gun_refit.selected", selectedText).withStyle(ChatFormatting.GRAY), x, y, 0xFFE6E6E6);
		y += 12;
		for (TaczAttachmentType type : TaczAttachmentType.values()) {
			if (type == TaczAttachmentType.NONE || !gun.allowAttachmentType(gunStack, type)) {
				continue;
			}
			ItemStack installed = gun.getAttachment(gunStack, type);
			Component value = installed.isEmpty() ? Component.translatable("tooltip.tacz.attachment.none") : installed.getHoverName();
			guiGraphics.drawString(this.font, Component.translatable("gui.tacz.gun_refit.slot.line", Component.translatable("tooltip.tacz.attachment." + type.getSerializedName()), value), x, y, 0xFFD8D8D8);
			y += 11;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (TaczKeyMappings.REFIT.matches(keyEvent)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(keyEvent);
	}

	@Override
	public void onClose() {
		TaczRefitTransform.close();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
