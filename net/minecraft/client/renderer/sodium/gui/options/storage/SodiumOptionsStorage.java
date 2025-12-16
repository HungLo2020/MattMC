package net.minecraft.client.renderer.sodium.gui.options.storage;

import net.minecraft.client.renderer.sodium.SodiumClientMod;
import net.minecraft.client.renderer.sodium.gui.SodiumGameOptions;

import java.io.IOException;

public class SodiumOptionsStorage implements OptionStorage<SodiumGameOptions> {
    private final SodiumGameOptions options;

    public SodiumOptionsStorage() {
        this.options = SodiumClientMod.options();
    }

    @Override
    public SodiumGameOptions getData() {
        return this.options;
    }

    @Override
    public void save() {
        try {
            SodiumGameOptions.writeToDisk(this.options);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save configuration changes", e);
        }

        SodiumClientMod.logger().info("Flushed changes to Sodium configuration");
    }
}
