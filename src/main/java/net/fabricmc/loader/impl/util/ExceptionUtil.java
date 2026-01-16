package net.fabricmc.loader.impl.util;

import java.io.UncheckedIOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public final class ExceptionUtil {
	private static final boolean THROW_DIRECTLY = SystemProperties.isSet(SystemProperties.DEBUG_THROW_DIRECTLY);

	public static <T extends Throwable> T gatherExceptions(Throwable exc, T prev, Function<Throwable, T> mainExcFactory) throws T {
		exc = unwrap(exc);

		if (THROW_DIRECTLY) throw mainExcFactory.apply(exc);

		if (prev == null) {
			return mainExcFactory.apply(exc);
		} else if (exc != prev) {
			for (Throwable t : prev.getSuppressed()) {
				if (exc.equals(t)) return prev;
			}

			prev.addSuppressed(exc);
		}

		return prev;
	}

	public static RuntimeException wrap(Throwable exc) {
		if (exc instanceof RuntimeException) return (RuntimeException) exc;

		exc = unwrap(exc);
		if (exc instanceof RuntimeException) return (RuntimeException) exc;

		return new WrappedException(exc);
	}

	private static Throwable unwrap(Throwable exc) {
		if (exc instanceof WrappedException
				|| exc instanceof UncheckedIOException
				|| exc instanceof ExecutionException
				|| exc instanceof CompletionException) {
			Throwable ret = exc.getCause();
			if (ret != null) return unwrap(ret);
		}

		return exc;
	}

	@SuppressWarnings("serial")
	public static final class WrappedException extends RuntimeException {
		public WrappedException(Throwable cause) {
			super(cause);
		}
	}
}
