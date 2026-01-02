package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Manages panorama capture for creating 6-face cubemaps.
 * Captures one face per frame to minimize hitching.
 * 
 * Uses temporary render target resizing: resizes main target to exactly 1024x1024,
 * calls gameRenderer.resize() for proper shader reinitialization, captures, then restores.
 * This approach guarantees exact dimensions while maintaining shader compatibility.
 */
@Environment(EnvType.CLIENT)
public class PanoramaCapture {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PANORAMA_SIZE = 1024;
	private static final int FACE_COUNT = 6;
	
	// Face ordering matches CubeMapTexture.SUFFIXES: _1, _3, _5, _4, _0, _2
	// Indices in order: 1, 3, 5, 4, 0, 2
	// Corresponds to: right, left, up, down, front, back
	private static final int[] FACE_INDICES = {1, 3, 5, 4, 0, 2};
	
	// Camera rotations for each face index (yaw offset from saved, pitch absolute)
	// panorama_0 = front, panorama_1 = right, panorama_2 = back, panorama_3 = left, panorama_4 = up, panorama_5 = down
	private static final float[][] FACE_ROTATIONS = {
		{0.0F, 0.0F},      // panorama_0.png - front (yaw+0, pitch=0)
		{90.0F, 0.0F},     // panorama_1.png - right (yaw+90, pitch=0)
		{180.0F, 0.0F},    // panorama_2.png - back (yaw+180, pitch=0)
		{270.0F, 0.0F},    // panorama_3.png - left (yaw+270, pitch=0)
		{0.0F, -90.0F},    // panorama_4.png - up (yaw+0, pitch=-90)
		{0.0F, 90.0F}      // panorama_5.png - down (yaw+0, pitch=+90)
	};
	
	@Nullable
	private static PanoramaJob activeJob = null;
	
