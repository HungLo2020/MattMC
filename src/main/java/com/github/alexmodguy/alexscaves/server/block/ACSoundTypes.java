package com.github.alexmodguy.alexscaves.server.block;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;

public class ACSoundTypes {

    // Simplified sound types using vanilla sounds as placeholders
    public static final SoundType PEWEN_BRANCH = new SoundType(1.0F, 1.0F, 
        SoundEvents.CHERRY_WOOD_BREAK, 
        SoundEvents.CHERRY_WOOD_STEP, 
        SoundEvents.CHERRY_WOOD_PLACE, 
        SoundEvents.CHERRY_WOOD_HIT, 
        SoundEvents.CHERRY_WOOD_FALL);
    
    public static final SoundType FLOOD_BASALT = new SoundType(1.0F, 1.0F, 
        SoundEvents.BASALT_BREAK, 
        SoundEvents.BASALT_STEP, 
        SoundEvents.BASALT_PLACE, 
        SoundEvents.BASALT_HIT, 
        SoundEvents.BASALT_FALL);
}
