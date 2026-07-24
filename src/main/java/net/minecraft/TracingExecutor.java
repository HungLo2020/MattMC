package net.minecraft;

import net.minecraft.util.profiling.TracyCompat;
import net.minecraft.util.profiling.TracyCompat.Zone;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public record TracingExecutor(ExecutorService service) implements Executor {
	public Executor forName(String string) {
		// DH: Run world gen tasks on current thread instead of MC thread pools
		if (com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment.isThisDhWorldGenThread()) {
			return new com.seibel.distanthorizons.core.util.objects.RunOnThisThreadExecutorService();
		}
		
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			return runnable -> this.service.execute(() -> {
				Thread thread = Thread.currentThread();
				String string2 = thread.getName();
				thread.setName(string);

				try (Zone zone = TracyCompat.beginZone(string, SharedConstants.IS_RUNNING_IN_IDE)) {
					runnable.run();
				} finally {
					thread.setName(string2);
				}
			});
		} else {
			return TracyCompat.isAvailable() ? (Executor)(runnable -> this.service.execute(() -> {
				try (Zone zone = TracyCompat.beginZone(string, SharedConstants.IS_RUNNING_IN_IDE)) {
					runnable.run();
				}
			})) : this.service;
		}
	}

	public void execute(Runnable runnable) {
		this.service.execute(wrapUnnamed(runnable));
	}

	public void shutdownAndAwait(long l, TimeUnit timeUnit) {
		this.service.shutdown();

		boolean bl;
		try {
			bl = this.service.awaitTermination(l, timeUnit);
		} catch (InterruptedException var6) {
			bl = false;
		}

		if (!bl) {
			this.service.shutdownNow();
		}
	}

	private static Runnable wrapUnnamed(Runnable runnable) {
		return !TracyCompat.isAvailable() ? runnable : () -> {
			try (Zone zone = TracyCompat.beginZone("task", SharedConstants.IS_RUNNING_IN_IDE)) {
				runnable.run();
			}
		};
	}
}
