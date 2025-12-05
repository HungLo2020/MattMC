package net.minecraft.util.debug;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public interface DebugValueSource {
	void registerDebugValues(ServerLevel serverLevel, DebugValueSource.Registration registration);

	public interface Registration {
		<T> void register(DebugSubscription<T> debugSubscription, DebugValueSource.ValueGetter<T> valueGetter);
	}

	public interface ValueGetter<T> {
		@Nullable
		T get();
	}
}
