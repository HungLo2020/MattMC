package net.matt.quantize.modules.entities;

import java.util.Arrays;
import java.util.Comparator;

public enum PigEntityVariant {
    REGULAR_PIG(0),
    WARM_PIG(1),
    COLD_PIG(2);

    private static final PigEntityVariant[] BY_ID = Arrays.stream(values()).sorted(Comparator.
            comparingInt(PigEntityVariant::getId)).toArray(PigEntityVariant[]::new);
    private final int id;

    PigEntityVariant(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static PigEntityVariant byId(int id) {
        return BY_ID[id % BY_ID.length];
    }
}
