package net.vulkanic.gui;

import net.vulkanic.bridge.RustGalFrameScheduler;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import net.vulkanic.bridge.VulkanicGalBridge;

import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.dev.RenderDocCaptureHook;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.TracyCompat;
import net.minecraft.world.BossEvent;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RustGalGuiRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String CROSSHAIR_PRODUCER = "minecraft.gui.crosshair";
	private static final String HOTBAR_BASE_PRODUCER = "minecraft.gui.hotbar.base";
	private static final String HOTBAR_SELECTION_PRODUCER = "minecraft.gui.hotbar.selection";
	private static final String EXPERIENCE_BACKGROUND_PRODUCER = "minecraft.gui.experience.background";
	private static final String EXPERIENCE_PROGRESS_PRODUCER = "minecraft.gui.experience.progress";
	private static final String ATTACK_CROSSHAIR_BACKGROUND_PRODUCER = "minecraft.gui.attack.crosshair.background";
	private static final String ATTACK_CROSSHAIR_PROGRESS_PRODUCER = "minecraft.gui.attack.crosshair.progress";
	private static final String ATTACK_HOTBAR_BACKGROUND_PRODUCER = "minecraft.gui.attack.hotbar.background";
	private static final String ATTACK_HOTBAR_PROGRESS_PRODUCER = "minecraft.gui.attack.hotbar.progress";
	private static final String BOSS_BAR_BACKGROUND_PRODUCER = "minecraft.gui.boss.background";
	private static final String BOSS_BAR_PROGRESS_PRODUCER = "minecraft.gui.boss.progress";
	private static final String ARMOR_ICON_PRODUCER = "minecraft.gui.armor";
	private static final String PLAYER_HEART_PRODUCER = "minecraft.gui.player-heart";
	private static final String ABSORPTION_HEART_PRODUCER = "minecraft.gui.absorption-heart";
	private static final String HUNGER_ICON_PRODUCER = "minecraft.gui.hunger";
	private static final String AIR_BUBBLE_PRODUCER = "minecraft.gui.air";
	private static final String MOUNT_HEART_PRODUCER = "minecraft.gui.mount-heart";
	private static final boolean ASSET_UPDATES_DISABLED =
		Boolean.getBoolean("mattmc.dev.rustGalGui.assetUpdates.disabled");
	private static final Object LOCK = new Object();
	private static VulkanicGalBridge bridge;
	private static Thread renderThread;
	private static int configuredWidth;
	private static int configuredHeight;
	private static long nextCorrelationId = 1L;
	private static long generation = 1L;
	private static long assetGeneration = 1L;
	private static long uploadedAssetGeneration;
	private static long attemptedAssetGeneration;
	private static long lastAssetPayloadCount;
	private static long lastAssetPayloadBytes;
	private static long assetUpdateFailures;
	private static long lastSubmitted;
	private static long lastRetiredSubmission;
	private static List<VulkanicGalBridge.GuiAssetRecord> pendingAssets = List.of();
	private static final RustGalFrameScheduler<VulkanicGalBridge.GuiSpriteRecord> SCHEDULER =
		new RustGalFrameScheduler<>("Rust VulkanicGAL deferred GUI");
	private static final Metrics METRICS = new Metrics();

	private RustGalGuiRenderer() {
	}

	public enum GuiExecutionRoute {
		DISABLED(false, false),
		JAVA_COMPATIBILITY(true, false),
		RUST_OPENGL_BORROWED_CONTEXT(false, true),
		RUST_VULKAN_WHOLE_FRAME(false, true);

		private final boolean javaCompatibility;
		private final boolean rustGui;

		GuiExecutionRoute(boolean javaCompatibility, boolean rustGui) {
			this.javaCompatibility = javaCompatibility;
			this.rustGui = rustGui;
		}

		public boolean usesJavaCompatibility() {
			return this.javaCompatibility;
		}

		public boolean usesRustGui() {
			return this.rustGui;
		}
	}

	public static boolean isMigratedGuiEnabled() {
		String legacyCrosshairFlag = System.getProperty("mattmc.rustGal.guiCrosshair.enabled", "true");
		return Boolean.parseBoolean(System.getProperty("mattmc.rustGal.gui.enabled", legacyCrosshairFlag));
	}

	public static boolean isWholeFrameVulkanEnabled() {
		return RustGalVulkanWholeFrameMode.enabled();
	}

	public static boolean isWholeFrameVulkanActive() {
		return RustGalVulkanWholeFrameMode.enabledForBackend(VulkanicAPI.isVulkanBackendSelected());
	}

	public static boolean isMigratedGuiDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.guiCrosshair.disabled") || Boolean.getBoolean("mattmc.dev.rustGalGui.disabled");
	}

	public static boolean isMigratedGuiLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.guiCrosshair.legacyControl") || Boolean.getBoolean("mattmc.dev.rustGalGui.legacyControl");
	}

	public static boolean isArmorDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.armor.disabled");
	}

	public static boolean isArmorLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.armor.legacyControl");
	}

	public static boolean isPlayerHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.disabled");
	}

	public static boolean isPlayerHealthLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.legacyControl");
	}

	public static boolean isAbsorptionHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.absorption.disabled");
	}

	public static boolean isAbsorptionHealthLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.absorption.legacyControl");
	}

	public static boolean isHungerDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.hunger.disabled");
	}

	public static boolean isHungerLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.hunger.legacyControl");
	}

	public static boolean isAirDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.air.disabled");
	}

	public static boolean isAirLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.air.legacyControl");
	}

	public static boolean isMountHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.mountHealth.disabled");
	}

	public static boolean isMountHealthLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.mountHealth.legacyControl");
	}

	public static GuiExecutionRoute currentExecutionRoute() {
		return selectExecutionRoute(
			VulkanicAPI.isVulkanBackendSelected(),
			isWholeFrameVulkanActive(),
			isMigratedGuiDisabledForDiagnostics(),
			isMigratedGuiLegacyControl()
		);
	}

	public static GuiExecutionRoute selectExecutionRouteForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled,
		boolean diagnosticLegacyControl
	) {
		return selectExecutionRoute(vulkanBackendSelected, wholeFrameVulkanEnabled, diagnosticsDisabled, diagnosticLegacyControl);
	}

	private static GuiExecutionRoute selectExecutionRoute(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled,
		boolean diagnosticLegacyControl
	) {
		if (diagnosticsDisabled) {
			return GuiExecutionRoute.DISABLED;
		}
		if (diagnosticLegacyControl) {
			return GuiExecutionRoute.JAVA_COMPATIBILITY;
		}
		if (vulkanBackendSelected) {
			return wholeFrameVulkanEnabled
				? GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
				: GuiExecutionRoute.JAVA_COMPATIBILITY;
		}
		return GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT;
	}

	public static boolean shouldDrawJavaCompatibilityGui() {
		return currentExecutionRoute().usesJavaCompatibility();
	}

	public static void enqueueCrosshair(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.CROSSHAIR, CROSSHAIR_PRODUCER, -1, x, y, width, height);
	}

	public static void enqueueHotbarBase(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.HOTBAR_BASE, HOTBAR_BASE_PRODUCER, -1, x, y, width, height);
	}

	public static void enqueueHotbarSelection(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int selectedSlot,
		int x,
		int y,
		int width,
		int height
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (selectedSlot < 0 || selectedSlot > 8) {
			throw new IllegalArgumentException("selected hotbar slot must be in 0..8: " + selectedSlot);
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.HOTBAR_SELECTION, HOTBAR_SELECTION_PRODUCER, selectedSlot, x, y, width, height);
	}

	public static void enqueueExperienceBar(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		int width,
		int height,
		float progressFraction,
		int filledWidth
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(progressFraction)) {
			throw new IllegalArgumentException("experience progress fraction must be finite: " + progressFraction);
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("experience bar dimensions must be positive: " + width + "x" + height);
		}
		if (filledWidth < 0 || filledWidth > width + 1) {
			throw new IllegalArgumentException("experience bar filled width is outside the vanilla range: " + filledWidth);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.EXPERIENCE_BAR_BACKGROUND,
			EXPERIENCE_BACKGROUND_PRODUCER,
			-1,
			progressFraction,
			GuiFillDirection.NONE,
			x,
			y,
			width,
			height
		);
		if (filledWidth > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.EXPERIENCE_BAR_PROGRESS,
				EXPERIENCE_PROGRESS_PRODUCER,
				-1,
				progressFraction,
				GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
				x,
				y,
				filledWidth,
				height
			);
		}
	}

	public static void enqueueCrosshairAttackIndicator(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		float cooldownProgress,
		int filledWidth,
		boolean fullIndicator
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(cooldownProgress)) {
			throw new IllegalArgumentException("crosshair attack indicator progress must be finite: " + cooldownProgress);
		}
		if (fullIndicator) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.CROSSHAIR_ATTACK_FULL,
				ATTACK_CROSSHAIR_PROGRESS_PRODUCER,
				-1,
				Math.max(0.0F, Math.min(1.0F, cooldownProgress)),
				GuiFillDirection.NONE,
				x,
				y,
				16,
				16
			);
			return;
		}
		if (filledWidth < 0 || filledWidth > 16) {
			throw new IllegalArgumentException("crosshair attack indicator filled width must be in 0..16: " + filledWidth);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.CROSSHAIR_ATTACK_BACKGROUND,
			ATTACK_CROSSHAIR_BACKGROUND_PRODUCER,
			-1,
			cooldownProgress,
			GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
			x,
			y,
			16,
			4
		);
		if (filledWidth > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.CROSSHAIR_ATTACK_PROGRESS,
				ATTACK_CROSSHAIR_PROGRESS_PRODUCER,
				-1,
				cooldownProgress,
				GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
				x,
				y,
				filledWidth,
				4
			);
		}
	}

	public static void enqueueHotbarAttackIndicator(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		float cooldownProgress,
		int filledHeight
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(cooldownProgress)) {
			throw new IllegalArgumentException("hotbar attack indicator progress must be finite: " + cooldownProgress);
		}
		if (filledHeight < 0 || filledHeight > 18) {
			throw new IllegalArgumentException("hotbar attack indicator filled height must be in 0..18: " + filledHeight);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.HOTBAR_ATTACK_BACKGROUND,
			ATTACK_HOTBAR_BACKGROUND_PRODUCER,
			-1,
			cooldownProgress,
			GuiFillDirection.VERTICAL_BOTTOM_TO_TOP,
			x,
			y,
			18,
			18
		);
		if (filledHeight > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.HOTBAR_ATTACK_PROGRESS,
				ATTACK_HOTBAR_PROGRESS_PRODUCER,
				-1,
				cooldownProgress,
				GuiFillDirection.VERTICAL_BOTTOM_TO_TOP,
				x,
				y + 18 - filledHeight,
				18,
				filledHeight
			);
		}
	}

	public static void enqueueArmorIcons(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int armorValue, int x, int y) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (armorValue < 0 || armorValue > 20) {
			throw new IllegalArgumentException("armor value must be in 0..20: " + armorValue);
		}
		if (armorValue == 0) {
			return;
		}
		for (int icon = 0; icon < 10; icon++) {
			ArmorIconState state = armorIconState(armorValue, icon);
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				armorIconSprite(state),
				ARMOR_ICON_PRODUCER + "." + state.id() + ".slot" + icon,
				icon,
				armorValue / 20.0F,
				GuiFillDirection.NONE,
				x + icon * 8,
				y,
				9,
				9
			);
		}
	}

	public static ArmorIconState armorIconStateForTests(int armorValue, int iconIndex) {
		return armorIconState(armorValue, iconIndex);
	}

	public static void enqueuePlayerHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<PlayerHeartRequest> hearts
	) {
		if (!isMigratedGuiEnabled() || hearts.isEmpty()) {
			return;
		}
		for (PlayerHeartRequest heart : hearts) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				playerHeartSprite(heart),
				PLAYER_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9
			);
		}
	}

	public static void enqueueAbsorptionHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<AbsorptionHeartRequest> hearts
	) {
		if (!isMigratedGuiEnabled() || hearts.isEmpty()) {
			return;
		}
		for (AbsorptionHeartRequest heart : hearts) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				absorptionHeartSprite(heart),
				ABSORPTION_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9
			);
		}
	}

	public static void enqueueHungerIcons(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<HungerIconRequest> icons
	) {
		if (!isMigratedGuiEnabled() || icons.isEmpty()) {
			return;
		}
		for (HungerIconRequest icon : icons) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				hungerIconSprite(icon),
				HUNGER_ICON_PRODUCER + "." + icon.variant().id() + "." + icon.state().id() + ".order" + icon.order(),
				icon.order(),
				icon.state().progressValue(),
				GuiFillDirection.NONE,
				icon.x(),
				icon.y(),
				9,
				9
			);
		}
	}

	public static void enqueueAirBubbles(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<AirBubbleRequest> bubbles
	) {
		if (!isMigratedGuiEnabled() || bubbles.isEmpty()) {
			return;
		}
		for (AirBubbleRequest bubble : bubbles) {
			if (!bubble.visible()) {
				continue;
			}
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				airBubbleSprite(bubble),
				AIR_BUBBLE_PRODUCER + "." + bubble.state().id() + (bubble.popping() ? ".popping" : "") + ".order" + bubble.order(),
				bubble.order(),
				bubble.state().progressValue(),
				GuiFillDirection.NONE,
				bubble.x(),
				bubble.y(),
				9,
				9
			);
		}
	}

	public static void enqueueMountHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<MountHeartRequest> hearts
	) {
		if (!isMigratedGuiEnabled() || hearts.isEmpty()) {
			return;
		}
		for (MountHeartRequest heart : hearts) {
			if (!heart.visible()) {
				continue;
			}
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				mountHeartSprite(heart),
				MOUNT_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".row" + heart.row() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9
			);
		}
	}

	private static ArmorIconState armorIconState(int armorValue, int iconIndex) {
		if (armorValue < 0 || armorValue > 20) {
			throw new IllegalArgumentException("armor value must be in 0..20: " + armorValue);
		}
		if (iconIndex < 0 || iconIndex >= 10) {
			throw new IllegalArgumentException("armor icon index must be in 0..9: " + iconIndex);
		}
		int threshold = iconIndex * 2 + 1;
		if (threshold < armorValue) {
			return ArmorIconState.FULL;
		}
		if (threshold == armorValue) {
			return ArmorIconState.HALF;
		}
		return ArmorIconState.EMPTY;
	}

	private static GuiSprite armorIconSprite(ArmorIconState state) {
		return switch (state) {
			case EMPTY -> GuiSprite.ARMOR_EMPTY;
			case HALF -> GuiSprite.ARMOR_HALF;
			case FULL -> GuiSprite.ARMOR_FULL;
		};
	}

	private static GuiSprite playerHeartSprite(PlayerHeartRequest request) {
		return switch (request.variant()) {
			case CONTAINER -> containerHeartSprite(request.state(), request.hardcore(), request.flashing());
			case NORMAL -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_NORMAL_FULL,
				GuiSprite.HEART_NORMAL_FULL_FLASHING,
				GuiSprite.HEART_NORMAL_HALF,
				GuiSprite.HEART_NORMAL_HALF_FLASHING,
				GuiSprite.HEART_NORMAL_HARDCORE_FULL,
				GuiSprite.HEART_NORMAL_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_NORMAL_HARDCORE_HALF,
				GuiSprite.HEART_NORMAL_HARDCORE_HALF_FLASHING);
			case POISONED -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_POISONED_FULL,
				GuiSprite.HEART_POISONED_FULL_FLASHING,
				GuiSprite.HEART_POISONED_HALF,
				GuiSprite.HEART_POISONED_HALF_FLASHING,
				GuiSprite.HEART_POISONED_HARDCORE_FULL,
				GuiSprite.HEART_POISONED_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_POISONED_HARDCORE_HALF,
				GuiSprite.HEART_POISONED_HARDCORE_HALF_FLASHING);
			case WITHERED -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_WITHERED_FULL,
				GuiSprite.HEART_WITHERED_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HALF,
				GuiSprite.HEART_WITHERED_HALF_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF_FLASHING);
			case FROZEN -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_FROZEN_FULL,
				GuiSprite.HEART_FROZEN_FULL_FLASHING,
				GuiSprite.HEART_FROZEN_HALF,
				GuiSprite.HEART_FROZEN_HALF_FLASHING,
				GuiSprite.HEART_FROZEN_HARDCORE_FULL,
				GuiSprite.HEART_FROZEN_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_FROZEN_HARDCORE_HALF,
				GuiSprite.HEART_FROZEN_HARDCORE_HALF_FLASHING);
		};
	}

	private static GuiSprite absorptionHeartSprite(AbsorptionHeartRequest request) {
		return switch (request.variant()) {
			case CONTAINER -> containerHeartSprite(request.state(), request.hardcore(), request.flashing());
			case ABSORBING -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_ABSORBING_FULL,
				GuiSprite.HEART_ABSORBING_FULL_FLASHING,
				GuiSprite.HEART_ABSORBING_HALF,
				GuiSprite.HEART_ABSORBING_HALF_FLASHING,
				GuiSprite.HEART_ABSORBING_HARDCORE_FULL,
				GuiSprite.HEART_ABSORBING_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_ABSORBING_HARDCORE_HALF,
				GuiSprite.HEART_ABSORBING_HARDCORE_HALF_FLASHING);
			case WITHERED -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_WITHERED_FULL,
				GuiSprite.HEART_WITHERED_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HALF,
				GuiSprite.HEART_WITHERED_HALF_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF_FLASHING);
		};
	}

	private static GuiSprite hungerIconSprite(HungerIconRequest request) {
		return switch (request.variant()) {
			case NORMAL -> switch (request.state()) {
				case EMPTY -> GuiSprite.HUNGER_EMPTY;
				case HALF -> GuiSprite.HUNGER_HALF;
				case FULL -> GuiSprite.HUNGER_FULL;
			};
			case HUNGER_EFFECT -> switch (request.state()) {
				case EMPTY -> GuiSprite.HUNGER_EFFECT_EMPTY;
				case HALF -> GuiSprite.HUNGER_EFFECT_HALF;
				case FULL -> GuiSprite.HUNGER_EFFECT_FULL;
			};
		};
	}

	private static GuiSprite airBubbleSprite(AirBubbleRequest request) {
		return switch (request.state()) {
			case FULL -> GuiSprite.AIR_FULL;
			case PARTIAL -> request.popping() ? GuiSprite.AIR_POPPING : GuiSprite.AIR_FULL;
			case EMPTY -> GuiSprite.AIR_EMPTY;
		};
	}

	private static GuiSprite mountHeartSprite(MountHeartRequest request) {
		return switch (request.state()) {
			case EMPTY -> GuiSprite.HEART_VEHICLE_CONTAINER;
			case HALF -> GuiSprite.HEART_VEHICLE_HALF;
			case FULL -> GuiSprite.HEART_VEHICLE_FULL;
		};
	}

	private static GuiSprite containerHeartSprite(GuiHeartState state, boolean hardcore, boolean flashing) {
		if (state != GuiHeartState.CONTAINER) {
			throw new IllegalArgumentException("container heart variant cannot render " + state);
		}
		if (hardcore) {
			return flashing ? GuiSprite.HEART_CONTAINER_HARDCORE_FLASHING : GuiSprite.HEART_CONTAINER_HARDCORE;
		}
		return flashing ? GuiSprite.HEART_CONTAINER_FLASHING : GuiSprite.HEART_CONTAINER;
	}

	private static GuiSprite filledHeartSprite(
		GuiHeartState state,
		boolean hardcore,
		boolean flashing,
		GuiSprite full,
		GuiSprite fullFlashing,
		GuiSprite half,
		GuiSprite halfFlashing,
		GuiSprite hardcoreFull,
		GuiSprite hardcoreFullFlashing,
		GuiSprite hardcoreHalf,
		GuiSprite hardcoreHalfFlashing
	) {
		return switch (state) {
			case CONTAINER -> throw new IllegalArgumentException("filled heart variant cannot render a container");
			case FULL -> hardcore ? (flashing ? hardcoreFullFlashing : hardcoreFull) : (flashing ? fullFlashing : full);
			case HALF -> hardcore ? (flashing ? hardcoreHalfFlashing : hardcoreHalf) : (flashing ? halfFlashing : half);
		};
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		int x,
		int y,
		int width,
		int height
	) {
		enqueueGuiSprite(minecraft, guiGraphics, sprite, producerId, selectedSlot, -1.0F, GuiFillDirection.NONE, x, y, width, height);
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		GuiFillDirection fillDirection,
		int x,
		int y,
		int width,
		int height
	) {
		long started = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal." + sprite.phaseName + ".java-producer");
		GuiExecutionRoute route = currentExecutionRoute();
		if (!route.usesRustGui()) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalStateException("Rust VulkanicGAL GUI enqueue requested while route is " + route);
		}
		if (width <= 0 || height <= 0 || width > sprite.width || height > sprite.height) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalArgumentException("GUI sprite destination extent is outside " + sprite.name() + ": " + width + "x" + height);
		}
		try {
			synchronized (LOCK) {
				VulkanicGalBridge.GuiSpriteRecord request = new VulkanicGalBridge.GuiSpriteRecord(
					sprite.stratum.order(),
					sprite.semanticId(),
					selectedSlot,
					progressFraction,
					fillDirection.ordinal(),
					0xFFFFFFFF,
					x,
					y,
					width,
						height,
						guiGraphics.guiWidth(),
						guiGraphics.guiHeight()
					);
					RustGalFrameScheduler.Token token = SCHEDULER.enqueue(generation, sprite.stratum.id(), sprite.stratum.order(), request);
					guiGraphics.guiRenderState.submitGuiElement(
						new RustGalGuiElementRenderState(
							token,
							sprite.stratum,
							producerId,
							selectedSlot,
							progressFraction,
							fillDirection,
							x,
							y,
							width,
							height,
							guiGraphics.guiWidth(),
							guiGraphics.guiHeight()
						)
					);
					METRICS.enqueueNanos += elapsedSince(started);
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
		}
	}

	public static void executeFrame(Minecraft minecraft, List<RustGalGuiElementRenderState> elements) {
		if (elements.isEmpty()) {
			return;
		}
		GuiExecutionRoute route = currentExecutionRoute();
		if (route != GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT) {
			throw new IllegalStateException("Rust OpenGL borrowed-context GUI execution requires route " + GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT + "; current route is " + route);
		}
		for (RustGalGuiElementRenderState element : elements) {
			if (!element.stratum().supportedForPartialFrame()) {
				throw new IllegalArgumentException("unsupported Rust GAL GUI stratum: " + element.stratum().id());
			}
		}
		ensureRenderThreadAndContext(minecraft);
		Window window = minecraft.getWindow();
		ensureConfigured(window);
		synchronized (LOCK) {
			List<RustGalFrameScheduler.Token> tokens = elements.stream().map(RustGalGuiElementRenderState::token).toList();
			List<VulkanicGalBridge.GuiSpriteRecord> requests = SCHEDULER.takeAll(tokens, generation);
			executeFrameBatches(window, requests, false, window.getGuiScaledWidth(), window.getGuiScaledHeight());
		}
	}

	public static void executeWholeFrameVulkan(Minecraft minecraft, GuiRenderState renderState) {
		if (!isWholeFrameVulkanActive()) {
			throw new IllegalStateException("Rust Vulkan whole-frame shell requires " + RustGalVulkanWholeFrameMode.propertyName() + "=true and Vulkan backend selection");
		}
		if (!VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Rust Vulkan whole-frame shell requires the Java Vulkan backend selection at startup");
		}
		ensureRenderThreadAndWindowedVulkanContext(minecraft);
		Window window = minecraft.getWindow();
		ensureConfigured(window);
		synchronized (LOCK) {
			List<RustGalFrameScheduler.Token> tokens = new ArrayList<>();
			renderState.forEachElement(element -> {
				if (element instanceof RustGalGuiElementRenderState rustGalElement) {
					tokens.add(rustGalElement.token());
				}
			}, GuiRenderState.TraverseRange.ALL);
			List<VulkanicGalBridge.GuiSpriteRecord> requests = SCHEDULER.takeAll(tokens, generation);
			executeFrameBatches(window, requests, true, window.getGuiScaledWidth(), window.getGuiScaledHeight());
		}
	}

	public static void enqueueBossBar(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		int width,
		int height,
		float progressFraction,
		int filledWidth,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(progressFraction)) {
			throw new IllegalArgumentException("boss bar progress fraction must be finite: " + progressFraction);
		}
		if (color == null || overlay == null) {
			throw new IllegalArgumentException("boss bar color and overlay must be present");
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("boss bar dimensions must be positive: " + width + "x" + height);
		}
		if (filledWidth < 0 || filledWidth > width) {
			throw new IllegalArgumentException("boss bar filled width must be in 0.." + width + ": " + filledWidth);
		}
		enqueueBossBarSprite(minecraft, guiGraphics, bossBarColorBackground(color), BOSS_BAR_BACKGROUND_PRODUCER, color, overlay,
			progressFraction, x, y, width, height);
		if (overlay != BossEvent.BossBarOverlay.PROGRESS) {
			enqueueBossBarSprite(minecraft, guiGraphics, bossBarOverlayBackground(overlay), BOSS_BAR_BACKGROUND_PRODUCER, color, overlay,
				progressFraction, x, y, width, height);
		}
		if (filledWidth > 0) {
			enqueueBossBarSprite(minecraft, guiGraphics, bossBarColorProgress(color), BOSS_BAR_PROGRESS_PRODUCER, color, overlay,
				progressFraction, x, y, filledWidth, height);
			if (overlay != BossEvent.BossBarOverlay.PROGRESS) {
				enqueueBossBarSprite(minecraft, guiGraphics, bossBarOverlayProgress(overlay), BOSS_BAR_PROGRESS_PRODUCER, color, overlay,
					progressFraction, x, y, filledWidth, height);
			}
		}
	}

	private static void enqueueBossBarSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerPrefix,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay,
		float progressFraction,
		int x,
		int y,
		int width,
		int height
	) {
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			sprite,
			producerPrefix + "." + color.getSerializedName() + "." + overlay.getSerializedName() + "." + sprite.semanticSuffix,
			-1,
			progressFraction,
			GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
			x,
			y,
			width,
			height
		);
	}

	public static void resize(int width, int height) {
		synchronized (LOCK) {
			configuredWidth = 0;
			configuredHeight = 0;
			int cancelled = SCHEDULER.cancelAll("resize");
			retireOutstanding(true);
			METRICS.cancellations++;
			METRICS.batchesCancelled += cancelled;
		}
	}

	public static void reload(ResourceManager resourceManager) {
		if (ASSET_UPDATES_DISABLED) {
			auditMessage("Rust VulkanicGAL GUI asset update skipped reason=diagnostic-disabled");
			return;
		}
		List<VulkanicGalBridge.GuiAssetRecord> assets = collectResolvedAssets(resourceManager);
		synchronized (LOCK) {
			generation++;
			assetGeneration++;
			pendingAssets = assets;
			attemptedAssetGeneration = Math.min(attemptedAssetGeneration, uploadedAssetGeneration);
			int cancelled = SCHEDULER.cancelAll("resource-reload");
			METRICS.reloadInvalidations++;
			METRICS.batchesCancelled += cancelled;
			retireOutstanding(true);
			flushPendingAssetsLocked();
		}
	}

	private static List<VulkanicGalBridge.GuiAssetRecord> collectResolvedAssets(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return List.of();
		}
		List<VulkanicGalBridge.GuiAssetRecord> assets = new ArrayList<>();
		for (GuiSprite sprite : GuiSprite.values()) {
			ResourceLocation location = sprite.resourceLocation();
			Optional<Resource> resource = resourceManager.getResource(location);
			if (resource.isEmpty()) {
				auditMessage(
					"Rust VulkanicGAL GUI asset missing"
						+ " sprite=" + sprite.name()
						+ " sprite_id=" + sprite.semanticId()
						+ " path=" + location
						+ " fallback=vanilla"
				);
				continue;
			}
			try (InputStream input = resource.get().open()) {
				byte[] bytes = input.readAllBytes();
				assets.add(new VulkanicGalBridge.GuiAssetRecord(sprite.semanticId(), bytes));
				auditMessage(
					"Rust VulkanicGAL GUI asset resolved"
						+ " sprite=" + sprite.name()
						+ " sprite_id=" + sprite.semanticId()
						+ " path=" + location
						+ " source_pack=" + resource.get().sourcePackId()
						+ " bytes=" + bytes.length
						+ " sha256=" + sha256Hex(bytes)
				);
			} catch (IOException error) {
				LOGGER.warn("Failed to read Rust VulkanicGAL GUI sprite override {}; vanilla fallback remains active for this reload", location, error);
			}
		}
		return assets;
	}

	private static void flushPendingAssetsLocked() {
		if (bridge == null || uploadedAssetGeneration >= assetGeneration || attemptedAssetGeneration >= assetGeneration) {
			return;
		}
		attemptedAssetGeneration = assetGeneration;
		try {
			recordStatus(Operation.GUI_ASSET_UPDATE, bridge.updateGuiAssets(assetGeneration, pendingAssets));
			lastAssetPayloadCount = pendingAssets.size();
			lastAssetPayloadBytes = pendingAssets.stream().mapToLong(asset -> asset.pngBytes().length).sum();
			uploadedAssetGeneration = assetGeneration;
			auditMessage(
				"Rust VulkanicGAL GUI asset update accepted"
					+ " generation=" + assetGeneration
					+ " payloads=" + lastAssetPayloadCount
					+ " payload_bytes=" + lastAssetPayloadBytes
					+ " uploaded_generation=" + uploadedAssetGeneration
			);
		} catch (RuntimeException error) {
			assetUpdateFailures++;
			LOGGER.error(
				"Rust VulkanicGAL GUI asset update failed for generation {}; preserving last valid atlas",
				assetGeneration,
				error
			);
			auditMessage(
				"Rust VulkanicGAL GUI asset update failed"
					+ " generation=" + assetGeneration
					+ " uploaded_generation=" + uploadedAssetGeneration
					+ " failures=" + assetUpdateFailures
					+ " preserve_last_valid=true"
			);
		}
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 digest is unavailable", error);
		}
	}

	public static void cancelPending(String reason) {
		synchronized (LOCK) {
			int cancelled = SCHEDULER.cancelAll(reason);
			METRICS.cancellations++;
			METRICS.batchesCancelled += cancelled;
		}
	}

	public static void shutdown() {
		VulkanicGalBridge existing;
		synchronized (LOCK) {
			int cancelled = SCHEDULER.cancelAll("shutdown");
			existing = bridge;
			retireOutstanding(true);
			auditMessage(metricsAuditLine(0L, METRICS.frames, lastSubmitted, isWholeFrameVulkanEnabled()));
			bridge = null;
			renderThread = null;
			lastSubmitted = 0L;
			lastRetiredSubmission = 0L;
			configuredWidth = 0;
			configuredHeight = 0;
			METRICS.cancellations++;
			METRICS.batchesCancelled += cancelled;
		}
		if (existing != null) {
			try {
				existing.shutdownFrame();
			} finally {
				existing.close();
			}
		}
	}

	public static MetricsSnapshot metricsSnapshot() {
		synchronized (LOCK) {
			return new MetricsSnapshot(
				METRICS.frames,
				METRICS.submissions,
				METRICS.cacheHits,
				METRICS.cacheMisses,
				METRICS.resourceCreates,
				METRICS.resourceDestroys,
				METRICS.ffiCalls,
				METRICS.ffiBytes,
				METRICS.cancellations,
				METRICS.reloadInvalidations,
				METRICS.completionPolls,
				METRICS.completionTimeouts,
				SCHEDULER.pendingCount(),
				METRICS.batchesExecuted,
				METRICS.spriteBatchesExecuted,
				METRICS.packedSpritesExecuted,
				METRICS.batchesCancelled,
				METRICS.contextCreateCalls,
				METRICS.capabilityCalls,
				METRICS.frameConfigureCalls,
				METRICS.frameAcquireCalls,
				METRICS.frameResizeCalls,
				METRICS.framePresentCalls,
				METRICS.resourceBatchCalls,
				METRICS.submitCalls,
				METRICS.completionQueryCalls,
				METRICS.retireCalls,
				METRICS.contextCreateBytes,
				METRICS.capabilityBytes,
				METRICS.frameConfigureBytes,
				METRICS.frameAcquireBytes,
				METRICS.frameResizeBytes,
				METRICS.framePresentBytes,
				METRICS.resourceBatchBytes,
				METRICS.submitBytes,
				METRICS.completionQueryBytes,
				METRICS.retireBytes,
				METRICS.enqueueNanos,
				METRICS.resourceLookupNanos,
				METRICS.resourceCreateNanos,
				METRICS.abiPackingNanos,
				METRICS.frameAcquireNanos,
				METRICS.submitNanos,
				METRICS.framePresentNanos,
				METRICS.retireNanos,
				METRICS.completionQueryNanos,
				METRICS.executeNanos,
				METRICS.commandLists,
				METRICS.commandOps,
				METRICS.backendSubmissions,
				METRICS.backendWaits,
				METRICS.glCalls,
				METRICS.glFlushes,
				METRICS.glFinishes,
				METRICS.glFencesInserted,
				METRICS.glFencesPolled,
				METRICS.glFencesWaited,
				METRICS.glFencesDeleted
			);
		}
	}

	private static void executeFrameBatches(Window window, List<VulkanicGalBridge.GuiSpriteRecord> requests, boolean allowEmpty, int guiWidth, int guiHeight) {
		if (requests.isEmpty() && !allowEmpty) {
			return;
		}
		long executeStarted = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.execute");
		long correlationId = nextCorrelationId++;
		long frameId = 0L;
		long submissionId = 0L;
		boolean executeCounted = false;
		RustGalWorldPrimitiveRenderer.PrimitiveFrame primitiveFrame = null;
		boolean wholeFrameVulkan = allowEmpty && isWholeFrameVulkanActive();
		boolean renderdocFrameCaptureStarted = false;
		try {
			if (wholeFrameVulkan) {
				primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();
				if (!primitiveFrame.segments().isEmpty() || !primitiveFrame.crackQuads().isEmpty()) {
					renderdocFrameCaptureStarted = RenderDocCaptureHook.beginFrameCaptureOnce(
						window,
						"rust-vulkan-whole-frame-world#" + correlationId
							+ "-segments=" + primitiveFrame.segments().size()
							+ "-crackQuads=" + primitiveFrame.crackQuads().size()
					);
				}
			}
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.acquire");
			long acquireStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_ACQUIRE, VulkanicGalBridge.Struct.FRAME_ACQUIRE.byteSize());
			VulkanicGalBridge.AcquiredFrame frame = bridge.acquireFrame(correlationId, window.getWidth(), window.getHeight());
			METRICS.frameAcquireNanos += elapsedSince(acquireStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.acquire");
			frameId = frame.frameId();
			if (wholeFrameVulkan) {
				if (frame.frameTarget() != 0L) {
					METRICS.frameTargetGenerations++;
					if (METRICS.lastFrameTargetIdentity != 0L && METRICS.lastFrameTargetIdentity != frame.frameTargetIdentity()) {
						METRICS.frameTargetIdentityChanges++;
					}
					METRICS.lastFrameTargetGeneration = frame.frameId();
					METRICS.lastFrameTargetIdentity = frame.frameTargetIdentity();
				}
				auditMessage("gal.frame.acquire backend=vulkan correlation=" + correlationId
					+ " frame=" + frameId
					+ " image=" + frame.frameTargetIdentity()
					+ " target=0x" + Long.toUnsignedString(frame.frameTarget(), 16)
					+ " extent=" + frame.width() + "x" + frame.height());
			}
				if (frame.status() == 4 || frame.frameTarget() == 0L) {
					int cancelled = SCHEDULER.cancelFrame(frameId, "acquire-skipped");
					METRICS.cancellations++;
					METRICS.batchesCancelled += cancelled;
					return;
				}
				GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.abi-packing");
				long packingStarted = System.nanoTime();
				int frameGuiWidth = requests.isEmpty() ? guiWidth : requests.get(0).guiWidth();
				int frameGuiHeight = requests.isEmpty() ? guiHeight : requests.get(0).guiHeight();
				VulkanicGalBridge.WholeFrameSubmitResult wholeFrameResult = null;
				VulkanicGalBridge.GuiFrameSubmitResult guiResult = null;
				if (wholeFrameVulkan) {
					if (primitiveFrame == null) {
						primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();
					}
					wholeFrameResult = bridge.submitWholeFrame(
						generation,
						frameId,
						correlationId,
						frame.frameTarget(),
						frameGuiWidth,
						frameGuiHeight,
						primitiveFrame.viewportWidth() <= 0 ? window.getWidth() : primitiveFrame.viewportWidth(),
						primitiveFrame.viewportHeight() <= 0 ? window.getHeight() : primitiveFrame.viewportHeight(),
						primitiveFrame.viewMatrix(),
						primitiveFrame.projectionMatrix(),
						primitiveFrame.segments(),
						primitiveFrame.crackQuads(),
						requests
					);
				} else {
					guiResult = bridge.submitGuiFrame(
						generation,
						frameId,
						frame.frameTarget(),
						frameGuiWidth,
						frameGuiHeight,
						requests
					);
				}
			METRICS.abiPackingNanos += elapsedSince(packingStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.abi-packing");
			recordStatus(Operation.SUBMIT, wholeFrameResult != null ? wholeFrameResult.asStatus() : guiResult.asStatus());
			submissionId = wholeFrameResult != null ? wholeFrameResult.submissionId() : guiResult.submissionId();
			if (wholeFrameVulkan) {
				auditMessage("gal.frame.target.begin backend=vulkan frame=" + frameId
					+ " image=" + frame.frameTargetIdentity()
					+ " extent=" + frame.width() + "x" + frame.height()
					+ " clear=0.063,0.157,0.855,1.000 expected=blue-diagnostic-shell");
				auditMessage("gal.frame.target.present-ready backend=vulkan frame=" + frameId
					+ " image=" + frame.frameTargetIdentity());
			}
			lastSubmitted = Math.max(lastSubmitted, submissionId);
			TracyCompat.message("gal.frame.deferred producer=gui.frame stratum=gui.frame"
				+ " frame=" + frameId + " submission=" + submissionId + " batches=" + requests.size());
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.present");
			long presentStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_PRESENT, VulkanicGalBridge.Struct.FRAME_PRESENT.byteSize());
			VulkanicGalBridge.PresentedFrame presented = bridge.presentFrame(frameId, correlationId, submissionId);
			if (wholeFrameVulkan) {
				auditMessage("gal.frame.present backend=vulkan correlation=" + correlationId
					+ " frame=" + presented.frameId()
					+ " image=" + presented.frameTargetIdentity()
					+ " submission=" + submissionId
					+ " status=" + presented.status());
			}
			METRICS.framePresentNanos += elapsedSince(presentStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.present");
				if (renderdocFrameCaptureStarted) {
					RenderDocCaptureHook.endFrameCaptureOnce(window, "rust-vulkan-whole-frame-outline#" + frameId + "-submission=" + submissionId);
					renderdocFrameCaptureStarted = false;
				}
				METRICS.frames++;
				METRICS.submissions++;
				METRICS.batchesExecuted += requests.size();
				METRICS.spriteBatchesExecuted += wholeFrameResult != null ? wholeFrameResult.spriteBatchCount() : guiResult.spriteBatchCount();
				METRICS.packedSpritesExecuted += wholeFrameResult != null ? wholeFrameResult.spriteCount() : guiResult.spriteCount();
				METRICS.worldPrimitiveBatchesExecuted += wholeFrameResult != null ? wholeFrameResult.worldBatchCount() : 0L;
				METRICS.worldLineSegmentsExecuted += wholeFrameResult != null ? wholeFrameResult.worldSegmentCount() : 0L;
				METRICS.worldLineVerticesExecuted += wholeFrameResult != null ? wholeFrameResult.worldVertexCount() : 0L;
				METRICS.worldPrimitiveDrawsExecuted += wholeFrameResult != null ? wholeFrameResult.worldDrawCount() : 0L;
				METRICS.worldCrackQuadsExecuted += wholeFrameResult != null ? wholeFrameResult.worldCrackQuadCount() : 0L;
				METRICS.worldCrackBatchesExecuted += wholeFrameResult != null ? wholeFrameResult.worldCrackBatchCount() : 0L;
				METRICS.worldCrackDrawsExecuted += wholeFrameResult != null ? wholeFrameResult.worldCrackDrawCount() : 0L;
				METRICS.worldDepthAttachmentCreates += wholeFrameResult != null ? wholeFrameResult.depthAttachmentCreates() : 0L;
				METRICS.worldDepthAttachmentReuses += wholeFrameResult != null ? wholeFrameResult.depthAttachmentReuses() : 0L;
				METRICS.worldDepthAttachmentRetires += wholeFrameResult != null ? wholeFrameResult.depthAttachmentRetires() : 0L;
				METRICS.worldOutlineCacheHits += wholeFrameResult != null ? wholeFrameResult.outlineCacheHits() : 0L;
				METRICS.worldOutlineCacheMisses += wholeFrameResult != null ? wholeFrameResult.outlineCacheMisses() : 0L;
				METRICS.worldCrackCacheHits += wholeFrameResult != null ? wholeFrameResult.crackCacheHits() : 0L;
				METRICS.worldCrackCacheMisses += wholeFrameResult != null ? wholeFrameResult.crackCacheMisses() : 0L;
				METRICS.cacheHits += wholeFrameResult != null ? wholeFrameResult.cacheHits() : guiResult.cacheHits();
				METRICS.cacheMisses += wholeFrameResult != null ? wholeFrameResult.cacheMisses() : guiResult.cacheMisses();
				METRICS.resourceCreates += wholeFrameResult != null ? wholeFrameResult.resourceCreates() : guiResult.resourceCreates();
				retireOutstanding(false);
				auditMessage(metricsAuditLine(requests.size(), frameId, submissionId, wholeFrameResult != null));
				METRICS.executeNanos += elapsedSince(executeStarted);
				executeCounted = true;
				if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
					TracyCompat.message("Rust VulkanicGAL GUI frame executed"
						+ " batches=" + requests.size()
						+ " spriteBatches=" + (wholeFrameResult != null ? wholeFrameResult.spriteBatchCount() : guiResult.spriteBatchCount())
						+ " frame=" + frameId
						+ " submission=" + submissionId);
				}
		} finally {
			if (renderdocFrameCaptureStarted) {
				RenderDocCaptureHook.endFrameCaptureOnce(window, "rust-vulkan-whole-frame-outline#aborted");
			}
			if (!executeCounted) {
				METRICS.executeNanos += elapsedSince(executeStarted);
			}
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.execute");
		}
	}

	public static GuiSprite debugArmorSpriteForTests(ArmorIconState state) {
		return armorIconSprite(state);
	}

	private static void retireOutstanding(boolean force) {
		if (bridge == null || lastSubmitted == 0L || lastSubmitted <= lastRetiredSubmission) {
			return;
		}
		if (!force) {
			return;
		}
		long started = System.nanoTime();
		recordStatus(Operation.RETIRE, bridge.retire(lastSubmitted));
		METRICS.retireNanos += elapsedSince(started);
		lastRetiredSubmission = lastSubmitted;
	}

	private static long elapsedSince(long started) {
		return Math.max(0L, System.nanoTime() - started);
	}

	private static void ensureRenderThreadAndContext(Minecraft minecraft) {
		Thread current = Thread.currentThread();
		if (renderThread == null) {
			renderThread = current;
		} else if (renderThread != current) {
			throw new IllegalStateException("Rust VulkanicGAL deferred frame queue used from the wrong render thread");
		}
		Window window = minecraft.getWindow();
		if (!VulkanicGalBridge.isBorrowedOpenGlContextCurrent(window)) {
			throw new IllegalStateException("Rust VulkanicGAL deferred OpenGL execution requires Minecraft's current GL context");
		}
		if (bridge == null) {
			bridge = VulkanicGalBridge.createBorrowedOpenGl(window);
			recordFixedOperation(Operation.CONTEXT_CREATE, VulkanicGalBridge.Struct.BORROWED_OPENGL_CONTEXT_CREATE.byteSize());
			recordFixedOperation(Operation.CAPABILITY_QUERY, VulkanicGalBridge.Struct.CAPABILITY_QUERY.byteSize());
			flushPendingAssetsLocked();
			configuredWidth = 0;
			configuredHeight = 0;
		}
	}

	private static void ensureRenderThreadAndWindowedVulkanContext(Minecraft minecraft) {
		Thread current = Thread.currentThread();
		if (renderThread == null) {
			renderThread = current;
		} else if (renderThread != current) {
			throw new IllegalStateException("Rust VulkanicGAL whole-frame queue used from the wrong render thread");
		}
		Window window = minecraft.getWindow();
		if (bridge == null) {
			bridge = VulkanicGalBridge.createWindowedVulkan(
				window,
				Math.max(1, window.getWidth()),
				Math.max(1, window.getHeight())
			);
			recordFixedOperation(Operation.CONTEXT_CREATE, VulkanicGalBridge.Struct.WINDOWED_VULKAN_CONTEXT_CREATE.byteSize());
			recordFixedOperation(Operation.CAPABILITY_QUERY, VulkanicGalBridge.Struct.CAPABILITY_QUERY.byteSize());
			flushPendingAssetsLocked();
			configuredWidth = 0;
			configuredHeight = 0;
		}
	}

	private static void ensureConfigured(Window window) {
		int width = Math.max(1, window.getWidth());
		int height = Math.max(1, window.getHeight());
		if (configuredWidth == width && configuredHeight == height) {
			return;
		}
		if (configuredWidth == 0 || configuredHeight == 0) {
			String label = isWholeFrameVulkanEnabled() && VulkanicAPI.isVulkanBackendSelected()
				? "minecraft.rust-vulkan.swapchain"
				: "minecraft.borrowed.opengl.default";
			recordStatus(Operation.FRAME_CONFIGURE, bridge.configureFrame(label, width, height, VulkanicGalBridge.FORMAT_RGBA8));
		} else {
			recordFixedOperation(Operation.FRAME_RESIZE, VulkanicGalBridge.Struct.FRAME_RESIZE.byteSize());
			bridge.resizeFrame(nextCorrelationId++, width, height);
		}
		configuredWidth = width;
		configuredHeight = height;
	}

	private static void recordStatus(Operation operation, VulkanicGalBridge.Status status) {
		long ffiCalls = status.ffiCalls();
		long ffiBytes = status.ffiInputBytes();
		recordBackendMetrics(status.backendMetrics());
		long deltaCalls = 0L;
		long deltaBytes = 0L;
		if (ffiCalls >= METRICS.lastContextFfiCalls) {
			deltaCalls = ffiCalls - METRICS.lastContextFfiCalls;
			METRICS.ffiCalls += deltaCalls;
		}
		if (ffiBytes >= METRICS.lastContextFfiBytes) {
			deltaBytes = ffiBytes - METRICS.lastContextFfiBytes;
			METRICS.ffiBytes += deltaBytes;
		}
		addOperation(operation, deltaCalls, deltaBytes);
		METRICS.lastContextFfiCalls = ffiCalls;
		METRICS.lastContextFfiBytes = ffiBytes;
	}

	private static void recordBackendMetrics(VulkanicGalBridge.BackendMetrics metrics) {
		if (metrics == null) {
			return;
		}
		METRICS.commandLists = Math.max(METRICS.commandLists, metrics.commandLists());
		METRICS.commandOps = Math.max(METRICS.commandOps, metrics.commandOps());
		METRICS.backendSubmissions = Math.max(METRICS.backendSubmissions, metrics.backendSubmissions());
		METRICS.backendWaits = Math.max(METRICS.backendWaits, metrics.backendWaits());
		METRICS.glCalls = Math.max(METRICS.glCalls, metrics.glCalls());
		METRICS.glFlushes = Math.max(METRICS.glFlushes, metrics.glFlushes());
		METRICS.glFinishes = Math.max(METRICS.glFinishes, metrics.glFinishes());
		METRICS.glFencesInserted = Math.max(METRICS.glFencesInserted, metrics.glFencesInserted());
		METRICS.glFencesPolled = Math.max(METRICS.glFencesPolled, metrics.glFencesPolled());
		METRICS.glFencesWaited = Math.max(METRICS.glFencesWaited, metrics.glFencesWaited());
		METRICS.glFencesDeleted = Math.max(METRICS.glFencesDeleted, metrics.glFencesDeleted());
	}

	private static void recordFixedOperation(Operation operation, long inputBytes) {
		METRICS.ffiCalls++;
		METRICS.ffiBytes += inputBytes;
		METRICS.lastContextFfiCalls++;
		METRICS.lastContextFfiBytes += inputBytes;
		addOperation(operation, 1L, inputBytes);
	}

	private static void addOperation(Operation operation, long calls, long bytes) {
		switch (operation) {
			case CONTEXT_CREATE -> {
				METRICS.contextCreateCalls += calls;
				METRICS.contextCreateBytes += bytes;
			}
			case CAPABILITY_QUERY -> {
				METRICS.capabilityCalls += calls;
				METRICS.capabilityBytes += bytes;
			}
			case FRAME_CONFIGURE -> {
				METRICS.frameConfigureCalls += calls;
				METRICS.frameConfigureBytes += bytes;
			}
			case FRAME_ACQUIRE -> {
				METRICS.frameAcquireCalls += calls;
				METRICS.frameAcquireBytes += bytes;
			}
			case FRAME_RESIZE -> {
				METRICS.frameResizeCalls += calls;
				METRICS.frameResizeBytes += bytes;
			}
			case FRAME_PRESENT -> {
				METRICS.framePresentCalls += calls;
				METRICS.framePresentBytes += bytes;
			}
			case RESOURCE_BATCH -> {
				METRICS.resourceBatchCalls += calls;
				METRICS.resourceBatchBytes += bytes;
			}
			case SUBMIT -> {
				METRICS.submitCalls += calls;
				METRICS.submitBytes += bytes;
			}
			case COMPLETION_QUERY -> {
				METRICS.completionQueryCalls += calls;
				METRICS.completionQueryBytes += bytes;
			}
			case RETIRE -> {
				METRICS.retireCalls += calls;
				METRICS.retireBytes += bytes;
			}
			case GUI_ASSET_UPDATE -> {
				METRICS.guiAssetUpdateCalls += calls;
				METRICS.guiAssetUpdateBytes += bytes;
			}
		}
	}

	private static void auditMessage(String message) {
		if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			System.out.println("[MattMC graphics audit] " + message);
		}
	}

	public static String currentAuditMetricsLine() {
		synchronized (LOCK) {
			return metricsAuditLine(0L, METRICS.frames, lastSubmitted, isWholeFrameVulkanEnabled());
		}
	}

	private static String metricsAuditLine(long frameBatchCount, long frameId, long submissionId, boolean wholeFrameVulkan) {
		return auditBackendPrefix(wholeFrameVulkan) + " GUI frame executed producer=gui.frame"
			+ " stratum=gui.frame"
			+ " frame_batch_count=" + frameBatchCount
			+ " frame=" + frameId
			+ " submission=" + submissionId
			+ " rust_gal_cache_hits=" + METRICS.cacheHits
			+ " rust_gal_cache_misses=" + METRICS.cacheMisses
			+ " rust_gal_queue_depth=" + SCHEDULER.pendingCount()
			+ " rust_gal_frames_executed=" + METRICS.frames
			+ " rust_gal_batches_executed=" + METRICS.batchesExecuted
			+ " rust_gal_sprite_batches_executed=" + METRICS.spriteBatchesExecuted
			+ " rust_gal_packed_sprites_executed=" + METRICS.packedSpritesExecuted
			+ " rust_gal_world_primitive_batches_executed=" + METRICS.worldPrimitiveBatchesExecuted
			+ " rust_gal_world_line_segments_executed=" + METRICS.worldLineSegmentsExecuted
			+ " rust_gal_world_line_vertices_executed=" + METRICS.worldLineVerticesExecuted
			+ " rust_gal_world_primitive_draws_executed=" + METRICS.worldPrimitiveDrawsExecuted
			+ " rust_gal_world_crack_quads_executed=" + METRICS.worldCrackQuadsExecuted
			+ " rust_gal_world_crack_batches_executed=" + METRICS.worldCrackBatchesExecuted
			+ " rust_gal_world_crack_draws_executed=" + METRICS.worldCrackDrawsExecuted
			+ " rust_gal_world_depth_attachment_creates=" + METRICS.worldDepthAttachmentCreates
			+ " rust_gal_world_depth_attachment_reuses=" + METRICS.worldDepthAttachmentReuses
			+ " rust_gal_world_depth_attachment_retires=" + METRICS.worldDepthAttachmentRetires
			+ " rust_gal_world_outline_cache_hits=" + METRICS.worldOutlineCacheHits
			+ " rust_gal_world_outline_cache_misses=" + METRICS.worldOutlineCacheMisses
			+ " rust_gal_world_crack_cache_hits=" + METRICS.worldCrackCacheHits
			+ " rust_gal_world_crack_cache_misses=" + METRICS.worldCrackCacheMisses
			+ " rust_gal_frame_target_generations=" + METRICS.frameTargetGenerations
			+ " rust_gal_frame_target_identity_changes=" + METRICS.frameTargetIdentityChanges
			+ " rust_gal_last_frame_target_generation=" + METRICS.lastFrameTargetGeneration
			+ " rust_gal_last_frame_target_identity=" + METRICS.lastFrameTargetIdentity
			+ " rust_gal_batches_cancelled=" + METRICS.batchesCancelled
			+ " rust_gal_completion_polls=" + METRICS.completionPolls
			+ " rust_gal_completion_timeouts=" + METRICS.completionTimeouts
			+ " rust_gal_asset_generation=" + assetGeneration
			+ " rust_gal_uploaded_asset_generation=" + uploadedAssetGeneration
			+ " rust_gal_asset_payload_count=" + lastAssetPayloadCount
			+ " rust_gal_asset_payload_bytes=" + lastAssetPayloadBytes
			+ " rust_gal_asset_update_failures=" + assetUpdateFailures
			+ " rust_gal_ffi_context_create_calls=" + METRICS.contextCreateCalls
			+ " rust_gal_ffi_capability_calls=" + METRICS.capabilityCalls
			+ " rust_gal_ffi_frame_configure_calls=" + METRICS.frameConfigureCalls
			+ " rust_gal_ffi_frame_acquire_calls=" + METRICS.frameAcquireCalls
			+ " rust_gal_ffi_frame_resize_calls=" + METRICS.frameResizeCalls
			+ " rust_gal_ffi_frame_present_calls=" + METRICS.framePresentCalls
			+ " rust_gal_ffi_resource_batch_calls=" + METRICS.resourceBatchCalls
			+ " rust_gal_ffi_submit_calls=" + METRICS.submitCalls
			+ " rust_gal_ffi_completion_query_calls=" + METRICS.completionQueryCalls
			+ " rust_gal_ffi_retire_calls=" + METRICS.retireCalls
			+ " rust_gal_ffi_asset_update_calls=" + METRICS.guiAssetUpdateCalls
			+ " rust_gal_ffi_context_create_bytes=" + METRICS.contextCreateBytes
			+ " rust_gal_ffi_capability_bytes=" + METRICS.capabilityBytes
			+ " rust_gal_ffi_frame_configure_bytes=" + METRICS.frameConfigureBytes
			+ " rust_gal_ffi_frame_acquire_bytes=" + METRICS.frameAcquireBytes
			+ " rust_gal_ffi_frame_resize_bytes=" + METRICS.frameResizeBytes
			+ " rust_gal_ffi_frame_present_bytes=" + METRICS.framePresentBytes
			+ " rust_gal_ffi_resource_batch_bytes=" + METRICS.resourceBatchBytes
			+ " rust_gal_ffi_submit_bytes=" + METRICS.submitBytes
			+ " rust_gal_ffi_completion_query_bytes=" + METRICS.completionQueryBytes
			+ " rust_gal_ffi_retire_bytes=" + METRICS.retireBytes
			+ " rust_gal_ffi_asset_update_bytes=" + METRICS.guiAssetUpdateBytes
			+ " rust_gal_enqueue_nanos=" + METRICS.enqueueNanos
			+ " rust_gal_resource_lookup_nanos=" + METRICS.resourceLookupNanos
			+ " rust_gal_resource_create_nanos=" + METRICS.resourceCreateNanos
			+ " rust_gal_abi_packing_nanos=" + METRICS.abiPackingNanos
			+ " rust_gal_frame_acquire_nanos=" + METRICS.frameAcquireNanos
			+ " rust_gal_submit_nanos=" + METRICS.submitNanos
			+ " rust_gal_frame_present_nanos=" + METRICS.framePresentNanos
			+ " rust_gal_retire_nanos=" + METRICS.retireNanos
			+ " rust_gal_completion_query_nanos=" + METRICS.completionQueryNanos
			+ " rust_gal_execute_nanos=" + METRICS.executeNanos
			+ " rust_gal_command_lists=" + METRICS.commandLists
			+ " rust_gal_command_ops=" + METRICS.commandOps
			+ " rust_gal_backend_submissions=" + METRICS.backendSubmissions
			+ " rust_gal_backend_waits=" + METRICS.backendWaits
			+ " rust_gal_gl_calls=" + METRICS.glCalls
			+ " rust_gal_gl_flushes=" + METRICS.glFlushes
			+ " rust_gal_gl_finishes=" + METRICS.glFinishes
			+ " rust_gal_gl_fences_inserted=" + METRICS.glFencesInserted
			+ " rust_gal_gl_fences_polled=" + METRICS.glFencesPolled
			+ " rust_gal_gl_fences_waited=" + METRICS.glFencesWaited
			+ " rust_gal_gl_fences_deleted=" + METRICS.glFencesDeleted
			+ " ffi_call_count=" + METRICS.ffiCalls
			+ " ffi_bytes=" + METRICS.ffiBytes;
	}

	private static String auditBackendPrefix(boolean wholeFrameVulkan) {
		return wholeFrameVulkan
			? "Rust VulkanicGAL"
			: "Rust OpenGL VulkanicGAL";
	}

	private static GuiSprite bossBarColorBackground(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> GuiSprite.BOSS_BAR_PINK_BACKGROUND;
			case BLUE -> GuiSprite.BOSS_BAR_BLUE_BACKGROUND;
			case RED -> GuiSprite.BOSS_BAR_RED_BACKGROUND;
			case GREEN -> GuiSprite.BOSS_BAR_GREEN_BACKGROUND;
			case YELLOW -> GuiSprite.BOSS_BAR_YELLOW_BACKGROUND;
			case PURPLE -> GuiSprite.BOSS_BAR_PURPLE_BACKGROUND;
			case WHITE -> GuiSprite.BOSS_BAR_WHITE_BACKGROUND;
		};
	}

	private static GuiSprite bossBarColorProgress(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> GuiSprite.BOSS_BAR_PINK_PROGRESS;
			case BLUE -> GuiSprite.BOSS_BAR_BLUE_PROGRESS;
			case RED -> GuiSprite.BOSS_BAR_RED_PROGRESS;
			case GREEN -> GuiSprite.BOSS_BAR_GREEN_PROGRESS;
			case YELLOW -> GuiSprite.BOSS_BAR_YELLOW_PROGRESS;
			case PURPLE -> GuiSprite.BOSS_BAR_PURPLE_PROGRESS;
			case WHITE -> GuiSprite.BOSS_BAR_WHITE_PROGRESS;
		};
	}

	private static GuiSprite bossBarOverlayBackground(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> throw new IllegalArgumentException("progress boss overlay has no notch background sprite");
			case NOTCHED_6 -> GuiSprite.BOSS_BAR_NOTCHED_6_BACKGROUND;
			case NOTCHED_10 -> GuiSprite.BOSS_BAR_NOTCHED_10_BACKGROUND;
			case NOTCHED_12 -> GuiSprite.BOSS_BAR_NOTCHED_12_BACKGROUND;
			case NOTCHED_20 -> GuiSprite.BOSS_BAR_NOTCHED_20_BACKGROUND;
		};
	}

	private static GuiSprite bossBarOverlayProgress(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> throw new IllegalArgumentException("progress boss overlay has no notch progress sprite");
			case NOTCHED_6 -> GuiSprite.BOSS_BAR_NOTCHED_6_PROGRESS;
			case NOTCHED_10 -> GuiSprite.BOSS_BAR_NOTCHED_10_PROGRESS;
			case NOTCHED_12 -> GuiSprite.BOSS_BAR_NOTCHED_12_PROGRESS;
			case NOTCHED_20 -> GuiSprite.BOSS_BAR_NOTCHED_20_PROGRESS;
		};
	}

	enum TextureGroup {
		GUI_ALPHA("gui-textured-alpha-atlas", "gui-alpha", false),
		GUI_INVERT("gui-textured-invert-atlas", "gui-invert", true);

		final String cacheKind;
		final String semanticId;
		final boolean invertBlend;

		TextureGroup(String cacheKind, String semanticId, boolean invertBlend) {
			this.cacheKind = cacheKind;
			this.semanticId = semanticId;
			this.invertBlend = invertBlend;
		}
	}

	enum GuiSprite {
		CROSSHAIR(
			GuiRenderStratum.GUI_CROSSHAIR,
			"crosshair",
			"gui-textured-invert-crosshair",
			"/assets/minecraft/textures/gui/sprites/hud/crosshair.png",
			15,
			15,
			true
		),
		HOTBAR_BASE(
			GuiRenderStratum.GUI_HOTBAR_BASE,
			"hotbar-base",
			"gui-textured-alpha-hotbar-base",
			"/assets/minecraft/textures/gui/sprites/hud/hotbar.png",
			182,
			22,
			false
		),
			HOTBAR_SELECTION(
				GuiRenderStratum.GUI_HOTBAR_SELECTION,
				"hotbar-selection",
				"gui-textured-alpha-hotbar-selection",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
				24,
				23,
				false
			),
			ARMOR_EMPTY(
				GuiRenderStratum.GUI_ARMOR,
				"armor-empty",
				"gui-textured-alpha-armor-empty",
				"/assets/minecraft/textures/gui/sprites/hud/armor_empty.png",
				9,
				9,
				false
			),
			ARMOR_HALF(
				GuiRenderStratum.GUI_ARMOR,
				"armor-half",
				"gui-textured-alpha-armor-half",
				"/assets/minecraft/textures/gui/sprites/hud/armor_half.png",
				9,
				9,
				false
			),
				ARMOR_FULL(
					GuiRenderStratum.GUI_ARMOR,
					"armor-full",
					"gui-textured-alpha-armor-full",
					"/assets/minecraft/textures/gui/sprites/hud/armor_full.png",
					9,
					9,
					false
				),
				HEART_CONTAINER(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_HARDCORE(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-hardcore",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_HARDCORE_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-hardcore-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/full.png",
					9,
					9,
					false
				),
				HEART_NORMAL_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/half.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png",
					9,
					9,
					false
				),
				HEART_POISONED_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png",
					9,
					9,
					false
				),
				HEART_POISONED_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png",
					9,
					9,
					false
				),
				HEART_WITHERED_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png",
					9,
					9,
					false
				),
				HEART_FROZEN_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				EXPERIENCE_BAR_BACKGROUND(
			GuiRenderStratum.GUI_EXPERIENCE_BAR_BACKGROUND,
			"experience-background",
			"gui-textured-alpha-experience-background",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png",
			182,
			5,
			false
		),
			EXPERIENCE_BAR_PROGRESS(
				GuiRenderStratum.GUI_EXPERIENCE_BAR_PROGRESS,
				"experience-progress",
			"gui-textured-alpha-experience-progress",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png",
				182,
				5,
				false
			),
			CROSSHAIR_ATTACK_FULL(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-full",
				"gui-textured-alpha-attack-crosshair-full",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png",
				16,
				16,
				false
			),
			CROSSHAIR_ATTACK_BACKGROUND(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_BACKGROUND,
				"attack-crosshair-background",
				"gui-textured-alpha-attack-crosshair-background",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png",
				16,
				4,
				false
			),
			CROSSHAIR_ATTACK_PROGRESS(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-progress",
				"gui-textured-alpha-attack-crosshair-progress",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png",
				16,
				4,
				false
			),
			HOTBAR_ATTACK_BACKGROUND(
				GuiRenderStratum.GUI_ATTACK_HOTBAR_BACKGROUND,
				"attack-hotbar-background",
				"gui-textured-alpha-attack-hotbar-background",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png",
				18,
				18,
				false
			),
			HOTBAR_ATTACK_PROGRESS(
				GuiRenderStratum.GUI_ATTACK_HOTBAR_PROGRESS,
				"attack-hotbar-progress",
				"gui-textured-alpha-attack-hotbar-progress",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png",
				18,
				18,
				false
			),
			BOSS_BAR_PINK_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-pink-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-blue-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-red-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-green-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-yellow-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-purple-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-white-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PINK_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-pink-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-blue-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-red-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-green-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-yellow-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-purple-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-white-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-6-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-10-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-12-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-20-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-6-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-10-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-12-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-20-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png",
				182,
				5,
				false
			),
			HUNGER_EMPTY(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-empty",
				"/assets/minecraft/textures/gui/sprites/hud/food_empty.png",
				9,
				9,
				false
			),
			HUNGER_HALF(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-half",
				"/assets/minecraft/textures/gui/sprites/hud/food_half.png",
				9,
				9,
				false
			),
			HUNGER_FULL(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-full",
				"/assets/minecraft/textures/gui/sprites/hud/food_full.png",
				9,
				9,
				false
			),
			HUNGER_EFFECT_EMPTY(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-effect-empty",
				"/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png",
				9,
				9,
				false
			),
			HUNGER_EFFECT_HALF(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-effect-half",
				"/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png",
				9,
				9,
				false
			),
			HUNGER_EFFECT_FULL(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-effect-full",
				"/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png",
				9,
				9,
				false
			),
			AIR_FULL(
				GuiRenderStratum.GUI_AIR,
				"air",
				"gui-textured-alpha-air-full",
				"/assets/minecraft/textures/gui/sprites/hud/air.png",
				9,
				9,
				false
			),
			AIR_POPPING(
				GuiRenderStratum.GUI_AIR,
				"air",
				"gui-textured-alpha-air-popping",
				"/assets/minecraft/textures/gui/sprites/hud/air_bursting.png",
				9,
				9,
				false
			),
			AIR_EMPTY(
				GuiRenderStratum.GUI_AIR,
				"air",
				"gui-textured-alpha-air-empty",
				"/assets/minecraft/textures/gui/sprites/hud/air_empty.png",
				9,
				9,
				false
			),
			HEART_VEHICLE_CONTAINER(
				GuiRenderStratum.GUI_MOUNT_HEALTH,
				"mount-health",
				"gui-textured-alpha-heart-vehicle-container",
				"/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png",
				9,
				9,
				false
			),
			HEART_VEHICLE_FULL(
				GuiRenderStratum.GUI_MOUNT_HEALTH,
				"mount-health",
				"gui-textured-alpha-heart-vehicle-full",
				"/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png",
				9,
				9,
				false
			),
			HEART_VEHICLE_HALF(
				GuiRenderStratum.GUI_MOUNT_HEALTH,
				"mount-health",
				"gui-textured-alpha-heart-vehicle-half",
				"/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png",
				9,
				9,
				false
			);

		final GuiRenderStratum stratum;
		final String phaseName;
		final String cacheKind;
		final String semanticSuffix;
		final String textureResource;
		final int width;
		final int height;
		final TextureGroup textureGroup;

		GuiSprite(GuiRenderStratum stratum, String phaseName, String cacheKind, String textureResource, int width, int height, boolean invertBlend) {
			this.stratum = stratum;
			this.phaseName = phaseName;
			this.cacheKind = cacheKind;
			this.semanticSuffix = semanticSuffix(cacheKind);
			this.textureResource = textureResource;
			this.width = width;
			this.height = height;
			this.textureGroup = invertBlend ? TextureGroup.GUI_INVERT : TextureGroup.GUI_ALPHA;
		}

		int textureBytes() {
			return this.width * this.height * 4;
		}

		int semanticId() {
			return ordinal() + 1;
		}

		ResourceLocation resourceLocation() {
			String prefix = "/assets/minecraft/";
			if (!this.textureResource.startsWith(prefix)) {
				throw new IllegalStateException("unexpected GUI sprite resource path: " + this.textureResource);
			}
			return ResourceLocation.withDefaultNamespace(this.textureResource.substring(prefix.length()));
		}

		private static String semanticSuffix(String cacheKind) {
			if (cacheKind.startsWith("gui-textured-alpha-")) {
				return cacheKind.substring("gui-textured-alpha-".length()).replace('_', '-');
			}
			if (cacheKind.startsWith("gui-textured-invert-")) {
				return cacheKind.substring("gui-textured-invert-".length()).replace('_', '-');
			}
			return cacheKind.replace('_', '-');
		}
	}

	private enum Operation {
		CONTEXT_CREATE,
		CAPABILITY_QUERY,
		FRAME_CONFIGURE,
		FRAME_ACQUIRE,
		FRAME_RESIZE,
		FRAME_PRESENT,
		RESOURCE_BATCH,
		SUBMIT,
		COMPLETION_QUERY,
		RETIRE,
		GUI_ASSET_UPDATE
	}

	private static final class Metrics {
		long frames;
		long submissions;
		long cacheHits;
		long cacheMisses;
		long resourceCreates;
		long resourceDestroys;
		long ffiCalls;
		long ffiBytes;
		long lastContextFfiCalls;
		long lastContextFfiBytes;
		long cancellations;
		long reloadInvalidations;
		long completionPolls;
		long completionTimeouts;
		long batchesExecuted;
		long spriteBatchesExecuted;
		long packedSpritesExecuted;
		long worldPrimitiveBatchesExecuted;
		long worldLineSegmentsExecuted;
		long worldLineVerticesExecuted;
		long worldPrimitiveDrawsExecuted;
		long worldCrackQuadsExecuted;
		long worldCrackBatchesExecuted;
		long worldCrackDrawsExecuted;
		long worldDepthAttachmentCreates;
		long worldDepthAttachmentReuses;
		long worldDepthAttachmentRetires;
		long worldOutlineCacheHits;
		long worldOutlineCacheMisses;
		long worldCrackCacheHits;
		long worldCrackCacheMisses;
		long frameTargetGenerations;
		long frameTargetIdentityChanges;
		long lastFrameTargetGeneration;
		long lastFrameTargetIdentity;
		long batchesCancelled;
		long contextCreateCalls;
		long capabilityCalls;
		long frameConfigureCalls;
		long frameAcquireCalls;
		long frameResizeCalls;
		long framePresentCalls;
		long resourceBatchCalls;
		long submitCalls;
		long completionQueryCalls;
		long retireCalls;
		long guiAssetUpdateCalls;
		long contextCreateBytes;
		long capabilityBytes;
		long frameConfigureBytes;
		long frameAcquireBytes;
		long frameResizeBytes;
		long framePresentBytes;
		long resourceBatchBytes;
		long submitBytes;
		long completionQueryBytes;
		long retireBytes;
		long guiAssetUpdateBytes;
		long enqueueNanos;
		long resourceLookupNanos;
		long resourceCreateNanos;
		long abiPackingNanos;
		long frameAcquireNanos;
		long submitNanos;
		long framePresentNanos;
		long retireNanos;
		long completionQueryNanos;
		long executeNanos;
		long commandLists;
		long commandOps;
		long backendSubmissions;
		long backendWaits;
		long glCalls;
		long glFlushes;
		long glFinishes;
		long glFencesInserted;
		long glFencesPolled;
		long glFencesWaited;
		long glFencesDeleted;
	}

	public record MetricsSnapshot(
		long frames,
		long submissions,
		long cacheHits,
		long cacheMisses,
		long resourceCreates,
		long resourceDestroys,
		long ffiCalls,
		long ffiBytes,
		long cancellations,
		long reloadInvalidations,
		long completionPolls,
		long completionTimeouts,
			long pendingBatches,
			long batchesExecuted,
			long spriteBatchesExecuted,
			long packedSpritesExecuted,
			long batchesCancelled,
		long contextCreateCalls,
		long capabilityCalls,
		long frameConfigureCalls,
		long frameAcquireCalls,
		long frameResizeCalls,
		long framePresentCalls,
		long resourceBatchCalls,
		long submitCalls,
		long completionQueryCalls,
		long retireCalls,
		long contextCreateBytes,
		long capabilityBytes,
		long frameConfigureBytes,
		long frameAcquireBytes,
		long frameResizeBytes,
		long framePresentBytes,
		long resourceBatchBytes,
		long submitBytes,
		long completionQueryBytes,
		long retireBytes,
		long enqueueNanos,
		long resourceLookupNanos,
		long resourceCreateNanos,
		long abiPackingNanos,
		long frameAcquireNanos,
		long submitNanos,
		long framePresentNanos,
		long retireNanos,
		long completionQueryNanos,
		long executeNanos,
		long commandLists,
		long commandOps,
		long backendSubmissions,
		long backendWaits,
		long glCalls,
		long glFlushes,
		long glFinishes,
		long glFencesInserted,
		long glFencesPolled,
		long glFencesWaited,
		long glFencesDeleted
	) {
	}

}
