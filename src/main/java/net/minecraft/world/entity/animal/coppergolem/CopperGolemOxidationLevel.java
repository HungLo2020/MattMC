package net.minecraft.world.entity.animal.coppergolem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public record CopperGolemOxidationLevel(
	SoundEvent spinHeadSound, SoundEvent hurtSound, SoundEvent deathSound, SoundEvent stepSound, ResourceLocation texture, ResourceLocation eyeTexture
) {
}
