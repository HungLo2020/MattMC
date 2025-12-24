package net.minecraft.world.entity;

import org.jetbrains.annotations.Nullable;

public interface Targeting {
	@Nullable
	LivingEntity getTarget();
}
