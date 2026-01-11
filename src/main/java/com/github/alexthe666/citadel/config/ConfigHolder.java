package com.github.alexthe666.citadel.config;

// TODO: Replace with Fabric config system (e.g., AutoConfig or custom JSON)
// import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

// TODO: This class uses NeoForge's ModConfigSpec - needs Fabric replacement
// For now, config values are initialized with defaults in Citadel.initConfig()
public class ConfigHolder {

    // TODO: Replace with Fabric config spec
    // public static final ModConfigSpec SERVER_SPEC;
    public static final Object SERVER_SPEC = null; // Placeholder
    public static final ServerConfig SERVER = new ServerConfig();

    /* Original NeoForge config initialization
    static {
        {
            final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
            SERVER = specPair.getLeft();
            SERVER_SPEC = specPair.getRight();
        }
    }
    */
}