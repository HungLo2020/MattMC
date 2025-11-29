package net.matt.quantize.commands;

import net.minecraft.world.level.GameRules;

public final class QGameRules {
    public static GameRules.Key<GameRules.IntegerValue> FLIGHT_SPEED;

    public static void init() {
        FLIGHT_SPEED = GameRules.register(
                "flightSpeed",
                GameRules.Category.PLAYER,
                GameRules.IntegerValue.create(1) // default = 1x
        );
    }

    private QGameRules() {}
}