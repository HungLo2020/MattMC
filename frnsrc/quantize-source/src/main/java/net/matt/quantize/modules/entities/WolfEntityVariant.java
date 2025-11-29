package net.matt.quantize.modules.entities;

import java.util.Arrays;
import java.util.Comparator;

public enum WolfEntityVariant {
    PALE_WOLF(0),
    WOODS_WOLF(1),
    ASHEN_WOLF(2),
    BLACK_WOLF(3),
    CHESTNUT_WOLF(4),
    RUSTY_WOLF(5),
    SPOTTED_WOLF(6),
    STRIPED_WOLF(7),
    SNOWY_WOLF(8);

    private static final WolfEntityVariant[] BY_ID = Arrays.stream(values()).sorted(Comparator.
            comparingInt(WolfEntityVariant::getId)).toArray(WolfEntityVariant[]::new);
    private final int id;

    WolfEntityVariant(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static WolfEntityVariant byId(int id) {
        return BY_ID[id % BY_ID.length];
    }
}
