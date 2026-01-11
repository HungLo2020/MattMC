package net.minecraft.world.entity.animal.subterranodon.citadel;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * Simplified replacement for Citadel's AdvancedPathNavigate.
 * Provides basic flying and ground navigation.
 */
public class AdvancedPathNavigateNoTeleport extends FlyingPathNavigation {
    
    public AdvancedPathNavigateNoTeleport(Mob mob, Level level) {
        super(mob, level);
    }
    
    /**
     * Movement types for the navigator.
     */
    public enum MovementType {
        WALKING,
        FLYING
    }
    
    /**
     * Creates a path navigator for the given movement type.
     */
    public static PathNavigation create(Mob mob, Level level, MovementType type) {
        if (type == MovementType.FLYING) {
            return new FlyingPathNavigation(mob, level);
        } else {
            return new GroundPathNavigation(mob, level);
        }
    }
}
