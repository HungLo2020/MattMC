package net.irisshaders.iris.gl;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.opengl.GLDebugMessageAMDCallback;
import org.lwjgl.opengl.GLDebugMessageARBCallback;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.system.APIUtil;

import java.io.PrintStream;
import java.util.Stack;
import java.util.function.Consumer;

public final class GLDebug {
	private static DebugState debugState;

	/**
	 * Sets up debug callbacks
	 *
	 * @return 0 for failure, 1 for success, 2 for restart required.
	 */
	public static int setupDebugMessageCallback() {
		reloadDebugState();

		return setupDebugMessageCallback(System.out);
	}

	private static void trace(Consumer<String> output) {
		/*
		 * We can not just use a fixed stacktrace element offset, because some methods
		 * are intercepted and some are not. So, check the package name.
		 */
		StackTraceElement[] elems = filterStackTrace(new Throwable(), 4).getStackTrace();
		for (StackTraceElement ste : elems) {
			output.accept(ste.toString());
		}
	}

	public static Throwable filterStackTrace(Throwable throwable, int offset) {
		StackTraceElement[] elems = throwable.getStackTrace();
		StackTraceElement[] filtered = new StackTraceElement[elems.length];
		int j = 0;
		for (int i = offset; i < elems.length; i++) {
			filtered[j++] = elems[i];
		}
		StackTraceElement[] newElems = new StackTraceElement[j];
		System.arraycopy(filtered, 0, newElems, 0, j);
		throwable.setStackTrace(newElems);
		return throwable;
	}

	private static void printTrace(PrintStream stream) {
		trace(new Consumer<>() {
			boolean first = true;

			public void accept(String str) {
				if (first) {
					printDetail(stream, "Stacktrace", str);
					first = false;
				} else {
					printDetailLine(stream, "Stacktrace", str);
				}
			}
		});
	}

