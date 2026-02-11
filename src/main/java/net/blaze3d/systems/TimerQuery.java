package net.blaze3d.systems;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

@Environment(EnvType.CLIENT)
public class TimerQuery {
	private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
	private int nextQueryName;

	public static TimerQuery getInstance() {
		return TimerQuery.TimerQueryLazyLoader.INSTANCE;
	}

	public boolean isRecording() {
		return this.nextQueryName != 0;
	}

	public void beginProfile() {
		RenderSystem.assertOnRenderThread();
		if (this.nextQueryName != 0) {
			throw new IllegalStateException("Current profile not ended");
		} else {
			this.nextQueryName = VulkanicAPI.generateQueryObject(CTX);
			VulkanicAPI.initiateQuery(CTX, 35007, this.nextQueryName);
		}
	}

	public TimerQuery.FrameProfile endProfile() {
		RenderSystem.assertOnRenderThread();
		if (this.nextQueryName == 0) {
			throw new IllegalStateException("endProfile called before beginProfile");
		} else {
			VulkanicAPI.concludeQuery(CTX, 35007);
			TimerQuery.FrameProfile frameProfile = new TimerQuery.FrameProfile(this.nextQueryName);
			this.nextQueryName = 0;
			return frameProfile;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class FrameProfile {
		private static final long NO_RESULT = 0L;
		private static final long CANCELLED_RESULT = -1L;
		private final int queryName;
		private long result;

		FrameProfile(int i) {
			this.queryName = i;
		}

		public void cancel() {
			RenderSystem.assertOnRenderThread();
			if (this.result == 0L) {
				this.result = -1L;
				VulkanicAPI.disposeQueryObject(CTX, this.queryName);
			}
		}

		public boolean isDone() {
			RenderSystem.assertOnRenderThread();
			if (this.result != 0L) {
				return true;
			} else if (1 == VulkanicAPI.retrieveQueryObjectInt(CTX, this.queryName, 34919)) {
				this.result = VulkanicAPI.retrieveQueryObjectInt64(CTX, this.queryName, 34918);
				VulkanicAPI.disposeQueryObject(CTX, this.queryName);
				return true;
			} else {
				return false;
			}
		}

		public long get() {
			RenderSystem.assertOnRenderThread();
			if (this.result == 0L) {
				this.result = VulkanicAPI.retrieveQueryObjectInt64(CTX, this.queryName, 34918);
				VulkanicAPI.disposeQueryObject(CTX, this.queryName);
			}

			return this.result;
		}
	}

	@Environment(EnvType.CLIENT)
	static class TimerQueryLazyLoader {
		static final TimerQuery INSTANCE = instantiate();

		private TimerQueryLazyLoader() {
		}

		private static TimerQuery instantiate() {
			return new TimerQuery();
		}
	}
}
