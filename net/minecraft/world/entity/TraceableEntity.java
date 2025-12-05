package net.minecraft.world.entity;

import org.jetbrains.annotations.Nullable;

public interface TraceableEntity {
	@Nullable
	Entity getOwner();
}
