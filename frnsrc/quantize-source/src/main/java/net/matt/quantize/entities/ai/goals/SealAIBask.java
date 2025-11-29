package net.matt.quantize.entities.ai.goals;

import net.matt.quantize.entities.mobs.EntitySeal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SealAIBask extends Goal {
    private final EntitySeal seal;

    public SealAIBask(EntitySeal seal) {
        this.seal = seal;
        this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    public boolean canContinueToUse() {
        return this.seal.isBasking() && !this.seal.isInWaterOrBubble();
    }

    public boolean canUse() {
        if (this.seal.isInWaterOrBubble()) {
            return false;
        } else {
            return seal.getLastHurtByMob() == null && seal.getTarget() == null && seal.isBasking();
        }
    }

    public void tick() {
        this.seal.getNavigation().stop();
    }

    public void stop() {
        this.seal.setBasking(false);
    }
}
