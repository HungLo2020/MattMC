package com.github.alexthe666.alexsmobs.entity.ai;

import com.github.alexthe666.alexsmobs.entity.EntityGrizzlyBear;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.EnumSet;

public class GrizzlyBearAIAprilFools extends Goal {

    private final EntityGrizzlyBear bear;
    private Player target;
    private int runDelay = 0;
    private final double maxDistance = 13;
    private int powerOutTimer = 0;
    private int musicBoxTimer = 0;
    private int maxMusicBoxTime = 0;
    private int leapTimer = 0;

    public GrizzlyBearAIAprilFools(EntityGrizzlyBear bear){
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.bear = bear;
    }

    @Override
    public boolean canUse() {
        // Disabled April Fools behavior
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && bear.distanceTo(target) < maxDistance * 2;
    }

    public void start(){
        maxMusicBoxTime = 100 + bear.getRandom().nextInt(130);
    }

    @Override
    public void tick() {
        super.tick();
        double dist = bear.distanceTo(target);
        bear.getLookControl().setLookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
        if(dist <= 6 && bear.hasLineOfSight(target)){
            bear.getNavigation().stop();
            if(bear.getAprilFoolsFlag() == 5){
                leapTimer++;
                if(leapTimer == 7){
                    // Stubbed network message
                }
                if(leapTimer >= 10){
                    bear.setAprilFoolsFlag(0);
                    if(bear.level().getLevelData().isHardcore()){
                        target.hurt(bear.damageSources().mobAttack(bear), target.getMaxHealth() - 1);
                        target.setHealth(1);
                    }else{
                        target.hurt(bear.damageSources().mobAttack(bear), target.getMaxHealth() + 1000F);
                    }
                    stop();
                    return;
                }
            }else if(bear.getAprilFoolsFlag() < 4) {
                if(powerOutTimer == 0){
                    // Stubbed effect
                }
                powerOutTimer++;
                if (powerOutTimer >= 60) {
                    bear.setAprilFoolsFlag(4);
                    powerOutTimer = 0;
                }else{
                    bear.setAprilFoolsFlag(3);
                }
            }else{
                if(musicBoxTimer == 0){
                    bear.level().broadcastEntityEvent(bear, (byte) 67);
                }
                musicBoxTimer++;
                if (musicBoxTimer >= maxMusicBoxTime) {
                    if(bear.getAprilFoolsFlag() != 5){
                        bear.level().broadcastEntityEvent(bear, (byte) 68);
                        bear.setAprilFoolsFlag(5);
                        bear.gameEvent(GameEvent.ENTITY_ACTION);
                        bear.playSound(SoundEvents.POLAR_BEAR_WARNING, 3, 1);
                        musicBoxTimer = 0;
                    }
                }
            }
            if(bear.getAprilFoolsFlag() < 2){
                bear.setAprilFoolsFlag(2);
            }
        }else{
            bear.getNavigation().moveTo(target, 1.2F);
            if(bear.getAprilFoolsFlag() < 1){
                bear.setAprilFoolsFlag(1);
            }
        }
    }

    @Override
    public void stop(){
        target = null;
        runDelay = 100 + bear.getRandom().nextInt(100);
        bear.setAprilFoolsFlag(0);
        powerOutTimer = 0;
        musicBoxTimer = 0;
        leapTimer = 0;
    }
}
