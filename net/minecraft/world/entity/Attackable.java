package net.minecraft.world.entity;

import org.jetbrains.annotations.Nullable;

public interface Attackable {
	@Nullable
	LivingEntity getLastAttacker();
}
