package net.blaze3d.systems;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.gui.RustGalFrameCoordinator;

/**
 * Per-frame GPU time for F3's GPU usage entry and profiler recordings,
 * measured by Rust VulkanicGAL timestamps. A profile completes when Rust
 * reports a newer timed frame. Backends without timestamps (Rust OpenGL)
 * complete with no measurement after a few frames instead of waiting forever.
 */
@Environment(EnvType.CLIENT)
public class TimerQuery {
	private static final long FRAMES_WITHOUT_TIMESTAMPS = 8L;
	private boolean recording;

	public static TimerQuery getInstance() {
		return TimerQuery.TimerQueryLazyLoader.INSTANCE;
	}

	public boolean isRecording() {
		return this.recording;
	}

	public void beginProfile() {
		RenderSystem.assertOnRenderThread();
		if (this.recording) {
			throw new IllegalStateException("Current profile not ended");
		}
		RustGalFrameCoordinator.setGpuTimestampsRequested(true);
		this.recording = true;
	}

	public TimerQuery.FrameProfile endProfile() {
		RenderSystem.assertOnRenderThread();
		if (!this.recording) {
			throw new IllegalStateException("endProfile called before beginProfile");
		}
		this.recording = false;
		return new TimerQuery.FrameProfile(
			RustGalFrameCoordinator.latestGpuFrameSubmission(),
			RustGalFrameCoordinator.presentedFrameCount()
		);
	}

	/** Stops requesting timestamps once nothing is measuring GPU time. */
	public void stopRequesting() {
		RustGalFrameCoordinator.setGpuTimestampsRequested(false);
	}

	@Environment(EnvType.CLIENT)
	public static class FrameProfile {
		private final long submissionAtEnd;
		private final long frameAtEnd;
		private long result = -1L;

		FrameProfile(long submissionAtEnd, long frameAtEnd) {
			this.submissionAtEnd = submissionAtEnd;
			this.frameAtEnd = frameAtEnd;
		}

		public void cancel() {
			RenderSystem.assertOnRenderThread();
			if (this.result < 0L) {
				this.result = 0L;
			}
		}

		public boolean isDone() {
			RenderSystem.assertOnRenderThread();
			if (this.result >= 0L) {
				return true;
			}
			if (RustGalFrameCoordinator.latestGpuFrameSubmission() != this.submissionAtEnd) {
				this.result = RustGalFrameCoordinator.latestGpuFrameNanos();
				return true;
			}
			if (RustGalFrameCoordinator.presentedFrameCount() - this.frameAtEnd >= FRAMES_WITHOUT_TIMESTAMPS) {
				this.result = 0L;
				return true;
			}
			return false;
		}

		/** GPU nanoseconds of the measured frame, or 0 when unavailable. */
		public long get() {
			RenderSystem.assertOnRenderThread();
			return this.isDone() ? this.result : 0L;
		}
	}

	@Environment(EnvType.CLIENT)
	static class TimerQueryLazyLoader {
		static final TimerQuery INSTANCE = new TimerQuery();

		private TimerQueryLazyLoader() {
		}
	}
}
