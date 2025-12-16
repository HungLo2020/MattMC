package net.minecraft.client.renderer.sodium.gui.options.control;

import net.minecraft.client.renderer.sodium.gui.options.Option;
import net.minecraft.client.renderer.sodium.util.Dim2i;

public interface Control<T> {
    Option<T> getOption();

    ControlElement<T> createElement(Dim2i dim);

    int getMaxWidth();
}
