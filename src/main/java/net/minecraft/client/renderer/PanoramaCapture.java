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
 * Integration: Temporarily resizes the main render target to PANORAMA_SIZE during capture,
 * allowing normal rendering to produce panorama images at the correct resolution.
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
	 * Called before each frame renders. Prepares camera and render target for panorama capture.
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
	 * Returns true if we should clear the render target again after beforeRender resized it.
	 */
	public static boolean needsClear() {
		return activeJob != null && activeJob.justResized;
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
		private boolean justResized = false; // Tracks if we just resized (needs clear)
		
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
			
			// Set FOV to 90 for capture
			minecraft.options.fov().set(90);
			
			// Hide GUI
			minecraft.options.hideGui = true;
			
			LOGGER.info("Panorama capture initialized: output={}", this.outputFolder.getAbsolutePath());
		}
		
		/**
		 * Prepares for the next face capture. Resizes main render target, sets camera,
		 * and displays progress message to the user.
		 */
		void prepareNextFace(Minecraft minecraft) {
			if (currentFace >= FACE_COUNT) {
				justResized = false;
				return;
			}
			
			// If we haven't set up the camera yet for this face, do it now
			if (!faceReady && delayCounter == 0) {
				// Show progress
				minecraft.gui.getChat().addMessage(
					Component.literal("Capturing panorama... (" + (currentFace + 1) + "/" + FACE_COUNT + ")")
						.withStyle(ChatFormatting.YELLOW)
				);
				
				// Get the face index for proper ordering
				int faceIndex = FACE_INDICES[currentFace];
				float[] rotation = FACE_ROTATIONS[faceIndex];
				
				// Set camera rotation for this face
				minecraft.player.setYRot(savedYaw + rotation[0]);
				minecraft.player.setXRot(rotation[1]);
				minecraft.player.setYHeadRot(savedYaw + rotation[0]);
				
				// Resize main render target to panorama size
				RenderTarget mainTarget = minecraft.getMainRenderTarget();
				mainTarget.resize(PANORAMA_SIZE, PANORAMA_SIZE);
				
				// Notify GameRenderer and LevelRenderer about the resize to clear internal state
				minecraft.gameRenderer.resize(PANORAMA_SIZE, PANORAMA_SIZE);
				
				// Mark that we just resized so the render target needs to be cleared
				justResized = true;
				
				// Start delay counter
				delayCounter = 1;
			}
			// If we're in the delay period, increment counter
			else if (delayCounter > 0 && delayCounter < DELAY_FRAMES) {
				delayCounter++;
				justResized = false; // Clear flag after first frame
			}
			// If delay is complete, mark ready to capture
			else if (delayCounter >= DELAY_FRAMES) {
				faceReady = true;
				delayCounter = 0; // Reset for next face
				justResized = false;
			} else {
				justResized = false;
			}
		}
		
		/**
		 * Saves the currently captured face. Returns true if all faces are complete.
		 */
		boolean saveCurrentFace(Minecraft minecraft) {
			if (!faceReady) {
				return currentFace >= FACE_COUNT;
			}
			
			faceReady = false;
			
			// Get the face index for proper file naming
			int faceIndex = FACE_INDICES[currentFace];
			
			// Save the captured frame from the main render target
			RenderTarget mainTarget = minecraft.getMainRenderTarget();
			Screenshot.takeScreenshot(mainTarget, nativeImage -> {
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
		 * Clean up resources and restore state.
		 */
		void cleanup() {
			try {
				// Restore saved state
				minecraft.player.setYRot(savedYaw);
				minecraft.player.setXRot(savedPitch);
				minecraft.player.setYHeadRot(savedYaw);
				minecraft.options.fov().set(savedFov);
				minecraft.options.hideGui = savedHideGui;
				
				// Restore original render target size
				RenderTarget mainTarget = minecraft.getMainRenderTarget();
				mainTarget.resize(savedWidth, savedHeight);
				
				// Notify GameRenderer and LevelRenderer about the resize to clear internal state
				minecraft.gameRenderer.resize(savedWidth, savedHeight);
				
				LOGGER.info("Panorama capture cleanup complete");
			} catch (Exception e) {
				LOGGER.error("Error during panorama capture cleanup", e);
			}
		}
	}
}
