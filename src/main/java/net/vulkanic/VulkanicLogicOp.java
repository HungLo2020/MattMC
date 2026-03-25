package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral logic-op semantics.
 */
public enum VulkanicLogicOp {
    CLEAR(0x1500),
    AND(0x1501),
    AND_REVERSE(0x1502),
    COPY(0x1503),
    AND_INVERTED(0x1504),
    NOOP(0x1505),
    XOR(0x1506),
    OR(0x1507),
    NOR(0x1508),
    EQUIV(0x1509),
    INVERT(VulkanicAPI.GL_INVERT),
    OR_REVERSE(VulkanicAPI.GL_OR_REVERSE),
    COPY_INVERTED(0x150C),
    OR_INVERTED(0x150D),
    NAND(0x150E),
    SET(0x150F);

    private final int legacyGlConstant;

    VulkanicLogicOp(int legacyGlConstant) {
        this.legacyGlConstant = legacyGlConstant;
    }

    public int toLegacyGlConstant() {
        return legacyGlConstant;
    }

    public static Optional<VulkanicLogicOp> fromLegacyGlConstant(int legacyGlConstant) {
        for (VulkanicLogicOp value : values()) {
            if (value.legacyGlConstant == legacyGlConstant) {
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }
}
