package net.minecraft.util.profiling;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public final class TracyCompat {
	private static final Class<?> CLIENT_CLASS = findClass("com.mojang.jtracy.TracyClient");
	private static final Method LOAD = method(CLIENT_CLASS, "load");
	private static final Method IS_AVAILABLE = method(CLIENT_CLASS, "isAvailable");
	private static final Method REPORT_APP_INFO = method(CLIENT_CLASS, "reportAppInfo", String.class);
	private static final Method MESSAGE = method(CLIENT_CLASS, "message", String.class);
	private static final Method MESSAGE_COLOR = method(CLIENT_CLASS, "message", String.class, int.class);
	private static final Method MARK_FRAME = method(CLIENT_CLASS, "markFrame");
	private static final Method FRAME_IMAGE = method(CLIENT_CLASS, "frameImage", ByteBuffer.class, int.class, int.class, int.class, boolean.class);
	private static final Method SET_THREAD_NAME = method(CLIENT_CLASS, "setThreadName", String.class, int.class);
	private static final Method CREATE_MEMORY_POOL = method(CLIENT_CLASS, "createMemoryPool", String.class);
	private static final Method CREATE_PLOT = method(CLIENT_CLASS, "createPlot", String.class);
	private static final Method CREATE_DISCONTINUOUS_FRAME = method(CLIENT_CLASS, "createDiscontinuousFrame", String.class);
	private static final Method BEGIN_ZONE_SIMPLE = method(CLIENT_CLASS, "beginZone", String.class, boolean.class);
	private static final Method BEGIN_ZONE_SOURCE = method(CLIENT_CLASS, "beginZone", String.class, String.class, String.class, int.class);

	private TracyCompat() {
	}

	public static void load() {
		invoke(LOAD);
	}

	public static boolean isAvailable() {
		Object result = invoke(IS_AVAILABLE);
		return result instanceof Boolean value && value;
	}

	public static void reportAppInfo(String info) {
		if (isAvailable()) {
			invoke(REPORT_APP_INFO, info);
		}
	}

	public static void message(String message) {
		if (isAvailable()) {
			invoke(MESSAGE, message);
		}
	}

	public static void message(String message, int color) {
		if (isAvailable()) {
			invoke(MESSAGE_COLOR, message, color);
		}
	}

	public static void markFrame() {
		if (isAvailable()) {
			invoke(MARK_FRAME);
		}
	}

	public static void frameImage(ByteBuffer data, int width, int height, int delay, boolean flip) {
		if (isAvailable()) {
			invoke(FRAME_IMAGE, data, width, height, delay, flip);
		}
	}

	public static void setThreadName(String name, int hash) {
		if (isAvailable()) {
			invoke(SET_THREAD_NAME, name, hash);
		}
	}

	public static Zone beginZone(String name, boolean active) {
		if (!isAvailable()) {
			return Zone.NOOP;
		}
		return new Zone(invoke(BEGIN_ZONE_SIMPLE, name, active));
	}

	public static Zone beginZone(String name, String function, String file, int line) {
		if (!isAvailable()) {
			return Zone.NOOP;
		}
		return new Zone(invoke(BEGIN_ZONE_SOURCE, name, function, file, line));
	}

	public static MemoryPool createMemoryPool(String name) {
		return new MemoryPool(invoke(CREATE_MEMORY_POOL, name));
	}

	public static Plot createPlot(String name) {
		return new Plot(invoke(CREATE_PLOT, name));
	}

	public static DiscontinuousFrame createDiscontinuousFrame(String name) {
		return new DiscontinuousFrame(invoke(CREATE_DISCONTINUOUS_FRAME, name));
	}

	private static Class<?> findClass(String name) {
		try {
			return Class.forName(name);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Method method(Class<?> type, String name, Class<?>... parameters) {
		if (type == null) {
			return null;
		}
		try {
			Method method = type.getMethod(name, parameters);
			method.setAccessible(true);
			return method;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object invoke(Method method, Object... arguments) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(null, arguments);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void invokeInstance(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
		if (target == null) {
			return;
		}
		try {
			Method method = target.getClass().getMethod(name, parameterTypes);
			method.setAccessible(true);
			method.invoke(target, arguments);
		} catch (Throwable ignored) {
		}
	}

	public static final class Zone implements AutoCloseable {
		private static final Zone NOOP = new Zone(null);
		private final Object delegate;

		private Zone(Object delegate) {
			this.delegate = delegate;
		}

		public void addText(String text) {
			invokeInstance(this.delegate, "addText", new Class<?>[] {String.class}, text);
		}

		public void addValue(long value) {
			invokeInstance(this.delegate, "addValue", new Class<?>[] {long.class}, value);
		}

		public void setColor(int color) {
			invokeInstance(this.delegate, "setColor", new Class<?>[] {int.class}, color);
		}

		@Override
		public void close() {
			invokeInstance(this.delegate, "close", new Class<?>[0]);
		}
	}

	public static final class MemoryPool {
		private final Object delegate;

		private MemoryPool(Object delegate) {
			this.delegate = delegate;
		}

		public void malloc(long pointer, int size) {
			invokeInstance(this.delegate, "malloc", new Class<?>[] {long.class, int.class}, pointer, size);
		}

		public void free(long pointer) {
			invokeInstance(this.delegate, "free", new Class<?>[] {long.class}, pointer);
		}
	}

	public static final class Plot {
		private final Object delegate;

		private Plot(Object delegate) {
			this.delegate = delegate;
		}

		public void setValue(int value) {
			invokeInstance(this.delegate, "setValue", new Class<?>[] {int.class}, value);
		}
	}

	public static final class DiscontinuousFrame {
		private final Object delegate;

		private DiscontinuousFrame(Object delegate) {
			this.delegate = delegate;
		}

		public void start() {
			invokeInstance(this.delegate, "start", new Class<?>[0]);
		}

		public void end() {
			invokeInstance(this.delegate, "end", new Class<?>[0]);
		}
	}
}
