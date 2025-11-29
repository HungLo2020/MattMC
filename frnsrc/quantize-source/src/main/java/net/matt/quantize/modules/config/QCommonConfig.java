package net.matt.quantize.modules.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class QCommonConfig {
    public static final ForgeConfigSpec COMMON_CONFIG;

    // Define configuration values
    public static final ForgeConfigSpec.BooleanValue TEST_VALUE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();



        // Add configuration options
        builder.comment("Common configuration settings").push("general");

        // Do Crows Steal Crops
        TEST_VALUE = builder.comment("testing value")
                .define("testingValue", true);
        builder.pop();





        COMMON_CONFIG = builder.build();
    }

    /*public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG);
    }*/
}