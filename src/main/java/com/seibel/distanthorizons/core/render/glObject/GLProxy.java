package com.seibel.distanthorizons.core.render.glObject;

import com.seibel.distanthorizons.api.enums.config.EDhApiGLErrorHandlingMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.EPlatform;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.objects.GLMessages.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.glfw.GLFW;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A singleton that holds references to different openGL contexts
 * and GPU capabilities.
 */
public class GLProxy
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererGLEventToFile)
			.chatLevelConfig(Config.Common.Logging.logRendererGLEventToChat)
			.build();
	
	public static final Set<String> LOGGED_GL_MESSAGES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	
	private static final ConcurrentLinkedQueue<Runnable> RENDER_THREAD_RUNNABLE_QUEUE = new ConcurrentLinkedQueue<>();
	private static final long DEFAULT_RENDER_THREAD_TASK_BUDGET_NANOS = 4_000_000L;
	private static final long VULKAN_LOD_UPLOAD_TASK_BUDGET_NANOS = 32_000_000L;
	private static final int VULKAN_LOD_UPLOAD_MIN_TASKS = 16;
	private static final String DH_UPLOAD_DRAIN_DIAGNOSTICS_PROPERTY = "mattmc.dhUploadDrainDiagnostics";
	private static final long DH_UPLOAD_DRAIN_DIAGNOSTIC_LOG_INTERVAL_FRAMES = 120L;
	private static final AtomicLong DRAIN_FRAMES = new AtomicLong();
	private static final AtomicLong DRAIN_TASKS_PROCESSED = new AtomicLong();
	private static final AtomicLong DRAIN_ELAPSED_NANOS = new AtomicLong();
	private static final AtomicLong DRAIN_TIME_LIMIT_FRAMES = new AtomicLong();
	private static final AtomicLong DRAIN_MINIMUM_TASK_FRAMES = new AtomicLong();
	private static final AtomicLong DRAIN_MAX_BACKLOG = new AtomicLong();
	
	
	
	private static GLProxy instance = null;
	
	
	/** Minecraft's GL capabilities (platform-specific, cast to GLCapabilities for OpenGL backend) */
	public final Object glCapabilities;
	
	public boolean namedObjectSupported = false; // ~OpenGL 4.5 (UNUSED CURRENTLY)
	public boolean bufferStorageSupported = false; // ~OpenGL 4.4
	public boolean vertexAttributeBufferBindingSupported = false; // ~OpenGL 4.3
	public boolean instancedArraysSupported = false;
	public boolean vertexAttribDivisorSupported = false; // OpenGL 3.3 or newer
	
	private final EDhApiGpuUploadMethod preferredUploadMethod;
	
	public final GLMessageBuilder vanillaDebugMessageBuilder = 
		new GLMessageBuilder(
			(type) ->
			{
				if (type == EGLMessageType.POP_GROUP)
					return false;
				else if (type == EGLMessageType.PUSH_GROUP)
					return false;
				else if (type == EGLMessageType.MARKER)
					return false;
				else
					return true;
			},
			(severity) ->
			{
				// notifications can generally be ignored (if they are logged at all)
				if (severity == EGLMessageSeverity.NOTIFICATION)
					return false;
				else
					return true;
			},
			null
	);
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private GLProxy() throws IllegalStateException
	{
		
		LOGGER.info("Creating " + GLProxy.class.getSimpleName() + "... If this is the last message you see there must have been an OpenGL error.");
		LOGGER.info("Lod Render Vulkan compatibility backend [" + VulkanicAPI.getActiveBackendType() + "].");
		
		
		
		
		//============================//
		// get Minecraft's GL context //
		//============================//
		
		// A selected Rust Vulkan route has no Java OpenGL capability object to
		// borrow. DH's compatibility wrappers use the explicit backend flags
		// below instead; only the OpenGL backend receives Minecraft's GL object.
		this.glCapabilities = null;
		
		LOGGER.info("minecraftGlCapabilities: Vulkan compatibility mode; OpenGL capability floor checks skipped.");
			
		
		
		
		//======================//
		// get GPU capabilities //
		//======================//
		
		// These are OpenGL function-pointer probes. Never perform them while
		// Rust owns selected Vulkan; compatibility capabilities remain unavailable.
		this.namedObjectSupported = false; //Nullptr
		this.bufferStorageSupported = false; // Nullptr
		if (!this.bufferStorageSupported)
		{
			LOGGER.info("This GPU doesn't support Buffer Storage (OpenGL 4.4), falling back to using other methods.");
		}
		
		// Check if we can use the make-over version of Vertex Attribute, which is available in GL4.3 or after
		this.vertexAttributeBufferBindingSupported = false; // Nullptr
		
		this.vertexAttribDivisorSupported = true;
		this.instancedArraysSupported = true;
		
		// get the best automatic upload method
		String vendor;
		vendor = "VULKANIC";
		this.preferredUploadMethod = EDhApiGpuUploadMethod.DATA;
		LOGGER.info("GPU Vendor [" + vendor + "] with OS [" + EPlatform.get().getName() + "], Preferred upload method is [" + this.preferredUploadMethod + "].");
		
		
		
		//==========//
		// clean up //
		//==========//
		
		// GLProxy creation success
		LOGGER.info(GLProxy.class.getSimpleName() + " creation successful. OpenGL smiles upon you this day.");
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public static boolean hasInstance() { return instance != null; }
	/** @throws IllegalStateException if the Proxy hasn't been created yet and this is called outside the render thread */
	public static GLProxy getInstance() throws IllegalStateException
	{
		if (instance == null)
		{
			instance = new GLProxy();
		}
		
		return instance;
	}
	
	
	
	
	
	//=========================//
	// Worker Thread Runnables //
	//=========================//
	
	public static void queueRunningOnRenderThread(Runnable renderCall)
	{
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		RENDER_THREAD_RUNNABLE_QUEUE.add(() -> runOpenGlCall(renderCall, stackTrace));
	}
	private static void runOpenGlCall(Runnable renderCall, StackTraceElement[] stackTrace)
	{
		try
		{
			renderCall.run();
		}
		catch (Exception e)
		{
			RuntimeException error = new RuntimeException("Uncaught Exception during GL call execution:", e);
			error.setStackTrace(stackTrace);
			LOGGER.error("[" + Thread.currentThread().getName() + "] ran into an unexpected error running a GL call, Error: ["+ e.getMessage() +"].", error);
		}
	}
	

	static record DrainStats(
		long frames,
		long tasksProcessed,
		long elapsedNanos,
		long framesHittingTimeLimit,
		long framesUsingMinimumTaskFloor,
		long maxObservedBacklog,
		int queuedAfter
	) {
	}
	
	
	
	//=========//
	// logging //
	//=========//
	
	/** this method is called on the render thread at the point of the GL Error */
	private static void logMessage(GLMessage msg)
	{
		EDhApiGLErrorHandlingMode errorHandlingMode = Config.Client.Advanced.Debugging.OpenGl.glErrorHandlingMode.get();
		if (errorHandlingMode == EDhApiGLErrorHandlingMode.IGNORE)
		{
			return;
		}
		
		
		
		boolean onlyLogOnce = Config.Client.Advanced.Debugging.OpenGl.onlyLogGlErrorsOnce.get();
		String errorMessage = "GL ERROR [" + msg.id + "] from [" + msg.source + "]: [" + msg.message + "]"+(onlyLogOnce ? " this message will only be logged once" : "")+".";
		if (onlyLogOnce
			&& !LOGGED_GL_MESSAGES.add(errorMessage))
		{
			// this message has already been logged
			return;
		}
		
		
		// create an exception so we get a stacktrace of where the message was triggered from
		RuntimeException exception = new RuntimeException(errorMessage);
		
		if (msg.type == EGLMessageType.ERROR || msg.type == EGLMessageType.UNDEFINED_BEHAVIOR)
		{
			// critical error
			
			LOGGER.error(exception.getMessage(), exception);
			
			if (errorHandlingMode == EDhApiGLErrorHandlingMode.LOG_THROW)
			{
				// will probably crash the game,
				// good for quickly checking if there's a problem while preventing log spam
				throw exception;
			}
		}
		else
		{
			// non-critical log
			
			EGLMessageSeverity severity = msg.severity;
			if (severity == null)
			{
				// just in case the message was malformed
				severity = EGLMessageSeverity.LOW;
			}
			
			switch (severity)
			{
				case HIGH:
					LOGGER.error(exception.getMessage(), exception);
					break;
				case MEDIUM:
					LOGGER.warn(exception.getMessage(), exception);
					break;
				case LOW:
					LOGGER.info(exception.getMessage(), exception);
					break;
				case NOTIFICATION:
					LOGGER.debug(exception.getMessage(), exception);
					break;
			}
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	
	
}
