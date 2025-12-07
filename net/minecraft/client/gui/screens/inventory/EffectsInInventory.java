package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EffectsInInventory {
	private static final ResourceLocation EFFECT_BACKGROUND_LARGE_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_large");
	private static final ResourceLocation EFFECT_BACKGROUND_SMALL_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_small");
	private final AbstractContainerScreen<?> screen;
	private final Minecraft minecraft;
	@Nullable
	private MobEffectInstance hoveredEffect;

	public EffectsInInventory(AbstractContainerScreen<?> abstractContainerScreen) {
		this.screen = abstractContainerScreen;
		this.minecraft = Minecraft.getInstance();
	}

	public boolean canSeeEffects() {
		int i = this.screen.leftPos + this.screen.imageWidth + 2;
		int j = this.screen.width - i;
		return j >= 32;
	}

	public void renderEffects(GuiGraphics guiGraphics, int i, int j) {
		this.hoveredEffect = null;
		int k = this.screen.leftPos + this.screen.imageWidth + 2;
		int l = this.screen.width - k;
		Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
		if (!collection.isEmpty() && l >= 32) {
			boolean bl = l >= 120;
			int m = 33;
			if (collection.size() > 5) {
				m = 132 / (collection.size() - 1);
			}

			Iterable<MobEffectInstance> iterable = Ordering.natural().<MobEffectInstance>sortedCopy(collection);
			this.renderBackgrounds(guiGraphics, k, m, iterable, bl);
			this.renderIcons(guiGraphics, k, m, iterable, bl);
			if (bl) {
				this.renderLabels(guiGraphics, k, m, iterable);
			} else if (i >= k && i <= k + 33) {
				int n = this.screen.topPos;

				for (MobEffectInstance mobEffectInstance : iterable) {
					if (j >= n && j <= n + m) {
						this.hoveredEffect = mobEffectInstance;
					}

					n += m;
				}
			}
		}
	}

	public void renderTooltip(GuiGraphics guiGraphics, int i, int j) {
		if (this.hoveredEffect != null) {
			List<Component> list = List.of(
				this.getEffectName(this.hoveredEffect), MobEffectUtil.formatDuration(this.hoveredEffect, 1.0F, this.minecraft.level.tickRateManager().tickrate())
			);
			guiGraphics.setTooltipForNextFrame(this.screen.getFont(), list, Optional.empty(), i, j);
		}
	}

	private void renderBackgrounds(GuiGraphics guiGraphics, int i, int j, Iterable<MobEffectInstance> iterable, boolean bl) {
		int k = this.screen.topPos;

		for (MobEffectInstance mobEffectInstance : iterable) {
			if (bl) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_LARGE_SPRITE, i, k, 120, 32);
			} else {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_SMALL_SPRITE, i, k, 32, 32);
			}

			k += j;
		}
	}

	private void renderIcons(GuiGraphics guiGraphics, int i, int j, Iterable<MobEffectInstance> iterable, boolean bl) {
		int k = this.screen.topPos;

		for (MobEffectInstance mobEffectInstance : iterable) {
			Holder<MobEffect> holder = mobEffectInstance.getEffect();
			ResourceLocation resourceLocation = Gui.getMobEffectSprite(holder);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocation, i + (bl ? 6 : 7), k + 7, 18, 18);
			k += j;
		}
	}

	private void renderLabels(GuiGraphics guiGraphics, int i, int j, Iterable<MobEffectInstance> iterable) {
		int k = this.screen.topPos;

		for (MobEffectInstance mobEffectInstance : iterable) {
			Component component = this.getEffectName(mobEffectInstance);
			guiGraphics.drawString(this.screen.getFont(), component, i + 10 + 18, k + 6, -1);
			Component component2 = MobEffectUtil.formatDuration(mobEffectInstance, 1.0F, this.minecraft.level.tickRateManager().tickrate());
			guiGraphics.drawString(this.screen.getFont(), component2, i + 10 + 18, k + 6 + 10, -8421505);
			k += j;
		}
	}

	private Component getEffectName(MobEffectInstance mobEffectInstance) {
		MutableComponent mutableComponent = ((MobEffect)mobEffectInstance.getEffect().value()).getDisplayName().copy();
		if (mobEffectInstance.getAmplifier() >= 1 && mobEffectInstance.getAmplifier() <= 9) {
			mutableComponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (mobEffectInstance.getAmplifier() + 1)));
		}

		return mutableComponent;
	}
}