	/**
	 * Starts a new panorama capture.
	 */
	public static void start(Minecraft minecraft) {
		if (activeJob != null) {
			minecraft.gui.getChat().addMessage(Component.literal("Panorama capture already in progress!").withStyle(ChatFormatting.RED));
			return;
		}
		
		if (minecraft.level == null || minecraft.player == null) {
			minecraft.gui.getChat().addMessage(Component.literal("Cannot capture panorama: not in a world").withStyle(ChatFormatting.RED));
			return;
		}
		
		try {
			activeJob = new PanoramaJob(minecraft);
			LOGGER.info("Started panorama capture");
		} catch (Exception e) {
			LOGGER.error("Failed to start panorama capture", e);
			minecraft.gui.getChat().addMessage(Component.literal("Failed to start panorama capture: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
	}
	
	/**
	 * Called before each frame renders. Prepares camera for panorama capture.
	 */
	public static void beforeRender(Minecraft minecraft) {
		if (activeJob == null) {
			return;
		}
		
		try {
			activeJob.prepareNextFace(minecraft);
		} catch (Exception e) {
			LOGGER.error("Error preparing panorama capture frame", e);
			minecraft.gui.getChat().addMessage(Component.literal("Panorama capture failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
			if (activeJob != null) {
				activeJob.cleanup();
				activeJob = null;
			}
		}
	}
	
	/**
	 * Called after rendering finishes. Saves the captured frame if panorama capture is active.
	 */
	public static void afterRender(Minecraft minecraft) {
		if (activeJob == null) {
			return;
		}
		
		try {
			boolean completed = activeJob.saveCurrentFace(minecraft);
			if (completed) {
				activeJob.cleanup();
				activeJob = null;
			}
		} catch (Exception e) {
			LOGGER.error("Error saving panorama capture frame", e);
			minecraft.gui.getChat().addMessage(Component.literal("Panorama capture failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
			if (activeJob != null) {
				activeJob.cleanup();
				activeJob = null;
			}
		}
	}
	
	/**
	 * Returns true if a capture is currently in progress.
	 */
	public static boolean isCapturing() {
		return activeJob != null;
	}
	
	/**
	 * State machine for capturing panorama one face per frame.
	 */
	private static class PanoramaJob {
		private static final int DELAY_FRAMES = 10; // ~0.5 seconds at 20 FPS (half a second delay)
		
		private final Minecraft minecraft;
		private final File outputFolder;
		
		// Saved state
		private final float savedYaw;
		private final float savedPitch;
		private final int savedFov;
		private final boolean savedHideGui;
		private final int savedWidth;
		private final int savedHeight;
		
		private int currentFace = 0;
		private boolean faceReady = false;
		private int delayCounter = 0; // Counts frames to delay before capturing
		
		PanoramaJob(Minecraft minecraft) throws Exception {
			this.minecraft = minecraft;
			
			// Save current state
			this.savedYaw = minecraft.player.getYRot();
			this.savedPitch = minecraft.player.getXRot();
			this.savedFov = minecraft.options.fov().get();
			this.savedHideGui = minecraft.options.hideGui;
			
			// Save current render target size
			RenderTarget mainTarget = minecraft.getMainRenderTarget();
			this.savedWidth = mainTarget.width;
			this.savedHeight = mainTarget.height;
			
			// Create timestamped output folder
			File screenshotsDir = new File(minecraft.gameDirectory, Screenshot.SCREENSHOT_DIR);
			screenshotsDir.mkdirs();
			
			String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
			this.outputFolder = new File(screenshotsDir, timestamp);
			
			if (!this.outputFolder.exists() && !this.outputFolder.mkdirs()) {
				throw new Exception("Failed to create output folder: " + this.outputFolder);
			}
			
			// NOTE: We don't set FOV/hide GUI here anymore - we do it fresh before each face
			// This ensures each face starts from a clean state like the first one
			
			LOGGER.info("Panorama capture initialized: output={}, size={}x{}", 
				this.outputFolder.getAbsolutePath(), PANORAMA_SIZE, PANORAMA_SIZE);
		}
		
		/**
		 * Prepares for the next face capture. Sets camera and displays progress.
		 * Uses /tp command to set rotation while game is in normal rendering mode.
		 * Resizes window to 1024x1024 and back before each capture to refresh shader state.
		 */
		void prepareNextFace(Minecraft minecraft) {
			if (currentFace >= FACE_COUNT) {
				return;
			}
			
			// If we haven't set up the camera yet for this face, do it now
			if (!faceReady && delayCounter == 0) {
				// Show progress
				minecraft.gui.getChat().addMessage(
					Component.literal("Capturing panorama... (" + (currentFace + 1) + "/" + FACE_COUNT + ")")
						.withStyle(ChatFormatting.YELLOW)
				);
				
				// CRITICAL: Resize window to 1024x1024 and back to refresh shader state
				// This ensures rendering pipeline is properly initialized for each face
				// Window resizing triggered shader rebuilds that made captures work correctly
				minecraft.getWindow().setWindowed(PANORAMA_SIZE, PANORAMA_SIZE);
				// Let it settle for a frame, then restore
				minecraft.getWindow().setWindowed(savedWidth, savedHeight);
				
				// Get the face index for proper ordering
				int faceIndex = FACE_INDICES[currentFace];
				float[] rotation = FACE_ROTATIONS[faceIndex];
				
				// Calculate target yaw and pitch
				float targetYaw = savedYaw + rotation[0];
				float targetPitch = rotation[1];
				
				// Use /tp command to set rotation BEFORE changing render state
				// This ensures shaders see a proper camera state transition
				// Format: /tp @s ~ ~ ~ <yaw> <pitch>
				String tpCommand = String.format(java.util.Locale.US, "tp @s ~ ~ ~ %.2f %.2f", targetYaw, targetPitch);
				
				// Execute the teleport command
				if (minecraft.getConnection() != null) {
					minecraft.getConnection().sendCommand(tpCommand);
				}
				
				// Set FOV to 90 for capture
				minecraft.options.fov().set(90);
				
				// Hide GUI
				minecraft.options.hideGui = true;
				
				// Resize render target to exactly 1024x1024 for this frame
				RenderTarget mainTarget = minecraft.getMainRenderTarget();
				mainTarget.resize(PANORAMA_SIZE, PANORAMA_SIZE);
				
				// CRITICAL: Call gameRenderer.resize() to properly reinitialize shaders
				// This triggers the shader pipeline to rebuild FBOs and depth buffers for the new size
				minecraft.gameRenderer.resize(PANORAMA_SIZE, PANORAMA_SIZE);
				
				// Mark ready to capture on next frame and start delay
				faceReady = true;
				delayCounter = 1;
			}
			
			// If delay is active, increment counter
			if (delayCounter > 0) {
				delayCounter++;
			}
		}
		
		/**
		 * Saves the currently captured face. Returns true if all faces are complete.
		 */
		boolean saveCurrentFace(Minecraft minecraft) {
			if (!faceReady) {
				return currentFace >= FACE_COUNT;
			}
			
			// Wait for delay to complete before capturing
			if (delayCounter < DELAY_FRAMES) {
				return false;
			}
			
			// Reset for next face
			faceReady = false;
			delayCounter = 0;
			
			// Get the face index for proper file naming
			int faceIndex = FACE_INDICES[currentFace];
			
			// Save the captured frame from the main render target (now 1024x1024)
			Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), nativeImage -> {
				File outputFile = new File(outputFolder, "panorama_" + faceIndex + ".png");
				Util.ioPool().execute(() -> {
					try {
						nativeImage.writeToFile(outputFile);
						LOGGER.info("Saved panorama face {} to {}", faceIndex, outputFile.getAbsolutePath());
					} catch (Exception e) {
						LOGGER.error("Failed to save panorama face {} to {}: {}", faceIndex, outputFile.getAbsolutePath(), e.getMessage(), e);
					} finally {
						nativeImage.close();
					}
				});
			});
			
			// CRITICAL: After capturing, restore state completely (like first screenshot worked)
			// Then we'll set up fresh for the next face
			restoreState(minecraft);
			
			currentFace++;
			
			// Check if complete
			if (currentFace >= FACE_COUNT) {
				Component folderComponent = Component.literal(outputFolder.getName())
					.withStyle(ChatFormatting.UNDERLINE)
					.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(outputFolder.getAbsoluteFile())));
				
				minecraft.gui.getChat().addMessage(
					Component.literal("Saved panorama to: ").append(folderComponent)
						.withStyle(ChatFormatting.GREEN)
				);
				return true;
			}
			
			return false;
		}
		
		/**
		 * Restore state to normal (called after each face capture).
		 * Uses /tp command to restore rotation properly.
		 */
		private void restoreState(Minecraft minecraft) {
			// Restore camera using /tp command (proper state transition for shaders)
			// Format: /tp @s ~ ~ ~ <yaw> <pitch>
			String tpCommand = String.format(java.util.Locale.US, "tp @s ~ ~ ~ %.2f %.2f", savedYaw, savedPitch);
			if (minecraft.getConnection() != null) {
				minecraft.getConnection().sendCommand(tpCommand);
			}
			
			// Restore FOV
			minecraft.options.fov().set(savedFov);
			
			// Restore GUI visibility
			minecraft.options.hideGui = savedHideGui;
			
			// Restore render target size
			RenderTarget mainTarget = minecraft.getMainRenderTarget();
			mainTarget.resize(savedWidth, savedHeight);
			
			// CRITICAL: Call gameRenderer.resize() to force shader pipeline rebuild
			// This ensures shaders rebuild their internal FBO graph for the restored size
			// Critical for shader compatibility - each resize triggers full pipeline reinitialization
			minecraft.gameRenderer.resize(savedWidth, savedHeight);
		}
		
		/**
		 * Clean up resources and restore state (called at the very end).
		 */
		void cleanup() {
			try {
				// Final restore - make sure we're back to normal
				restoreState(minecraft);
				
				LOGGER.info("Panorama capture cleanup complete");
			} catch (Exception e) {
				LOGGER.error("Error during panorama capture cleanup", e);
			}
		}
	}
}
