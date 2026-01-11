package net.minecraft.world.entity.animal.subterranodon;

import net.minecraft.world.entity.TamableAnimal;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for animals that can form packs and follow a leader.
 * Based on AlexsCaves PackAnimal interface.
 */
public interface PackAnimal {
    
    @Nullable
    PackAnimal getPriorPackMember();
    
    @Nullable
    PackAnimal getAfterPackMember();
    
    void setPriorPackMember(@Nullable PackAnimal animal);
    
    void setAfterPackMember(@Nullable PackAnimal animal);
    
    void resetPackFlags();
    
    default boolean isPackFollower() {
        return getPackLeader() != this;
    }
    
    default PackAnimal getPackLeader() {
        PackAnimal leader = this;
        PackAnimal prior = getPriorPackMember();
        int safety = 0;
        while (prior != null && safety < 16) {
            leader = prior;
            prior = leader.getPriorPackMember();
            safety++;
        }
        return leader;
    }
    
    default boolean isInPack() {
        return getPriorPackMember() != null || getAfterPackMember() != null;
    }
}
