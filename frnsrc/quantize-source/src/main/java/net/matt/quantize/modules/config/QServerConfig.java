package net.matt.quantize.modules.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;

public class QServerConfig {
    public static final ForgeConfigSpec SERVER_CONFIG;

    // Define configuration values
    public static final ForgeConfigSpec.BooleanValue CROWS_STEAL_CROPS;
    public static final ForgeConfigSpec.BooleanValue SUPER_SECRET_SETTINGS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();



        // Add configuration options
        builder.comment("Server configuration settings").push("general");

        // Do Crows Steal Crops
        CROWS_STEAL_CROPS = builder.comment("Whether crows can steal crops")
                .define("crowsStealCrops", true);
        // Add superSecretSettings boolean
        SUPER_SECRET_SETTINGS = builder.comment("Enable super secret settings")
                .define("superSecretSettings", false);

        builder.pop();





        SERVER_CONFIG = builder.build();
    }

    /*public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_CONFIG);
    }*/
}