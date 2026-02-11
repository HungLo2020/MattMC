package net.blaze3d.opengl;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.logging.LogUtils;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlDebug {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
	private static final int CIRCULAR_LOG_SIZE = 10;
	private final Queue<GlDebug.LogEntry> MESSAGE_BUFFER = EvictingQueue.create(10);
	@Nullable
	private volatile GlDebug.LogEntry lastEntry;
	private static final List<Integer> DEBUG_LEVELS = ImmutableList.of(37190, 37191, 37192, 33387);
	private static final List<Integer> DEBUG_LEVELS_ARB = ImmutableList.of(37190, 37191, 37192);

	private static String printUnknownToken(int i) {
		return "Unknown (0x" + Integer.toHexString(i).toUpperCase() + ")";
	}

	public static String sourceToString(int i) {
		switch (i) {
			case 33350:
				return "API";
			case 33351:
				return "WINDOW SYSTEM";
			case 33352:
				return "SHADER COMPILER";
			case 33353:
				return "THIRD PARTY";
			case 33354:
				return "APPLICATION";
			case 33355:
				return "OTHER";
			default:
				return printUnknownToken(i);
		}
	}

	public static String typeToString(int i) {
		switch (i) {
			case 33356:
				return "ERROR";
			case 33357:
				return "DEPRECATED BEHAVIOR";
			case 33358:
				return "UNDEFINED BEHAVIOR";
			case 33359:
				return "PORTABILITY";
			case 33360:
				return "PERFORMANCE";
			case 33361:
				return "OTHER";
			case 33384:
				return "MARKER";
			default:
				return printUnknownToken(i);
		}
	}

	public static String severityToString(int i) {
		switch (i) {
			case 33387:
				return "NOTIFICATION";
			case 37190:
				return "HIGH";
			case 37191:
				return "MEDIUM";
			case 37192:
				return "LOW";
			default:
				return printUnknownToken(i);
		}
	}

	private void handleDebugMessage(String message) {
		// Parse message for log entry (simplified approach)
		GlDebug.LogEntry logEntry;
		synchronized (this.MESSAGE_BUFFER) {
			logEntry = new GlDebug.LogEntry(0, 0, 0, 0, message);
			this.MESSAGE_BUFFER.add(logEntry);
			this.lastEntry = logEntry;
		}

		LOGGER.info("OpenGL debug message: {}", message);
	}

	public List<String> getLastOpenGlDebugMessages() {
		synchronized (this.MESSAGE_BUFFER) {
			List<String> list = Lists.<String>newArrayListWithCapacity(this.MESSAGE_BUFFER.size());

			for (GlDebug.LogEntry logEntry : this.MESSAGE_BUFFER) {
				list.add(logEntry + " x " + logEntry.count);
			}

			return list;
		}
	}

	@Nullable
	public static GlDebug enableDebugCallback(int verbosity, boolean sync, Set<String> extensions) {
		if (verbosity <= 0) {
			return null;
		}
		
		GlDebug debugSystem = new GlDebug();
		
		// Try KHR_debug first
		if (VulkanicAPI.supportsKhrDebug(CTX) && GlDevice.USE_GL_KHR_debug) {
			extensions.add("GL_KHR_debug");
			VulkanicAPI.setupKhrDebugSystem(CTX, verbosity, sync, debugSystem::handleDebugMessage);
			return debugSystem;
		}
		
		// Fall back to ARB_debug_output
		if (VulkanicAPI.supportsArbDebugOutput(CTX) && GlDevice.USE_GL_ARB_debug_output) {
			extensions.add("GL_ARB_debug_output");
			VulkanicAPI.setupArbDebugSystem(CTX, verbosity, sync, debugSystem::handleDebugMessage);
			return debugSystem;
		}
		
		return null;
	}

	@Environment(EnvType.CLIENT)
	static class LogEntry {
		private final int id;
		private final int source;
		private final int type;
		private final int severity;
		private final String message;
		int count = 1;

		LogEntry(int i, int j, int k, int l, String string) {
			this.id = k;
			this.source = i;
			this.type = j;
			this.severity = l;
			this.message = string;
		}

		boolean isSame(int i, int j, int k, int l, String string) {
			return j == this.type && i == this.source && k == this.id && l == this.severity && string.equals(this.message);
		}

		public String toString() {
			return "id="
				+ this.id
				+ ", source="
				+ GlDebug.sourceToString(this.source)
				+ ", type="
				+ GlDebug.typeToString(this.type)
				+ ", severity="
				+ GlDebug.severityToString(this.severity)
				+ ", message='"
				+ this.message
				+ "'";
		}
	}
}
