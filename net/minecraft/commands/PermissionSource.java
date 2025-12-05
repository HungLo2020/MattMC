package net.minecraft.commands;

import net.minecraft.server.commands.PermissionCheck;

public interface PermissionSource {
	boolean hasPermission(int i);

	default boolean allowsSelectors() {
		return this.hasPermission(2);
	}

	public record Check<T extends PermissionSource>(int requiredLevel) implements PermissionCheck<T> {
		public boolean test(T permissionSource) {
			return permissionSource.hasPermission(this.requiredLevel);
		}
	}
}
