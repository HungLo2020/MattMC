package net.minecraft.client;

import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.ClipboardManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.debug.DebugOptionsScreen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.OpenFile;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.VersionCommand;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.ProblemReporter.ScopedCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class KeyboardHandler {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int DEBUG_CRASH_TIME = 10000;
	private final Minecraft minecraft;
	private final ClipboardManager clipboardManager = new ClipboardManager();
	private long debugCrashKeyTime = -1L;
	private long debugCrashKeyReportedTime = -1L;
	private long debugCrashKeyReportedCount = -1L;
	private boolean handledDebugKey;

	public KeyboardHandler(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	private boolean handleChunkDebugKeys(KeyEvent keyEvent) {
		switch (keyEvent.key()) {
			case 69:
				if (this.minecraft.player == null) {
					return false;
				}

				boolean bl = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_SECTION_PATHS);
				this.debugFeedbackFormatted("SectionPath: {0}", bl ? "shown" : "hidden");
				return true;
			case 70:
				boolean bl3 = FogRenderer.toggleFog();
				this.debugFeedbackFormatted("Fog: {0}", bl3 ? "enabled" : "disabled");
				return true;
			case 71:
			case 72:
			case 73:
			case 74:
			case 75:
			case 77:
			case 78:
			case 80:
			case 81:
			case 82:
			case 83:
			case 84:
			default:
				return false;
			case 76:
				this.minecraft.smartCull = !this.minecraft.smartCull;
				this.debugFeedbackFormatted("SmartCull: {0}", this.minecraft.smartCull ? "enabled" : "disabled");
				return true;
			case 79:
				if (this.minecraft.player == null) {
					return false;
				}

				boolean bl2 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_SECTION_OCTREE);
				this.debugFeedbackFormatted("Frustum culling Octree: {0}", bl2 ? "enabled" : "disabled");
				return true;
			case 85:
				if (keyEvent.hasShiftDown()) {
					this.minecraft.levelRenderer.killFrustum();
					this.debugFeedbackFormatted("Killed frustum");
				} else {
					this.minecraft.levelRenderer.captureFrustum();
					this.debugFeedbackFormatted("Captured frustum");
				}

				return true;
			case 86:
				if (this.minecraft.player == null) {
					return false;
				}

				boolean bl4 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_SECTION_VISIBILITY);
				this.debugFeedbackFormatted("SectionVisibility: {0}", bl4 ? "enabled" : "disabled");
				return true;
			case 87:
				this.minecraft.wireframe = !this.minecraft.wireframe;
				this.debugFeedbackFormatted("WireFrame: {0}", this.minecraft.wireframe ? "enabled" : "disabled");
				return true;
		}
	}

	private void showDebugChat(Component component) {
		this.minecraft.gui.getChat().addMessage(component);
		this.minecraft.getNarrator().saySystemQueued(component);
	}

	private static Component decorateDebugComponent(ChatFormatting chatFormatting, Component component) {
		return Component.empty()
			.append(Component.translatable("debug.prefix").withStyle(new ChatFormatting[]{chatFormatting, ChatFormatting.BOLD}))
			.append(CommonComponents.SPACE)
			.append(component);
	}

	private void debugWarningComponent(Component component) {
		this.showDebugChat(decorateDebugComponent(ChatFormatting.RED, component));
	}

	private void debugFeedbackComponent(Component component) {
		this.showDebugChat(decorateDebugComponent(ChatFormatting.YELLOW, component));
	}

	private void debugFeedbackTranslated(String string) {
		this.debugFeedbackComponent(Component.translatable(string));
	}

	private void debugFeedbackFormatted(String string, Object... objects) {
		this.debugFeedbackComponent(Component.literal(MessageFormat.format(string, objects)));
	}

	private boolean handleDebugKeys(KeyEvent keyEvent) {
		if (this.debugCrashKeyTime > 0L && this.debugCrashKeyTime < Util.getMillis() - 100L) {
			return true;
		} else if (SharedConstants.DEBUG_HOTKEYS && this.handleChunkDebugKeys(keyEvent)) {
			return true;
		} else {
			if (SharedConstants.DEBUG_FEATURE_COUNT) {
				switch (keyEvent.key()) {
					case 76:
						FeatureCountTracker.logCounts();
						return true;
					case 82:
						FeatureCountTracker.clearCounts();
						return true;
				}
			}

			switch (keyEvent.key()) {
				case 49:
					this.minecraft.getDebugOverlay().toggleProfilerChart();
					return true;
				case 50:
					this.minecraft.getDebugOverlay().toggleFpsCharts();
					return true;
				case 51:
					this.minecraft.getDebugOverlay().toggleNetworkCharts();
					return true;
				case 65:
					this.minecraft.levelRenderer.allChanged();
					this.debugFeedbackTranslated("debug.reload_chunks.message");
					return true;
				case 66:
					if (this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
						boolean bl = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
						this.debugFeedbackTranslated(bl ? "debug.show_hitboxes.on" : "debug.show_hitboxes.off");
						return true;
					}

					return false;
				case 67:
					if (this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
						ClientPacketListener clientPacketListener = this.minecraft.player.connection;
						if (clientPacketListener == null) {
							return false;
						}

						this.debugFeedbackTranslated("debug.copy_location.message");
						this.setClipboard(
							String.format(
								Locale.ROOT,
								"/execute in %s run tp @s %.2f %.2f %.2f %.2f %.2f",
								this.minecraft.player.level().dimension().location(),
								this.minecraft.player.getX(),
								this.minecraft.player.getY(),
								this.minecraft.player.getZ(),
								this.minecraft.player.getYRot(),
								this.minecraft.player.getXRot()
							)
						);
						return true;
					}

					return false;
				case 68:
					if (this.minecraft.gui != null) {
						this.minecraft.gui.getChat().clearMessages(false);
					}

					return true;
				case 71:
					if (this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
						boolean bl2 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_BORDERS);
						this.debugFeedbackTranslated(bl2 ? "debug.chunk_boundaries.on" : "debug.chunk_boundaries.off");
						return true;
					}

					return false;
				case 72:
					this.minecraft.options.advancedItemTooltips = !this.minecraft.options.advancedItemTooltips;
					this.debugFeedbackTranslated(this.minecraft.options.advancedItemTooltips ? "debug.advanced_tooltips.on" : "debug.advanced_tooltips.off");
					this.minecraft.options.save();
					return true;
				case 73:
					if (this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
						this.copyRecreateCommand(this.minecraft.player.hasPermissions(2), !keyEvent.hasShiftDown());
					}

					return true;
				case 76:
					if (this.minecraft.debugClientMetricsStart(this::debugFeedbackComponent)) {
						this.debugFeedbackComponent(Component.translatable("debug.profiling.start", new Object[]{10}));
					}

					return true;
				case 78:
					if (this.minecraft.player == null || !this.minecraft.player.hasPermissions(2)) {
						this.debugFeedbackTranslated("debug.creative_spectator.error");
					} else if (!this.minecraft.player.isSpectator()) {
						this.minecraft.player.connection.send(new ServerboundChangeGameModePacket(GameType.SPECTATOR));
					} else {
						GameType gameType = MoreObjects.firstNonNull(this.minecraft.gameMode.getPreviousPlayerMode(), GameType.CREATIVE);
						this.minecraft.player.connection.send(new ServerboundChangeGameModePacket(gameType));
					}

					return true;
				case 80:
					this.minecraft.options.pauseOnLostFocus = !this.minecraft.options.pauseOnLostFocus;
					this.minecraft.options.save();
					this.debugFeedbackTranslated(this.minecraft.options.pauseOnLostFocus ? "debug.pause_focus.on" : "debug.pause_focus.off");
					return true;
				case 81:
					this.debugFeedbackTranslated("debug.help.message");
					this.showDebugChat(Component.translatable("debug.reload_chunks.help"));
					this.showDebugChat(Component.translatable("debug.show_hitboxes.help"));
					this.showDebugChat(Component.translatable("debug.copy_location.help"));
					this.showDebugChat(Component.translatable("debug.clear_chat.help"));
					this.showDebugChat(Component.translatable("debug.chunk_boundaries.help"));
					this.showDebugChat(Component.translatable("debug.advanced_tooltips.help"));
					this.showDebugChat(Component.translatable("debug.inspect.help"));
					this.showDebugChat(Component.translatable("debug.profiling.help"));
					this.showDebugChat(Component.translatable("debug.creative_spectator.help"));
					this.showDebugChat(Component.translatable("debug.pause_focus.help"));
					this.showDebugChat(Component.translatable("debug.help.help"));
					this.showDebugChat(Component.translatable("debug.dump_dynamic_textures.help"));
					this.showDebugChat(Component.translatable("debug.reload_resourcepacks.help"));
					this.showDebugChat(Component.translatable("debug.version.help"));
					this.showDebugChat(Component.translatable("debug.pause.help"));
					this.showDebugChat(Component.translatable("debug.gamemodes.help"));
					this.showDebugChat(Component.translatable("debug.options.help"));
					return true;
				case 83:
					Path path = this.minecraft.gameDirectory.toPath().toAbsolutePath();
					Path path2 = TextureUtil.getDebugTexturePath(path);
					this.minecraft.getTextureManager().dumpAllSheets(path2);
					Component component = Component.literal(path.relativize(path2).toString())
						.withStyle(ChatFormatting.UNDERLINE)
						.withStyle(style -> style.withClickEvent(new OpenFile(path2)));
					this.debugFeedbackComponent(Component.translatable("debug.dump_dynamic_textures", new Object[]{component}));
					return true;
				case 84:
					this.debugFeedbackTranslated("debug.reload_resourcepacks.message");
					this.minecraft.reloadResourcePacks();
					return true;
				case 86:
					this.debugFeedbackTranslated("debug.version.header");
					VersionCommand.dumpVersion(this::showDebugChat);
					return true;
				case 293:
					if (!this.minecraft.canSwitchGameMode() || !this.minecraft.player.hasPermissions(2)) {
						this.debugFeedbackTranslated("debug.gamemodes.error");
					} else if (!(this.minecraft.screen instanceof WinScreen)) {
						this.minecraft.setScreen(new GameModeSwitcherScreen());
					}

					return true;
				case 295:
					if (this.minecraft.screen instanceof DebugOptionsScreen) {
						this.minecraft.screen.onClose();
					} else if (this.minecraft.canInterruptScreen()) {
						if (this.minecraft.screen != null) {
							this.minecraft.screen.onClose();
						}

						this.minecraft.setScreen(new DebugOptionsScreen());
					}

					return true;
				default:
					return false;
			}
		}
	}

	private void copyRecreateCommand(boolean bl, boolean bl2) {
		HitResult hitResult = this.minecraft.hitResult;
		if (hitResult != null) {
			switch (hitResult.getType()) {
				case BLOCK:
					BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
					Level level = this.minecraft.player.level();
					BlockState blockState = level.getBlockState(blockPos);
					if (bl) {
						if (bl2) {
							this.minecraft.player.connection.getDebugQueryHandler().queryBlockEntityTag(blockPos, compoundTagx -> {
								this.copyCreateBlockCommand(blockState, blockPos, compoundTagx);
								this.debugFeedbackTranslated("debug.inspect.server.block");
							});
						} else {
							BlockEntity blockEntity = level.getBlockEntity(blockPos);
							CompoundTag compoundTag = blockEntity != null ? blockEntity.saveWithoutMetadata(level.registryAccess()) : null;
							this.copyCreateBlockCommand(blockState, blockPos, compoundTag);
							this.debugFeedbackTranslated("debug.inspect.client.block");
						}
					} else {
						this.copyCreateBlockCommand(blockState, blockPos, null);
						this.debugFeedbackTranslated("debug.inspect.client.block");
					}
					break;
				case ENTITY:
					Entity entity = ((EntityHitResult)hitResult).getEntity();
					ResourceLocation resourceLocation = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
					if (bl) {
						if (bl2) {
							this.minecraft.player.connection.getDebugQueryHandler().queryEntityTag(entity.getId(), compoundTagx -> {
								this.copyCreateEntityCommand(resourceLocation, entity.position(), compoundTagx);
								this.debugFeedbackTranslated("debug.inspect.server.entity");
							});
						} else {
							ScopedCollector scopedCollector = new ScopedCollector(entity.problemPath(), LOGGER);

							try {
								TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
								entity.saveWithoutId(tagValueOutput);
								this.copyCreateEntityCommand(resourceLocation, entity.position(), tagValueOutput.buildResult());
							} catch (Throwable var10) {
								try {
									scopedCollector.close();
								} catch (Throwable var9) {
									var10.addSuppressed(var9);
								}

								throw var10;
							}

							scopedCollector.close();
							this.debugFeedbackTranslated("debug.inspect.client.entity");
						}
					} else {
						this.copyCreateEntityCommand(resourceLocation, entity.position(), null);
						this.debugFeedbackTranslated("debug.inspect.client.entity");
					}
			}
		}
	}

	private void copyCreateBlockCommand(BlockState blockState, BlockPos blockPos, @Nullable CompoundTag compoundTag) {
		StringBuilder stringBuilder = new StringBuilder(BlockStateParser.serialize(blockState));
		if (compoundTag != null) {
			stringBuilder.append(compoundTag);
		}

		String string = String.format(Locale.ROOT, "/setblock %d %d %d %s", blockPos.getX(), blockPos.getY(), blockPos.getZ(), stringBuilder);
		this.setClipboard(string);
	}

	private void copyCreateEntityCommand(ResourceLocation resourceLocation, Vec3 vec3, @Nullable CompoundTag compoundTag) {
		String string2;
		if (compoundTag != null) {
			compoundTag.remove("UUID");
			compoundTag.remove("Pos");
			String string = NbtUtils.toPrettyComponent(compoundTag).getString();
			string2 = String.format(Locale.ROOT, "/summon %s %.2f %.2f %.2f %s", resourceLocation, vec3.x, vec3.y, vec3.z, string);
		} else {
			string2 = String.format(Locale.ROOT, "/summon %s %.2f %.2f %.2f", resourceLocation, vec3.x, vec3.y, vec3.z);
		}

		this.setClipboard(string2);
	}

	private void keyPress(long l, int i, KeyEvent keyEvent) {
		Window window = this.minecraft.getWindow();
		if (l == window.handle()) {
			this.minecraft.getFramerateLimitTracker().onInputReceived();
			boolean bl = InputConstants.isKeyDown(window, 292);
			if (this.debugCrashKeyTime > 0L) {
				if (!InputConstants.isKeyDown(window, 67) || !bl) {
					this.debugCrashKeyTime = -1L;
				}
			} else if (InputConstants.isKeyDown(window, 67) && bl) {
				this.handledDebugKey = true;
				this.debugCrashKeyTime = Util.getMillis();
				this.debugCrashKeyReportedTime = Util.getMillis();
				this.debugCrashKeyReportedCount = 0L;
			}

			Screen screen = this.minecraft.screen;
			if (screen != null) {
				switch (keyEvent.key()) {
					case 258:
						this.minecraft.setLastInputType(InputType.KEYBOARD_TAB);
					case 259:
					case 260:
					case 261:
					default:
						break;
					case 262:
					case 263:
					case 264:
					case 265:
						this.minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
				}
			}

			if (i == 1 && (!(this.minecraft.screen instanceof KeyBindsScreen) || ((KeyBindsScreen)screen).lastKeySelection <= Util.getMillis() - 20L)) {
				if (this.minecraft.options.keyFullscreen.matches(keyEvent)) {
					window.toggleFullScreen();
					boolean bl2 = window.isFullscreen();
					this.minecraft.options.fullscreen().set(bl2);
					this.minecraft.options.save();
					if (this.minecraft.screen instanceof VideoSettingsScreen videoSettingsScreen) {
						videoSettingsScreen.updateFullscreenButton(bl2);
					}

					return;
				}

				if (this.minecraft.options.keyScreenshot.matches(keyEvent)) {
					if (keyEvent.hasControlDown() && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) {
						this.showDebugChat(this.minecraft.grabPanoramixScreenshot(this.minecraft.gameDirectory));
					} else {
						Screenshot.grab(
							this.minecraft.gameDirectory, this.minecraft.getMainRenderTarget(), component -> this.minecraft.execute(() -> this.showDebugChat(component))
						);
					}

					return;
				}
			}

			if (i != 0) {
				boolean bl2 = screen == null || !(screen.getFocused() instanceof EditBox) || !((EditBox)screen.getFocused()).canConsumeInput();
				if (bl2) {
					if (keyEvent.hasControlDown() && keyEvent.key() == 66 && this.minecraft.getNarrator().isActive() && this.minecraft.options.narratorHotkey().get()) {
						boolean bl3 = this.minecraft.options.narrator().get() == NarratorStatus.OFF;
						this.minecraft.options.narrator().set(NarratorStatus.byId(this.minecraft.options.narrator().get().getId() + 1));
						this.minecraft.options.save();
						if (screen != null) {
							screen.updateNarratorStatus(bl3);
						}
					}

					LocalPlayer var17 = this.minecraft.player;
				}
			}

			if (screen != null) {
				try {
					if (i != 1 && i != 2) {
						if (i == 0 && screen.keyReleased(keyEvent)) {
							return;
						}
					} else {
						screen.afterKeyboardAction();
						if (screen.keyPressed(keyEvent)) {
							if (this.minecraft.screen == null) {
								InputConstants.Key key = InputConstants.getKey(keyEvent);
								KeyMapping.set(key, false);
							}

							return;
						}
					}
				} catch (Throwable var13) {
					CrashReport crashReport = CrashReport.forThrowable(var13, "keyPressed event handler");
					screen.fillCrashDetails(crashReport);
					CrashReportCategory crashReportCategory = crashReport.addCategory("Key");
					crashReportCategory.setDetail("Key", keyEvent.key());
					crashReportCategory.setDetail("Scancode", keyEvent.scancode());
					crashReportCategory.setDetail("Mods", keyEvent.modifiers());
					throw new ReportedException(crashReport);
				}
			}

			InputConstants.Key key = InputConstants.getKey(keyEvent);
			boolean bl3 = this.minecraft.screen == null;
			boolean bl4 = bl3
				|| this.minecraft.screen instanceof PauseScreen pauseScreen && !pauseScreen.showsPauseMenu()
				|| this.minecraft.screen instanceof GameModeSwitcherScreen;
			if (i == 0) {
				KeyMapping.set(key, false);
				if (keyEvent.key() == 292) {
					if (this.handledDebugKey) {
						this.handledDebugKey = false;
					} else {
						this.minecraft.debugEntries.toggleF3Visible();
					}
				}
			} else {
				boolean bl5 = false;
				if (bl4 && keyEvent.isEscape()) {
					this.minecraft.pauseGame(bl);
					bl5 = bl;
				} else if (bl) {
					bl5 = this.handleDebugKeys(keyEvent);
				} else if (bl4 && keyEvent.key() == 290) {
					this.minecraft.options.hideGui = !this.minecraft.options.hideGui;
				} else if (bl4 && keyEvent.key() == 293) {
					this.minecraft.gameRenderer.togglePostEffect();
				}

				this.handledDebugKey |= bl5;
				if (this.minecraft.getDebugOverlay().showProfilerChart() && !bl) {
					int j = keyEvent.getDigit();
					if (j != -1) {
						this.minecraft.getDebugOverlay().getProfilerPieChart().profilerPieChartKeyPress(j);
					}
				}

				if (bl3) {
					if (bl5) {
						KeyMapping.set(key, false);
					} else {
						KeyMapping.set(key, true);
						KeyMapping.click(key);
					}
				}
			}
		}
	}

	private void charTyped(long l, CharacterEvent characterEvent) {
		if (l == this.minecraft.getWindow().handle()) {
			Screen screen = this.minecraft.screen;
			if (screen != null && this.minecraft.getOverlay() == null) {
				try {
					screen.charTyped(characterEvent);
				} catch (Throwable var8) {
					CrashReport crashReport = CrashReport.forThrowable(var8, "charTyped event handler");
					screen.fillCrashDetails(crashReport);
					CrashReportCategory crashReportCategory = crashReport.addCategory("Key");
					crashReportCategory.setDetail("Codepoint", characterEvent.codepoint());
					crashReportCategory.setDetail("Mods", characterEvent.modifiers());
					throw new ReportedException(crashReport);
				}
			}
		}
	}

	public void setup(Window window) {
		InputConstants.setupKeyboardCallbacks(window, (l, i, j, k, m) -> {
			KeyEvent keyEvent = new KeyEvent(i, j, m);
			this.minecraft.execute(() -> this.keyPress(l, k, keyEvent));
		}, (l, i, j) -> {
			CharacterEvent characterEvent = new CharacterEvent(i, j);
			this.minecraft.execute(() -> this.charTyped(l, characterEvent));
		});
	}

	public String getClipboard() {
		return this.clipboardManager.getClipboard(this.minecraft.getWindow(), (i, l) -> {
			if (i != 65545) {
				this.minecraft.getWindow().defaultErrorCallback(i, l);
			}
		});
	}

	public void setClipboard(String string) {
		if (!string.isEmpty()) {
			this.clipboardManager.setClipboard(this.minecraft.getWindow(), string);
		}
	}

	public void tick() {
		if (this.debugCrashKeyTime > 0L) {
			long l = Util.getMillis();
			long m = 10000L - (l - this.debugCrashKeyTime);
			long n = l - this.debugCrashKeyReportedTime;
			if (m < 0L) {
				if (this.minecraft.hasControlDown()) {
					Blaze3D.youJustLostTheGame();
				}

				String string = "Manually triggered debug crash";
				CrashReport crashReport = new CrashReport("Manually triggered debug crash", new Throwable("Manually triggered debug crash"));
				CrashReportCategory crashReportCategory = crashReport.addCategory("Manual crash details");
				NativeModuleLister.addCrashSection(crashReportCategory);
				throw new ReportedException(crashReport);
			}

			if (n >= 1000L) {
				if (this.debugCrashKeyReportedCount == 0L) {
					this.debugFeedbackTranslated("debug.crash.message");
				} else {
					this.debugWarningComponent(Component.translatable("debug.crash.warning", new Object[]{Mth.ceil((float)m / 1000.0F)}));
				}

				this.debugCrashKeyReportedTime = l;
				this.debugCrashKeyReportedCount++;
			}
		}
	}
}
