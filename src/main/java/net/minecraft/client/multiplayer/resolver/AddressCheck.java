package net.minecraft.client.multiplayer.resolver;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface AddressCheck {
	/**
	 * An AddressCheck implementation that allows all server addresses.
	 * Used in development builds where server blocklist functionality is not needed.
	 */
	AddressCheck ALLOW_ALL = new AddressCheck() {
		@Override
		public boolean isAllowed(ResolvedServerAddress resolvedServerAddress) {
			return true;
		}

		@Override
		public boolean isAllowed(ServerAddress serverAddress) {
			return true;
		}
	};

	boolean isAllowed(ResolvedServerAddress resolvedServerAddress);

	boolean isAllowed(ServerAddress serverAddress);
}
