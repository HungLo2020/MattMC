package com.github.alexthe666.citadel;

import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import com.github.alexthe666.citadel.client.CitadelItemRenderProperties;
import com.github.alexthe666.citadel.client.event.EventRenderSplashText;
import com.github.alexthe666.citadel.client.game.Tetris;
// Citadel: Disabled GUI book classes (optional feature - requires major 1.21 GuiGraphics rewrite)
// import com.github.alexthe666.citadel.client.gui.GuiCitadelBook;
// import com.github.alexthe666.citadel.client.gui.GuiCitadelCapesConfig;
import com.github.alexthe666.citadel.client.gui.GuiCitadelPatreonConfig;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.github.alexthe666.citadel.client.model.TabulaModelHandler;
import com.github.alexthe666.citadel.client.render.CitadelLecternRenderer;
import com.github.alexthe666.citadel.client.render.pathfinding.WorldEventContext;
import com.github.alexthe666.citadel.client.rewards.CitadelCapes;
import com.github.alexthe666.citadel.client.rewards.CitadelPatreonRenderer;
import com.github.alexthe666.citadel.client.rewards.SpaceStationPatreonRenderer;
import com.github.alexthe666.citadel.client.shader.PostEffectRegistry;
import com.github.alexthe666.citadel.client.tick.ClientTickRateTracker;
import com.github.alexthe666.citadel.config.ServerConfig;
import com.github.alexthe666.citadel.item.ItemWithHoverAnimation;
import com.github.alexthe666.citadel.server.entity.CitadelEntityData;
import com.github.alexthe666.citadel.server.entity.pathfinding.raycoms.Pathfinding;
import com.github.alexthe666.citadel.server.event.EventChangeEntityTickRate;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// Citadel: Using custom event system since this is a Minecraft fork, not a Fabric mod
// TODO: Wire these events into vanilla Minecraft classes directly
import com.github.alexthe666.citadel.server.event.EventMergeStructureSpawns.TriState;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClientProxy extends ServerProxy {
    public static TabulaModel CITADEL_MODEL;
    public static boolean hideFollower = false;
    private Map<ItemStack, Float> prevMouseOverProgresses = new HashMap<>();

    private Map<ItemStack, Float> mouseOverProgresses = new HashMap<>();
    private ItemStack lastHoveredItem = null;
    private Tetris aprilFoolsTetrisGame = null;
    public static final ResourceLocation RAINBOW_AURA_POST_SHADER = ResourceLocation.parse("citadel:shaders/post/rainbow_aura.json");

    public ClientProxy() {
        super();
    }

    public void onClientInit() {
        try {
            CITADEL_MODEL = new TabulaModel(TabulaModelHandler.INSTANCE.loadTabulaModel("/assets/citadel/models/citadel_model"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        BlockEntityRenderers.register(Citadel.LECTERN_BE, CitadelLecternRenderer::new);
        CitadelPatreonRenderer.register("citadel", new SpaceStationPatreonRenderer(ResourceLocation.parse("citadel:patreon_space_station"), new int[]{}));
        CitadelPatreonRenderer.register("citadel_red", new SpaceStationPatreonRenderer(ResourceLocation.parse("citadel:patreon_space_station_red"), new int[]{0XB25048, 0X9D4540, 0X7A3631, 0X71302A}));
        CitadelPatreonRenderer.register("citadel_gray", new SpaceStationPatreonRenderer(ResourceLocation.parse("citadel:patreon_space_station_gray"), new int[]{0XA0A0A0, 0X888888, 0X646464, 0X575757}));
        if (CitadelConstants.debugShaders()) {
            PostEffectRegistry.registerEffect(RAINBOW_AURA_POST_SHADER);
        }
        
        // Citadel: Event wiring will be done by modifying vanilla Minecraft classes directly
        // Since this is a Minecraft fork, not a Fabric mod, we don't have Fabric API
        // TODO: Add hooks in Minecraft.java, Screen.java, etc. to call these methods
    }
    
    // Citadel: This method would be called from vanilla Minecraft.tick() via direct modification
    public void onClientTick() {
        if (!isGamePaused() && Minecraft.getInstance().isRunning() && Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
            ClientTickRateTracker.getForClient(Minecraft.getInstance()).masterTick();
            tickMouseOverAnimations();
        }
        if (!isGamePaused() && CitadelConstants.isAprilFools()) {
            if (aprilFoolsTetrisGame != null) {
                if (Minecraft.getInstance().screen instanceof TitleScreen) {
                    aprilFoolsTetrisGame.tick();
                } else {
                    aprilFoolsTetrisGame.reset();
                }
            }
        }
    }
    // Citadel: Converted from NeoForge @SubscribeEvent to Fabric callback (called from registerFabricEvents)
    public void screenOpen(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof SkinCustomizationScreen && Minecraft.getInstance().player != null) {
            try {
                String username = Minecraft.getInstance().player.getName().getString();
                int height = -20;
                if (Citadel.PATREONS.contains(username)) {
                    Button button1 = Button.builder(Component.translatable("citadel.gui.patreon_rewards_option").withStyle(ChatFormatting.GREEN), (p_213080_2_) -> Minecraft.getInstance().setScreen(new GuiCitadelPatreonConfig(screen, Minecraft.getInstance().options))).size(200, 20).pos(screen.width / 2 - 100, screen.height / 6 + 150 + height).build();
                    // TODO: Add button to screen
                    height += 25;
                }
                // Citadel: GuiCitadelCapesConfig disabled (optional feature)
                /*
                if (!CitadelCapes.getCapesFor(Minecraft.getInstance().player.getUUID()).isEmpty()) {
                    Button button2 = Button.builder(Component.translatable("citadel.gui.capes_option").withStyle(ChatFormatting.GREEN), (p_213080_2_) -> Minecraft.getInstance().setScreen(new GuiCitadelCapesConfig(screen, Minecraft.getInstance().options))).size(200, 20).pos(screen.width / 2 - 100, screen.height / 6 + 150 + height).build();
                    // TODO: Add button to screen
                    height += 25;
                }
                */
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - called from Fabric ScreenEvents
    public void screenRender(net.minecraft.client.gui.screens.Screen screen, net.minecraft.client.gui.GuiGraphics guiGraphics, float partialTick) {
        if (screen instanceof TitleScreen && CitadelConstants.isAprilFools()) {
            if (aprilFoolsTetrisGame == null) {
                aprilFoolsTetrisGame = new Tetris();
            } else {
                aprilFoolsTetrisGame.render((TitleScreen) screen, guiGraphics, partialTick);
            }
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - TODO: Wire to Fabric player render event
    public void playerRender(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, net.minecraft.world.entity.player.Player player, float partialTick) {
        String username = player.getName().getString();
        if (!player.isModelPartShown(PlayerModelPart.CAPE) || player.isSpectator()) {
            return;
        }
        if (Citadel.PATREONS.contains(username)) {
            CompoundTag tag = CitadelEntityData.getOrCreateCitadelTag(Minecraft.getInstance().player);
            // Citadel: CompoundTag methods now return Optional in 1.21
            String rendererName = tag.contains("CitadelFollowerType") ? tag.getString("CitadelFollowerType").orElse("citadel") : "citadel";
            if (!rendererName.equals("none") && !hideFollower) {
                CitadelPatreonRenderer renderer = CitadelPatreonRenderer.get(rendererName);
                if (renderer != null) {
                    float distance = tag.contains("CitadelRotateDistance") ? tag.getFloat("CitadelRotateDistance").orElse(2F) : 2F;
                    float speed = tag.contains("CitadelRotateSpeed") ? tag.getFloat("CitadelRotateSpeed").orElse(0F) : 0F;
                    float height = tag.contains("CitadelRotateHeight") ? tag.getFloat("CitadelRotateHeight").orElse(1F) : 1F;
                    renderer.render(poseStack, bufferSource, packedLight, partialTick, player, distance, speed, height);
                }
            }
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - called from Fabric WorldRenderEvents
    public void renderWorldLastEvent() {
        if (Pathfinding.isDebug()) {
            // WorldEventContext.INSTANCE.renderWorldLastEvent would go here
            // TODO: Adapt for Fabric rendering context
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - TODO: Wire to Fabric screen open event
    public void onOpenGui(net.minecraft.client.gui.screens.Screen screen) {
        if (ServerConfig.skipWarnings) {
            try {
                if (screen instanceof BackupConfirmScreen confirmBackupScreen) {
                    MutableComponent title = Component.translatable("selectWorld.backupQuestion.experimental");

                    if (confirmBackupScreen.getTitle().equals(title)) {
                        // Citadel: Use reflection to access protected field onProceed
                        try {
                            var field = BackupConfirmScreen.class.getDeclaredField("onProceed");
                            field.setAccessible(true);
                            var onProceed = (BackupConfirmScreen.Listener) field.get(confirmBackupScreen);
                            onProceed.proceed(false, true);
                        } catch (Exception reflectionEx) {
                            // If reflection fails, just skip
                        }
                    }
                }
                if (screen instanceof ConfirmScreen confirmScreen) {
                    MutableComponent title = Component.translatable("selectWorld.backupQuestion.experimental");
                    if (confirmScreen.getTitle().equals(title)) {
                        // Citadel: Use reflection to access protected field callback
                        try {
                            var field = ConfirmScreen.class.getDeclaredField("callback");
                            field.setAccessible(true);
                            var callback = (java.util.function.Consumer<Boolean>) field.get(confirmScreen);
                            callback.accept(true);
                        } catch (Exception reflectionEx) {
                            // If reflection fails, just skip
                        }
                    }
                }
            } catch (Exception e) {
                Citadel.LOGGER.warn("Citadel couldn't skip world loadings");
                e.printStackTrace();
            }
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - TODO: Wire to splash text rendering in vanilla TitleScreen
    public void renderSplashTextBefore(EventRenderSplashText.Pre event) {
        if (CitadelConstants.isAprilFools() && aprilFoolsTetrisGame != null) {
            event.setResult(TriState.TRUE);
            float hue = (System.currentTimeMillis() % 6000) / 6000f;
            // TODO: This needs to be wired into vanilla TitleScreen rendering
            // event.getGuiGraphics().pose().mulPose(Axis.ZP.rotationDegrees((float) Math.sin(hue * Math.PI) * 360));
            if (!aprilFoolsTetrisGame.isStarted()) {
                event.setSplashText("Psst... press 'T' ;)");
            } else {
                event.setSplashText("");
            }
            int rainbow = Color.HSBtoRGB(hue, 0.6f, 1);
            event.setSplashTextColor(rainbow);
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - TODO: Wire to Fabric keyboard event
    public void onKeyPressed(int keyCode) {
        if (Minecraft.getInstance().screen instanceof TitleScreen && aprilFoolsTetrisGame != null && aprilFoolsTetrisGame.isStarted()) {
            if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_DOWN || keyCode == InputConstants.KEY_UP) {
                // event.setCanceled(true); // TODO: Cancel event in Fabric
            }
        }
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - now called from Fabric ClientTickEvents
    public void clientTick() {
        if (!isGamePaused() && Minecraft.getInstance().isRunning() && Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
            ClientTickRateTracker.getForClient(Minecraft.getInstance()).masterTick();
            tickMouseOverAnimations();
        }
        if (!isGamePaused() && CitadelConstants.isAprilFools()) {
            if (aprilFoolsTetrisGame != null) {
                if (Minecraft.getInstance().screen instanceof TitleScreen) {
                    aprilFoolsTetrisGame.tick();
                } else {
                    aprilFoolsTetrisGame.reset();
                }
            }
        }
    }

    private void tickMouseOverAnimations() {
        prevMouseOverProgresses.putAll(mouseOverProgresses);
        if (lastHoveredItem != null) {
            float prev = mouseOverProgresses.getOrDefault(lastHoveredItem, 0F);
            float maxTime = 5F;
            if (lastHoveredItem.getItem() instanceof ItemWithHoverAnimation hoverOver) {
                maxTime = hoverOver.getMaxHoverOverTime(lastHoveredItem);
            }
            if (prev < maxTime) {
                mouseOverProgresses.put(lastHoveredItem, prev + 1);
            }
        }

        if (!mouseOverProgresses.isEmpty()) {
            Iterator<Map.Entry<ItemStack, Float>> it = mouseOverProgresses.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ItemStack, Float> next = it.next();
                float progress = next.getValue();
                if (lastHoveredItem == null || next.getKey() != lastHoveredItem) {
                    if (progress == 0) {
                        it.remove();
                    } else {
                        next.setValue(progress - 1);
                    }
                }
            }
        }
        lastHoveredItem = null;
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - TODO: Wire to Fabric tooltip rendering
    public void renderTooltipColor(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemWithHoverAnimation hoverOver && hoverOver.canHoverOver(itemStack)) {
            lastHoveredItem = itemStack;
        } else {
            lastHoveredItem = null;
        }
    }

    @Override
    public float getMouseOverProgress(ItemStack itemStack) {
        float prev = prevMouseOverProgresses.getOrDefault(itemStack, 0F);
        float current = mouseOverProgresses.getOrDefault(itemStack, 0F);
        float lerped = prev + (current - prev) * Minecraft.getInstance().getFrameTimeNs();
        float maxTime = 5F;
        if (itemStack.getItem() instanceof ItemWithHoverAnimation hoverOver) {
            maxTime = hoverOver.getMaxHoverOverTime(itemStack);
        }
        return lerped / maxTime;
    }

    @Override
    public void handleAnimationPacket(int entityId, int index) {
        if (Minecraft.getInstance().level != null) {
            IAnimatedEntity entity = (IAnimatedEntity) Minecraft.getInstance().level.getEntity(entityId);
            if (entity != null) {
                if (index == -1) {
                    entity.setAnimation(IAnimatedEntity.NO_ANIMATION);
                } else {
                    entity.setAnimation(entity.getAnimations()[index]);
                }
                entity.setAnimationTick(0);
            }
        }
    }

    @Override
    public void handlePropertiesPacket(String propertyID, CompoundTag compound, int entityID) {
        if (compound == null || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityID);
        if ((propertyID.equals("CitadelPatreonConfig") || propertyID.equals("CitadelTagUpdate")) && entity instanceof LivingEntity) {
            CitadelEntityData.setCitadelTag((LivingEntity) entity, compound);
        }
    }


    @Override
    public void handleClientTickRatePacket(CompoundTag compound) {
        ClientTickRateTracker.getForClient(Minecraft.getInstance()).syncFromServer(compound);
    }

    @Override
    public Object getISTERProperties() {
        return new CitadelItemRenderProperties();
    }

    @Override
    public void openBookGUI(ItemStack book) {
        // Citadel: GuiCitadelBook disabled (optional feature - requires major 1.21 GuiGraphics rewrite)
        // Minecraft.getInstance().setScreen(new GuiCitadelBook(book));
        Citadel.LOGGER.warn("Book GUI is disabled in this build - requires 1.21 GuiGraphics API rewrite");
    }

    public boolean isGamePaused() {
        return Minecraft.getInstance().isPaused();
    }

    public Player getClientSidePlayer() {
        return Minecraft.getInstance().player;
    }

    public boolean canEntityTickClient(Level level, Entity entity) {
        ClientTickRateTracker tracker = ClientTickRateTracker.getForClient(Minecraft.getInstance());
        if (tracker.isTickingHandled(entity)) {
            return false;
        } else if (!tracker.hasNormalTickRate(entity)) {
            EventChangeEntityTickRate event = new EventChangeEntityTickRate(entity, tracker.getEntityTickLengthModifier(entity));
            // Citadel: TODO: Post event to Fabric event bus when implemented
            // NeoForge.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                return true;
            } else {
                tracker.addTickBlockedEntity(entity);
                return false;
            }
        }
        return true;
    }

    // Citadel: Converted from NeoForge @SubscribeEvent - TODO: Wire to Fabric world render events
    public void postRenderStage() {
    }
}
