package net.minecraft.client.renderer.sodium.gui.options.storage;

public interface OptionStorage<T> {
    T getData();

    void save();
}
