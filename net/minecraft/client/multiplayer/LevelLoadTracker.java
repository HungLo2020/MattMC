package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.level.progress.LevelLoadProgressTracker;
import net.minecraft.server.level.progress.LevelLoadListener.Stage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LevelLoadTracker implements LevelLoadListener {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final long CLIENT_WAIT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30L);
	public static final long LEVEL_LOAD_CLOSE_DELAY_MS = 500L;
	private final LevelLoadProgressTracker serverProgressTracker = new LevelLoadProgressTracker(true);
	@Nullable
	private ChunkLoadStatusView serverChunkStatusView;
	@Nullable
	private volatile Stage serverStage;
	@Nullable
	private LevelLoadTracker.ClientState clientState;
	private final long closeDelayMs;

	public LevelLoadTracker() {
		this(0L);
	}

	public LevelLoadTracker(long l) {
		this.closeDelayMs = l;
	}

	public void setServerChunkStatusView(ChunkLoadStatusView chunkLoadStatusView) {
		this.serverChunkStatusView = chunkLoadStatusView;
	}

	public void startClientLoad(LocalPlayer localPlayer, ClientLevel clientLevel, LevelRenderer levelRenderer) {
		this.clientState = new LevelLoadTracker.WaitingForServer(localPlayer, clientLevel, levelRenderer, Util.getMillis() + CLIENT_WAIT_TIMEOUT_MS);
	}

	public void tickClientLoad() {
		if (this.clientState != null) {
			this.clientState = this.clientState.tick();
		}
	}

	public boolean isLevelReady() {
		if (this.clientState instanceof LevelLoadTracker.ClientLevelReady(long var8)) {
			long var5 = var8;
			if (Util.getMillis() >= var5 + this.closeDelayMs) {
				return true;
			}
		}

		return false;
	}

	public void loadingPacketsReceived() {
		if (this.clientState != null) {
			this.clientState = this.clientState.loadingPacketsReceived();
		}
	}

	public void start(Stage stage, int i) {
		this.serverProgressTracker.start(stage, i);
		this.serverStage = stage;
	}

	public void update(Stage stage, int i, int j) {
		this.serverProgressTracker.update(stage, i, j);
	}

	public void finish(Stage stage) {
		this.serverProgressTracker.finish(stage);
	}

	public void updateFocus(ResourceKey<Level> resourceKey, ChunkPos chunkPos) {
		if (this.serverChunkStatusView != null) {
			this.serverChunkStatusView.moveTo(resourceKey, chunkPos);
		}
	}

	@Nullable
	public ChunkLoadStatusView statusView() {
		return this.serverChunkStatusView;
	}

	public float serverProgress() {
		return this.serverProgressTracker.get();
	}

	public boolean hasProgress() {
		return this.serverStage != null;
	}

	@Environment(EnvType.CLIENT)
	record ClientLevelReady(long readyAt) implements LevelLoadTracker.ClientState {
	}

	@Environment(EnvType.CLIENT)
	sealed interface ClientState permits LevelLoadTracker.WaitingForServer, LevelLoadTracker.WaitingForPlayerChunk, LevelLoadTracker.ClientLevelReady {
		default LevelLoadTracker.ClientState tick() {
			return this;
		}

		default LevelLoadTracker.ClientState loadingPacketsReceived() {
			return this;
		}
	}

	@Environment(EnvType.CLIENT)
	record WaitingForPlayerChunk(LocalPlayer player, ClientLevel level, LevelRenderer levelRenderer, long timeoutAfter) implements LevelLoadTracker.ClientState {
		@Override
		public LevelLoadTracker.ClientState tick() {
			return (LevelLoadTracker.ClientState)(this.isReady() ? new LevelLoadTracker.ClientLevelReady(Util.getMillis()) : this);
		}

		private boolean isReady() {
			if (Util.getMillis() > this.timeoutAfter) {
				LevelLoadTracker.LOGGER.warn("Timed out while waiting for the client to load chunks, letting the player into the world anyway");
				return true;
			} else {
				BlockPos blockPos = this.player.blockPosition();
				return !this.level.isOutsideBuildHeight(blockPos.getY()) && !this.player.isSpectator() && this.player.isAlive()
					? this.levelRenderer.isSectionCompiled(blockPos)
					: true;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	record WaitingForServer(LocalPlayer player, ClientLevel level, LevelRenderer levelRenderer, long timeoutAfter) implements LevelLoadTracker.ClientState {
		@Override
		public LevelLoadTracker.ClientState loadingPacketsReceived() {
			return new LevelLoadTracker.WaitingForPlayerChunk(this.player, this.level, this.levelRenderer, this.timeoutAfter);
		}
	}
}
