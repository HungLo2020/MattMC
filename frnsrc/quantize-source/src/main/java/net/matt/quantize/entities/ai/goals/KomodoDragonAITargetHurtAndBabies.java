package net.matt.quantize.entities.ai.goals;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

public class KomodoDragonAITargetHurtAndBabies extends NearestAttackableTargetGoal {

    public KomodoDragonAITargetHurtAndBabies(Mob goalOwnerIn, Class targetClassIn, boolean checkSight) {
        super(goalOwnerIn, targetClassIn, checkSight);
    }


}
