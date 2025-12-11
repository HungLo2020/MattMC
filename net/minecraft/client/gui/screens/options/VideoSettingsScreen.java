package net.minecraft.client.gui.screens.options;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.client.renderer.shaders.gui.option.IrisVideoSettings;
import net.minecraft.client.renderer.shaders.gui.screen.ShaderPackScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class VideoSettingsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.videoTitle");
	private static final Component FABULOUS = Component.translatable("options.graphics.fabulous").withStyle(ChatFormatting.ITALIC);
	private static final Component WARNING_MESSAGE = Component.translatable("options.graphics.warning.message", new Object[]{FABULOUS, FABULOUS});
	private static final Component WARNING_TITLE = Component.translatable("options.graphics.warning.title").withStyle(ChatFormatting.RED);
	private static final Component BUTTON_ACCEPT = Component.translatable("options.graphics.warning.accept");
	private static final Component BUTTON_CANCEL = Component.translatable("options.graphics.warning.cancel");
	private static final int SHADER_PACK_BUTTON_WIDTH = 150;
	private final GpuWarnlistManager gpuWarnlistManager;
	private final int oldMipmaps;

	private OptionInstance<?>[] options() {
		List<OptionInstance<?>> optionsList = new ArrayList<>(
			List.of(
				this.options.graphicsMode(),
				this.options.renderDistance(),
				this.options.prioritizeChunkUpdates(),
				this.options.simulationDistance(),
				this.options.ambientOcclusion(),
				this.options.framerateLimit(),
				this.options.enableVsync(),
				this.options.inactivityFpsLimit(),
				this.options.guiScale(),
				this.options.attackIndicator(),
				this.options.gamma(),
				this.options.cloudStatus(),
				this.options.fullscreen(),
				this.options.particles(),
				this.options.mipmapLevels(),
				this.options.entityShadows(),
				this.options.screenEffectScale(),
				this.options.entityDistanceScaling(),
				this.options.fovEffectScale(),
				this.options.showAutosaveIndicator(),
				this.options.glintSpeed(),
				this.options.glintStrength(),
				this.options.menuBackgroundBlurriness(),
				this.options.panoramaTheme(),
				this.options.bobView(),
				this.options.cloudRange()
			)
		);
		optionsList.add(IrisVideoSettings.RENDER_DISTANCE);
		return optionsList.toArray(new OptionInstance[0]);
	}

	private Button createShaderPackButton() {
		return Button.builder(
				Component.translatable("options.iris.shaderPackSelection"),
				button -> this.minecraft.setScreen(new ShaderPackScreen(this))
			)
			.tooltip(Tooltip.create(Component.translatable("options.iris.shaderPackSelection.title")))
			.width(SHADER_PACK_BUTTON_WIDTH)
			.build();
	}

	public VideoSettingsScreen(Screen screen, Minecraft minecraft, Options options) {
		super(screen, options, TITLE);
		this.gpuWarnlistManager = minecraft.getGpuWarnlistManager();
		this.gpuWarnlistManager.resetWarnings();
		if (options.graphicsMode().get() == GraphicsStatus.FABULOUS) {
			this.gpuWarnlistManager.dismissWarning();
		}

		this.oldMipmaps = options.mipmapLevels().get();
	}

	@Override
	protected void addOptions() {
		int i = -1;
		Window window = this.minecraft.getWindow();
		Monitor monitor = window.findBestMonitor();
		int j;
		if (monitor == null) {
			j = -1;
		} else {
			Optional<VideoMode> optional = window.getPreferredFullscreenVideoMode();
			j = (Integer)optional.map(monitor::getVideoModeIndex).orElse(-1);
		}

		OptionInstance<Integer> optionInstance = new OptionInstance<>(
			"options.fullscreen.resolution",
			OptionInstance.noTooltip(),
			(component, integer) -> {
				if (monitor == null) {
					return Component.translatable("options.fullscreen.unavailable");
				} else if (integer == -1) {
					return Options.genericValueLabel(component, Component.translatable("options.fullscreen.current"));
				} else {
					VideoMode videoMode = monitor.getMode(integer);
					return Options.genericValueLabel(
						component,
						Component.translatable(
							"options.fullscreen.entry",
							new Object[]{
								videoMode.getWidth(), videoMode.getHeight(), videoMode.getRefreshRate(), videoMode.getRedBits() + videoMode.getGreenBits() + videoMode.getBlueBits()
							}
						)
					);
				}
			},
			new OptionInstance.IntRange(-1, monitor != null ? monitor.getModeCount() - 1 : -1),
			j,
			integer -> {
				if (monitor != null) {
					window.setPreferredFullscreenVideoMode(integer == -1 ? Optional.empty() : Optional.of(monitor.getMode(integer)));
				}
			}
		);
		this.list.addBig(optionInstance);
		this.list.addBig(this.options.biomeBlendRadius());
		this.list.addSmall(this.options());
		this.list.addSmall(this.createShaderPackButton(), null);
	}

	@Override
	public void onClose() {
		this.minecraft.getWindow().changeFullscreenVideoMode();
		super.onClose();
	}

	@Override
	public void removed() {
		if (this.options.mipmapLevels().get() != this.oldMipmaps) {
			this.minecraft.updateMaxMipLevel(this.options.mipmapLevels().get());
			this.minecraft.delayTextureReload();
		}

		super.removed();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (super.mouseClicked(mouseButtonEvent, bl)) {
			if (this.gpuWarnlistManager.isShowingWarning()) {
				List<Component> list = Lists.<Component>newArrayList(WARNING_MESSAGE, CommonComponents.NEW_LINE);
				String string = this.gpuWarnlistManager.getRendererWarnings();
				if (string != null) {
					list.add(CommonComponents.NEW_LINE);
					list.add(Component.translatable("options.graphics.warning.renderer", new Object[]{string}).withStyle(ChatFormatting.GRAY));
				}

				String string2 = this.gpuWarnlistManager.getVendorWarnings();
				if (string2 != null) {
					list.add(CommonComponents.NEW_LINE);
					list.add(Component.translatable("options.graphics.warning.vendor", new Object[]{string2}).withStyle(ChatFormatting.GRAY));
				}

				String string3 = this.gpuWarnlistManager.getVersionWarnings();
				if (string3 != null) {
					list.add(CommonComponents.NEW_LINE);
					list.add(Component.translatable("options.graphics.warning.version", new Object[]{string3}).withStyle(ChatFormatting.GRAY));
				}

				this.minecraft
					.setScreen(
						new UnsupportedGraphicsWarningScreen(WARNING_TITLE, list, ImmutableList.of(new UnsupportedGraphicsWarningScreen.ButtonOption(BUTTON_ACCEPT, button -> {
							this.options.graphicsMode().set(GraphicsStatus.FABULOUS);
							Minecraft.getInstance().levelRenderer.allChanged();
							this.gpuWarnlistManager.dismissWarning();
							this.minecraft.setScreen(this);
						}), new UnsupportedGraphicsWarningScreen.ButtonOption(BUTTON_CANCEL, button -> {
							this.gpuWarnlistManager.dismissWarningAndSkipFabulous();
							this.minecraft.setScreen(this);
						})))
					);
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (this.minecraft.hasControlDown()) {
			OptionInstance<Integer> optionInstance = this.options.guiScale();
			if (optionInstance.values() instanceof OptionInstance.ClampingLazyMaxIntRange clampingLazyMaxIntRange) {
				int i = optionInstance.get();
				int j = i == 0 ? clampingLazyMaxIntRange.maxInclusive() + 1 : i;
				int k = j + (int)Math.signum(g);
				if (k != 0 && k <= clampingLazyMaxIntRange.maxInclusive() && k >= clampingLazyMaxIntRange.minInclusive()) {
					CycleButton<Integer> cycleButton = (CycleButton<Integer>)this.list.findOption(optionInstance);
					if (cycleButton != null) {
						optionInstance.set(k);
						cycleButton.setValue(k);
						this.list.setScrollAmount(0.0);
						return true;
					}
				}
			}

			return false;
		} else {
			return super.mouseScrolled(d, e, f, g);
		}
	}

	public void updateFullscreenButton(boolean bl) {
		if (this.list != null) {
			AbstractWidget abstractWidget = this.list.findOption(this.options.fullscreen());
			if (abstractWidget != null) {
				CycleButton<Boolean> cycleButton = (CycleButton<Boolean>)abstractWidget;
				cycleButton.setValue(bl);
			}
		}
	}
}
