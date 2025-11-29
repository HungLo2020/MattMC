package net.matt.quantize.modules.entities;

import java.util.Arrays;
import java.util.Comparator;

public enum ChickenEntityVariant {
    REGULAR_CHICKEN(0),
    WARM_CHICKEN(1),
    COLD_CHICKEN(2);

    private static final ChickenEntityVariant[] BY_ID = Arrays.stream(values()).sorted(Comparator.
            comparingInt(ChickenEntityVariant::getId)).toArray(ChickenEntityVariant[]::new);
    private final int id;

    ChickenEntityVariant(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static ChickenEntityVariant byId(int id) {
        return BY_ID[id % BY_ID.length];
    }
}
