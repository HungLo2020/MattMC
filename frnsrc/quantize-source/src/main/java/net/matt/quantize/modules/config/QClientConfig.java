package net.matt.quantize.modules.config;

import net.matt.quantize.Quantize;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QClientConfig {

    public static Client CLIENT = new Client();

    private static abstract class ConfigClass {
        public ForgeConfigSpec SPEC;

        public abstract void onConfigReload(ModConfigEvent.Reloading event);
    }

    public static class Client extends ConfigClass {
        public ForgeConfigSpec.ConfigValue<Integer> GUI_BUTTON_X_OFFSET;
        public ForgeConfigSpec.ConfigValue<Integer> GUI_BUTTON_Y_OFFSET;
        public ForgeConfigSpec.ConfigValue<Integer> TITLE_SCREEN_BUTTON_X_OFFSET;
        public ForgeConfigSpec.ConfigValue<Integer> TITLE_SCREEN_BUTTON_Y_OFFSET;
        public ForgeConfigSpec.ConfigValue<Boolean> SHOW_BUTTON_IN_TITLE_SCREEN;
        public ForgeConfigSpec.ConfigValue<List<String>> METHOD_SHADER_BLACKLIST;
        public ForgeConfigSpec.ConfigValue<Boolean> METHOD_SHADER_DUMP;
        public ForgeConfigSpec.ConfigValue<String> SELECTED_PANORAMA;
        public ForgeConfigSpec.ConfigValue<Boolean> PANORAMA_BLUR;
        public ForgeConfigSpec.ConfigValue<Boolean> CROWS_STEAL_CROPS;
        public ForgeConfigSpec.ConfigValue<String> SELECTED_SKIN;

        public Client() {
            final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
            List<String> defaultBlacklist = new ArrayList<>(Arrays.asList(
                    "mezz.jei.common.render.FluidTankRenderer:drawTextureWithMasking",//1.19.1 JEI Path
                    "mezz.jei.library.render.FluidTankRenderer:drawTextureWithMasking",//1.19.2+ JEI Path
                    "renderCrosshair", "m_93080_",
                    "renderSky", "m_202423_",
                    "renderHotbar", "m_93009_", "m_193837_",//Normal hotbar, and spectator hotbar
                    "setupOverlayRenderState",
                    "net.minecraftforge.client.gui.overlay.ForgeGui",
                    "renderFood",
                    "renderExperienceBar", "m_93071_",
                    "renderLogo", "m_280037_", "m_280118_",
                    "net.minecraft.client.gui.Gui", "net.minecraft.src.C_3431_",
                    "renderDirtBackground", "m_280039_", "m_280039_",
                    "configured.client.screen.ListMenuScreen", // Configured background
                    "OnlineServerEntry:drawIcon", "OnlineServerEntry:m_99889_", // Multiplayer Server icons
                    "WorldSelectionList$WorldListEntry:render", "WorldSelectionList$WorldListEntry:m_6311_", // Single player world icons
                    "CubeMap:render", "CubeMap:m_108849_", //1.20+ title screen panorama
                    "squeek.appleskin.client.HUDOverlayHandler", //AppleSkin overlay
                    "shadows.packmenu.ExtendedMenuScreen" //Custom PackMenu backgrounds
            ));

            String TRANSLATION_KEY_BASE = "config." + Quantize.MOD_ID + ".";
            METHOD_SHADER_BLACKLIST = BUILDER.comment(
                            "A list of class:method strings (render methods) that the dark shader will not be applied to.",
                            "Each string consists of the class and the method (or any substring) to block the dark shader.",
                            "For example, 'renderHunger' is sufficient to block 'net.minecraftforge.client.gui.overlay.ForgeGui:renderFood' (either will work).")
                    .translation(TRANSLATION_KEY_BASE + "method_shader_blacklist")
                    .define("METHOD_SHADER_BLACKLIST", defaultBlacklist);
            METHOD_SHADER_DUMP = BUILDER.comment(
                            "Enabling this config will (every 5 seconds) dump which methods were used to render GUIs that the dark shader was applied to",
                            "The dump will consist of a list of class:method strings, e.g. 'net.minecraftforge.client.gui.overlay.ForgeGui:renderFood'",
                            "Use this feature to help find the render method strings of GUIs you would like to blacklist.")
                    .translation(TRANSLATION_KEY_BASE + "method_shader_dump")
                    .define("METHOD_SHADER_DUMP", false);

            // Panorama QClientConfig
            BUILDER.push("Panorama");
            SELECTED_PANORAMA = BUILDER.comment("The selected panorama texture.")
                    .translation(TRANSLATION_KEY_BASE + "selected_panorama")
                    .define("SELECTED_PANORAMA", "panorama1"); // Default value
            PANORAMA_BLUR = BUILDER.comment("Enable or disable blur effect on panorama.")
                    .translation(TRANSLATION_KEY_BASE + "panorama_blur")
                    .define("PANORAMA_BLUR", false); // Default value
            BUILDER.pop();

            // Selected Skin
            SELECTED_SKIN = BUILDER.comment("The selected skin texture.")
                    .translation(TRANSLATION_KEY_BASE + "selected_skin")
                    .define("SELECTED_SKIN", "steve"); // Default value

            // DONT ADD AFTER THIS LINE
            SPEC = BUILDER.build();
        }

        @Override
        public void onConfigReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getType() == ModConfig.Type.COMMON) {
                SPEC.setConfig(event.getConfig().getConfigData());
            }
        }
    }
}