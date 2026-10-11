package net.minecraft.util;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import net.logging.LogUtils;
import org.slf4j.Logger;

/**
 * The Java logger behind Rust's `core::java_log`: native code logs through
 * one upcall registered on first use.
 */
public final class NativeLog {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int WARN = 1;
	private static final int ERROR = 2;
	private static volatile boolean registered;

	private NativeLog() {
	}

	/** Registers the upcall once; native subsystems call this before logging. */
	public static void ensureRegistered() {
		if (registered) {
			return;
		}
		synchronized (NativeLog.class) {
			if (registered) {
				return;
			}
			try {
				MethodHandle target = MethodHandles.lookup().findStatic(NativeLog.class, "log",
					MethodType.methodType(void.class, int.class, MemorySegment.class, long.class));
				MemorySegment stub = Linker.nativeLinker().upcallStub(target,
					FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), Arena.global());
				MethodHandle register = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_java_log_register",
					FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
				register.invokeExact(stub);
				registered = true;
			} catch (Throwable throwable) {
				throw new IllegalStateException("Register native log upcall failed", throwable);
			}
		}
	}

	private static void log(int level, MemorySegment message, long length) {
		String text = new String(message.reinterpret(length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
		switch (level) {
			case ERROR -> LOGGER.error(text);
			case WARN -> LOGGER.warn(text);
			default -> LOGGER.info(text);
		}
	}
}
