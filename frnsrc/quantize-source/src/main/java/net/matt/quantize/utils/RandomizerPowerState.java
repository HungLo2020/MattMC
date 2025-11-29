// Java
package net.matt.quantize.utils;

import net.minecraft.util.StringRepresentable;

public enum RandomizerPowerState implements StringRepresentable {
    OFF("off"),
    LEFT("left"),
    RIGHT("right");

    private final String name;

    RandomizerPowerState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}