	public static int setupDebugMessageCallback(PrintStream stream) {
		GraphicsCapabilities caps = VulkanicAPI.getGraphicsCapabilities();
		VulkanicAPI.enable(VulkanicAPI.GL_DEBUG_OUTPUT_SYNCHRONOUS);
		if (caps.OpenGL43) {
			Iris.logger.info("[GL] Using OpenGL 4.3 for error logging.");
			GLDebugMessageCallback proc = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
				stream.println("[LWJGL] OpenGL debug message");
				printDetail(stream, "ID", String.format("0x%X", id));
				printDetail(stream, "Source", getDebugSource(source));
				printDetail(stream, "Type", getDebugType(type));
				printDetail(stream, "Severity", getDebugSeverity(severity));
				printDetail(stream, "Message", GLDebugMessageCallback.getMessage(length, message));
				printTrace(stream);
			});
			VulkanicAPI.glDebugMessageControl(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_HIGH, (int[]) null, true);
			VulkanicAPI.glDebugMessageControl(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_MEDIUM, (int[]) null, false);
			VulkanicAPI.glDebugMessageControl(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_LOW, (int[]) null, false);
			VulkanicAPI.glDebugMessageControl(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_NOTIFICATION, (int[]) null, false);
			VulkanicAPI.glDebugMessageCallback(proc, 0L);
			if ((VulkanicAPI.glGetInteger(VulkanicAPI.GL_CONTEXT_FLAGS) & 2) == 0) {
				Iris.logger.warn("[GL] Warning: A non-debug context may not produce any debug output.");
				VulkanicAPI.enable(VulkanicAPI.GL_DEBUG_OUTPUT);
				return 2;
			}
			return 1;
		} else if (caps.GL_KHR_debug) {
			Iris.logger.info("[GL] Using KHR_debug for error logging.");
			GLDebugMessageCallback proc = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
				stream.println("[LWJGL] OpenGL debug message");
				printDetail(stream, "ID", String.format("0x%X", id));
				printDetail(stream, "Source", getDebugSource(source));
				printDetail(stream, "Type", getDebugType(type));
				printDetail(stream, "Severity", getDebugSeverity(severity));
				printDetail(stream, "Message", GLDebugMessageCallback.getMessage(length, message));
				printTrace(stream);
			});
			VulkanicAPI.glDebugMessageControlKHR(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_HIGH, (int[]) null, true);
			VulkanicAPI.glDebugMessageControlKHR(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_MEDIUM, (int[]) null, false);
			VulkanicAPI.glDebugMessageControlKHR(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_LOW, (int[]) null, false);
			VulkanicAPI.glDebugMessageControlKHR(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_NOTIFICATION, (int[]) null, false);
			VulkanicAPI.glDebugMessageCallbackKHR(proc, 0L);
			if (caps.OpenGL30 && (VulkanicAPI.glGetInteger(VulkanicAPI.GL_CONTEXT_FLAGS) & 2) == 0) {
				Iris.logger.warn("[GL] Warning: A non-debug context may not produce any debug output.");
				VulkanicAPI.enable(VulkanicAPI.GL_DEBUG_OUTPUT);
				return 2;
			}
			return 1;
		} else if (caps.GL_ARB_debug_output) {
			Iris.logger.info("[GL] Using ARB_debug_output for error logging.");
			GLDebugMessageARBCallback proc = GLDebugMessageARBCallback.create((source, type, id, severity, length, message, userParam) -> {
				stream.println("[LWJGL] ARB_debug_output message");
				printDetail(stream, "ID", String.format("0x%X", id));
				printDetail(stream, "Source", getSourceARB(source));
				printDetail(stream, "Type", getTypeARB(type));
				printDetail(stream, "Severity", getSeverityARB(severity));
				printDetail(stream, "Message", GLDebugMessageARBCallback.getMessage(length, message));
				printTrace(stream);
			});
			VulkanicAPI.glDebugMessageControlARB(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_HIGH, (int[]) null, true);
			VulkanicAPI.glDebugMessageControlARB(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_MEDIUM, (int[]) null, false);
			VulkanicAPI.glDebugMessageControlARB(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_LOW, (int[]) null, false);
			VulkanicAPI.glDebugMessageControlARB(4352, 4352, VulkanicAPI.GL_DEBUG_SEVERITY_NOTIFICATION, (int[]) null, false);
			VulkanicAPI.glDebugMessageCallbackARB(proc, 0L);
			return 1;
		} else if (caps.GL_AMD_debug_output) {
			Iris.logger.info("[GL] Using AMD_debug_output for error logging.");
			GLDebugMessageAMDCallback proc = GLDebugMessageAMDCallback.create((id, category, severity, length, message, userParam) -> {
				stream.println("[LWJGL] AMD_debug_output message");
				printDetail(stream, "ID", String.format("0x%X", id));
				printDetail(stream, "Category", getCategoryAMD(category));
				printDetail(stream, "Severity", getSeverityAMD(severity));
				printDetail(stream, "Message", GLDebugMessageAMDCallback.getMessage(length, message));
				printTrace(stream);
			});
			VulkanicAPI.glDebugMessageEnableAMD(0, VulkanicAPI.GL_DEBUG_SEVERITY_HIGH, (int[]) null, true);
			VulkanicAPI.glDebugMessageEnableAMD(0, VulkanicAPI.GL_DEBUG_SEVERITY_MEDIUM, (int[]) null, false);
			VulkanicAPI.glDebugMessageEnableAMD(0, VulkanicAPI.GL_DEBUG_SEVERITY_LOW, (int[]) null, false);
			VulkanicAPI.glDebugMessageEnableAMD(0, VulkanicAPI.GL_DEBUG_SEVERITY_NOTIFICATION, (int[]) null, false);
			VulkanicAPI.glDebugMessageCallbackAMD(proc, 0L);
			return 1;
		} else {
			Iris.logger.info("[GL] No debug output implementation is available, cannot return debug info.");
			return 0;
		}
	}

	public static int disableDebugMessages() {
		GraphicsCapabilities caps = VulkanicAPI.getGraphicsCapabilities();
		if (caps.OpenGL43) {
			VulkanicAPI.glDebugMessageCallback(null, 0L);
			return 1;
		} else if (caps.GL_KHR_debug) {
			VulkanicAPI.glDebugMessageCallbackKHR(null, 0L);
			if (caps.OpenGL30 && (VulkanicAPI.glGetInteger(VulkanicAPI.GL_CONTEXT_FLAGS) & 2) == 0) {
				VulkanicAPI.disable(VulkanicAPI.GL_DEBUG_OUTPUT);
			}
			return 1;
		} else if (caps.GL_ARB_debug_output) {
			VulkanicAPI.glDebugMessageCallbackARB(null, 0L);
			return 1;
		} else if (caps.GL_AMD_debug_output) {
			VulkanicAPI.glDebugMessageCallbackAMD(null, 0L);
			return 1;
		} else {
			Iris.logger.info("[GL] No debug output implementation is available, cannot disable debug info.");
			return 0;
		}
	}

	private static void printDetail(PrintStream stream, String type, String message) {
		stream.printf("\t%s: %s\n", type, message);
	}

	private static void printDetailLine(PrintStream stream, String type, String message) {
		stream.append("    ");
		for (int i = 0; i < type.length(); i++) {
			stream.append(" ");
		}
		stream.append(message).append("\n");
	}

	private static String getDebugSource(int source) {
		switch (source) {
			case 33350 -> {
				return "API";
			}
			case 33351 -> {
				return "WINDOW SYSTEM";
			}
			case 33352 -> {
				return "SHADER COMPILER";
			}
			case 33353 -> {
				return "THIRD PARTY";
			}
			case 33354 -> {
				return "APPLICATION";
			}
			case 33355 -> {
				return "OTHER";
			}
			default -> {
				return APIUtil.apiUnknownToken(source);
			}
		}
	}

	private static String getDebugType(int type) {
		return switch (type) {
			case 33356 -> "ERROR";
			case 33357 -> "DEPRECATED BEHAVIOR";
			case 33358 -> "UNDEFINED BEHAVIOR";
			case 33359 -> "PORTABILITY";
			case 33360 -> "PERFORMANCE";
			case 33361 -> "OTHER";
			case 33384 -> "MARKER";
			default -> APIUtil.apiUnknownToken(type);
		};
	}

	private static String getDebugSeverity(int severity) {
		return switch (severity) {
			case 33387 -> "NOTIFICATION";
			case 37190 -> "HIGH";
			case 37191 -> "MEDIUM";
			case 37192 -> "LOW";
			default -> APIUtil.apiUnknownToken(severity);
		};
	}

	private static String getSourceARB(int source) {
		return switch (source) {
			case 33350 -> "API";
			case 33351 -> "WINDOW SYSTEM";
			case 33352 -> "SHADER COMPILER";
			case 33353 -> "THIRD PARTY";
			case 33354 -> "APPLICATION";
			case 33355 -> "OTHER";
			default -> APIUtil.apiUnknownToken(source);
		};
	}

	private static String getTypeARB(int type) {
		return switch (type) {
			case 33356 -> "ERROR";
			case 33357 -> "DEPRECATED BEHAVIOR";
			case 33358 -> "UNDEFINED BEHAVIOR";
			case 33359 -> "PORTABILITY";
			case 33360 -> "PERFORMANCE";
			case 33361 -> "OTHER";
			default -> APIUtil.apiUnknownToken(type);
		};
	}

	private static String getSeverityARB(int severity) {
		return switch (severity) {
			case 37190 -> "HIGH";
			case 37191 -> "MEDIUM";
			case 37192 -> "LOW";
			default -> APIUtil.apiUnknownToken(severity);
		};
	}

	private static String getCategoryAMD(int category) {
		return switch (category) {
			case 37193 -> "API ERROR";
			case 37194 -> "WINDOW SYSTEM";
			case 37195 -> "DEPRECATION";
			case 37196 -> "UNDEFINED BEHAVIOR";
			case 37197 -> "PERFORMANCE";
			case 37198 -> "SHADER COMPILER";
			case 37199 -> "APPLICATION";
			case 37200 -> "OTHER";
			default -> APIUtil.apiUnknownToken(category);
		};
	}

	private static String getSeverityAMD(int severity) {
		return switch (severity) {
			case 37190 -> "HIGH";
			case 37191 -> "MEDIUM";
			case 37192 -> "LOW";
			default -> APIUtil.apiUnknownToken(severity);
		};
	}

	public static void reloadDebugState() {
		GraphicsCapabilities caps = VulkanicAPI.getGraphicsCapabilities();
		if (Iris.getIrisConfig().areDebugOptionsEnabled() && (caps.GL_KHR_debug || caps.OpenGL43)) {
			debugState = new KHRDebugState();
		} else {
			debugState = new UnsupportedDebugState();
		}
	}

	public static void nameObject(int id, int object, String name) {
		debugState.nameObject(id, object, name);
	}

	public static void pushGroup(int id, String name) {
		debugState.pushGroup(id, name);
	}

	public static void popGroup() {
		debugState.popGroup();
	}

	private interface DebugState {
		void nameObject(int id, int object, String name);

		void pushGroup(int id, String name);

		void popGroup();
	}

	private static class KHRDebugState implements DebugState {
		// Let's see how bad this goes
		private static final boolean ENABLE_DEBUG_GROUPS = true;
		private int stackSize;
		private final Stack<String> stack = new Stack<>();

		@Override
		public void nameObject(int id, int object, String name) {
			VulkanicAPI.labelDebugObject(id, object, name);
		}

		@Override
		public void pushGroup(int id, String name) {
			if (ENABLE_DEBUG_GROUPS) {
				VulkanicAPI.enterDebugGroup(VulkanicAPI.GL_DEBUG_SOURCE_APPLICATION, id, name);
				stack.push(name);
				stackSize += 1;
			}
		}

		@Override
		public void popGroup() {
			if (ENABLE_DEBUG_GROUPS) {
				if (stackSize != 0) {
					VulkanicAPI.exitDebugGroup();
					stack.pop();
					stackSize -= 1;
				}
			}
		}
	}

	private static class UnsupportedDebugState implements DebugState {
		@Override
		public void nameObject(int id, int object, String name) {
		}

		@Override
		public void pushGroup(int id, String name) {
		}

		@Override
		public void popGroup() {
		}
	}
}
