package net.minecraft.client.dev;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import net.blaze3d.platform.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Diagnostic-only RenderDoc trigger used by DevUtils/Audit/Capture.py.
 */
public final class RenderDocCaptureHook {
	private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-RenderDocCapture");
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.renderdocCapture");
	private static final String LIBRARY_PATH = System.getProperty("mattmc.dev.renderdocCapture.library", "").trim();
	private static final String PATH_TEMPLATE = System.getProperty("mattmc.dev.renderdocCapture.pathTemplate", "").trim();
	private static final String BACKEND = System.getProperty("mattmc.dev.renderdocCapture.backend", "").trim();
	private static final int API_VERSION_1_6_0 = 10600;
	private static final int FN_SET_CAPTURE_FILE_PATH_TEMPLATE = 11;
	private static final int FN_TRIGGER_CAPTURE = 15;
	private static final int FN_SET_ACTIVE_WINDOW = 18;
	private static final int FN_START_FRAME_CAPTURE = 19;
	private static final int FN_END_FRAME_CAPTURE = 21;

	private static boolean initialized;
	private static boolean unavailable;
	private static boolean triggered;
	private static boolean frameCaptureStarted;
	private static boolean frameCaptureEnded;
	private static Pointer api;

	private RenderDocCaptureHook() {
	}

	public static boolean triggerNextFrameOnce(String context) {
		if (!ENABLED || triggered || unavailable) {
			return false;
		}
		if (!ensureInitialized()) {
			return false;
		}
		Function triggerCapture = functionAt(FN_TRIGGER_CAPTURE);
		if (triggerCapture == null) {
			unavailable = true;
			LOGGER.warn("RenderDoc API TriggerCapture function is unavailable");
			return false;
		}
		try {
			triggerCapture.invokeVoid(new Object[0]);
			triggered = true;
			LOGGER.info("Triggered RenderDoc capture for next frame ({})", context);
			return true;
		} catch (Throwable throwable) {
			unavailable = true;
			LOGGER.warn("Unable to trigger RenderDoc capture", throwable);
			return false;
		}
	}

	public static boolean beginFrameCaptureOnce(Window window, String context) {
		if (!ENABLED || frameCaptureStarted || unavailable) {
			return false;
		}
		if ("opengl".equalsIgnoreCase(BACKEND)) {
			LOGGER.info("Skipping manual RenderDoc frame capture for OpenGL; using trigger-only capture ({})", context);
			return false;
		}
		if (!ensureInitialized()) {
			return false;
		}
		Function startFrameCapture = functionAt(FN_START_FRAME_CAPTURE);
		if (startFrameCapture == null) {
			unavailable = true;
			LOGGER.warn("RenderDoc API StartFrameCapture function is unavailable");
			return false;
		}
			try {
				Pointer windowPointer = captureWindowPointer(window);
				Function setActiveWindow = functionAt(FN_SET_ACTIVE_WINDOW);
				if (setActiveWindow != null) {
					setActiveWindow.invokeVoid(new Object[] {Pointer.NULL, windowPointer});
				}
				startFrameCapture.invokeVoid(new Object[] {Pointer.NULL, windowPointer});
				frameCaptureStarted = true;
				LOGGER.info("Started RenderDoc frame capture ({}) backend={} windowPointer={}", context, BACKEND, Pointer.nativeValue(windowPointer));
				return true;
		} catch (Throwable throwable) {
			unavailable = true;
			LOGGER.warn("Unable to start RenderDoc frame capture", throwable);
			return false;
		}
	}

	public static boolean endFrameCaptureOnce(Window window, String context) {
		if (!ENABLED || !frameCaptureStarted || frameCaptureEnded || unavailable) {
			return false;
		}
		Function endFrameCapture = functionAt(FN_END_FRAME_CAPTURE);
		if (endFrameCapture == null) {
			unavailable = true;
			LOGGER.warn("RenderDoc API EndFrameCapture function is unavailable");
			return false;
		}
			try {
				Pointer windowPointer = captureWindowPointer(window);
				int result = endFrameCapture.invokeInt(new Object[] {Pointer.NULL, windowPointer});
				frameCaptureEnded = true;
				LOGGER.info("Ended RenderDoc frame capture ({}) backend={} windowPointer={} result={}", context, BACKEND, Pointer.nativeValue(windowPointer), result);
				return result != 0;
		} catch (Throwable throwable) {
			unavailable = true;
			LOGGER.warn("Unable to end RenderDoc frame capture", throwable);
			return false;
		}
	}

	private static boolean ensureInitialized() {
		if (initialized) {
			return api != null;
		}
		initialized = true;
		try {
			NativeLibrary library = LIBRARY_PATH.isEmpty()
				? NativeLibrary.getInstance("renderdoc")
				: NativeLibrary.getInstance(LIBRARY_PATH);
			Function getApi = library.getFunction("RENDERDOC_GetAPI");
			PointerByReference apiReference = new PointerByReference();
			int result = getApi.invokeInt(new Object[] {API_VERSION_1_6_0, apiReference});
			api = apiReference.getValue();
			if (result == 0 || api == null || Pointer.nativeValue(api) == 0L) {
				unavailable = true;
				LOGGER.warn("RenderDoc API was not available from {} result={}", library.getName(), result);
				return false;
			}
			if (!PATH_TEMPLATE.isEmpty()) {
				Function setCapturePath = functionAt(FN_SET_CAPTURE_FILE_PATH_TEMPLATE);
				if (setCapturePath != null) {
					setCapturePath.invokeVoid(new Object[] {PATH_TEMPLATE});
				}
			}
			LOGGER.info("RenderDoc API initialized pathTemplate={}", PATH_TEMPLATE);
			return true;
		} catch (Throwable throwable) {
			unavailable = true;
			LOGGER.warn("Unable to initialize RenderDoc API", throwable);
			return false;
		}
	}

	private static Function functionAt(int index) {
		if (api == null) {
			return null;
		}
		Pointer pointer = api.getPointer((long) index * Native.POINTER_SIZE);
		if (pointer == null || Pointer.nativeValue(pointer) == 0L) {
			return null;
		}
		return Function.getFunction(pointer);
	}

	private static Pointer windowPointer(Window window) throws ReflectiveOperationException {
		Field handle = Window.class.getDeclaredField("handle");
		handle.setAccessible(true);
		long value = handle.getLong(window);
		return Pointer.createConstant(value);
	}

	private static Pointer captureWindowPointer(Window window) throws ReflectiveOperationException {
		if ("opengl".equalsIgnoreCase(BACKEND)) {
			return Pointer.NULL;
		}
		return windowPointer(window);
	}
}
