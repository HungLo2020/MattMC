package net.matt.quantize.sounds;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.util.ForgeSoundType;

public class QSoundTypes {

    public static final SoundType PEWEN_BRANCH = new ForgeSoundType(1.0F, 1.0F, () -> QSounds.PEWEN_BRANCH_BREAK.get(), () -> SoundEvents.CHERRY_WOOD_STEP, () -> SoundEvents.CHERRY_WOOD_PLACE, () -> SoundEvents.CHERRY_WOOD_HIT, () -> SoundEvents.CHERRY_WOOD_FALL);
    public static final SoundType AMBER = new ForgeSoundType(1.0F, 1.0F, () -> QSounds.AMBER_BREAK.get(), () -> QSounds.AMBER_STEP.get(), () -> QSounds.AMBER_PLACE.get(), () -> QSounds.AMBER_BREAKING.get(), () -> QSounds.AMBER_STEP.get());
    public static final SoundType AMBER_MONOLITH = new ForgeSoundType(1.0F, 1.0F, () -> QSounds.AMBER_BREAK.get(), () -> QSounds.AMBER_STEP.get(), () -> QSounds.AMBER_MONOLITH_PLACE.get(), () -> QSounds.AMBER_BREAKING.get(), () -> QSounds.AMBER_STEP.get());
    public static final SoundType FLOOD_BASALT = new ForgeSoundType(1.0F, 1.0F, () -> QSounds.FLOOD_BASALT_BREAK.get(), () -> QSounds.FLOOD_BASALT_STEP.get(), () -> QSounds.FLOOD_BASALT_PLACE.get(), () -> QSounds.FLOOD_BASALT_BREAKING.get(), () -> QSounds.FLOOD_BASALT_STEP.get());

}
