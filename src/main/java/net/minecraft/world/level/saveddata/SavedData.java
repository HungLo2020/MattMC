package net.minecraft.world.level.saveddata;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public abstract class SavedData {
	private boolean dirty;

	public void setDirty() {
		this.setDirty(true);
	}

	public void setDirty(boolean bl) {
		this.dirty = bl;
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public record Context(@Nullable ServerLevel level, long worldSeed) {
		public Context(ServerLevel serverLevel) {
			this(serverLevel, serverLevel.getSeed());
		}

		public ServerLevel levelOrThrow() {
			return (ServerLevel)Objects.requireNonNull(this.level);
		}
	}
}
