package net.blaze3d.systems;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicAPI;

@Environment(EnvType.CLIENT)
public class TimerQuery {
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
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			this.nextQueryName = VulkanicAPI.generateQueryObject(ctx);
			VulkanicAPI.beginTimeElapsedQuery(ctx, this.nextQueryName);
		}
	}

	public TimerQuery.FrameProfile endProfile() {
		RenderSystem.assertOnRenderThread();
		if (this.nextQueryName == 0) {
			throw new IllegalStateException("endProfile called before beginProfile");
		} else {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.endTimeElapsedQuery(ctx);
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
				net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
				VulkanicAPI.disposeQueryObject(ctx, this.queryName);
			}
		}

		public boolean isDone() {
			RenderSystem.assertOnRenderThread();
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			if (this.result != 0L) {
				return true;
			} else if (VulkanicAPI.isQueryResultAvailable(ctx, this.queryName)) {
				this.result = VulkanicAPI.getQueryResultInt64(ctx, this.queryName);
				VulkanicAPI.disposeQueryObject(ctx, this.queryName);
				return true;
			} else {
				return false;
			}
		}

		public long get() {
			RenderSystem.assertOnRenderThread();
			if (this.result == 0L) {
				net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
				this.result = VulkanicAPI.getQueryResultInt64(ctx, this.queryName);
				VulkanicAPI.disposeQueryObject(ctx, this.queryName);
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
