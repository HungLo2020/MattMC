package net.matt.quantize.modules.entities;

import java.util.Arrays;
import java.util.Comparator;

public enum CowEntityVariant {
    REGULAR_COW(0),
    WARM_COW(1),
    COLD_COW(2);

    private static final CowEntityVariant[] BY_ID = Arrays.stream(values()).sorted(Comparator.
            comparingInt(CowEntityVariant::getId)).toArray(CowEntityVariant[]::new);
    private final int id;

    CowEntityVariant(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static CowEntityVariant byId(int id) {
        return BY_ID[id % BY_ID.length];
    }
}
