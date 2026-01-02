package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Path;
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
 */
@Environment(EnvType.CLIENT)
public class PanoramaCapture {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PANORAMA_SIZE = 1024;
	private static final int FACE_COUNT = 6;
	
	// Face ordering matches CubeMapTexture.SUFFIXES: _1, _3, _5, _4, _0, _2
	// which maps to indices: 1, 3, 5, 4, 0, 2
	private static final int[] FACE_INDICES = {1, 3, 5, 4, 0, 2};
	
	// Camera rotations for each face (yaw, pitch)
	// Based on cubemap conventions: right, left, top, bottom, front, back
	private static final float[][] FACE_ROTATIONS = {
		{0.0F, 0.0F},      // panorama_0.png - front
		{90.0F, 0.0F},     // panorama_1.png - right  
		{180.0F, 0.0F},    // panorama_2.png - back
		{270.0F, 0.0F},    // panorama_3.png - left
		{0.0F, -90.0F},    // panorama_4.png - up
		{0.0F, 90.0F}      // panorama_5.png - down
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
	 * Called each frame to process the active capture job.
	 */
	public static void onFrame(Minecraft minecraft) {
		if (activeJob != null) {
			try {
				boolean completed = activeJob.processNextFace(minecraft);
				if (completed) {
					activeJob.cleanup();
					activeJob = null;
				}
			} catch (Exception e) {
				LOGGER.error("Error during panorama capture", e);
				minecraft.gui.getChat().addMessage(Component.literal("Panorama capture failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
				if (activeJob != null) {
					activeJob.cleanup();
					activeJob = null;
				}
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
		private final Minecraft minecraft;
		private final File outputFolder;
		private final RenderTarget renderTarget;
		
		// Saved state
		private final float savedYaw;
		private final float savedPitch;
		private final float savedFov;
		private final boolean savedHideGui;
		
		private int currentFace = 0;
		
		PanoramaJob(Minecraft minecraft) throws Exception {
			this.minecraft = minecraft;
			
			// Save current state
			this.savedYaw = minecraft.player.getYRot();
			this.savedPitch = minecraft.player.getXRot();
			this.savedFov = minecraft.options.fov().get();
			this.savedHideGui = minecraft.options.hideGui;
			
			// Create timestamped output folder
			File screenshotsDir = new File(minecraft.gameDirectory, Screenshot.SCREENSHOT_DIR);
			screenshotsDir.mkdirs();
			
			String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
			this.outputFolder = new File(screenshotsDir, timestamp);
			
			if (!this.outputFolder.exists() && !this.outputFolder.mkdirs()) {
				throw new Exception("Failed to create output folder: " + this.outputFolder);
			}
			
			// Create offscreen render target
			this.renderTarget = new TextureTarget("Panorama Capture", PANORAMA_SIZE, PANORAMA_SIZE, true);
			
			// Set FOV to 90 for capture
			minecraft.options.fov().set(90);
			
			// Hide GUI
			minecraft.options.hideGui = true;
			
			LOGGER.info("Panorama capture initialized: output={}", this.outputFolder.getAbsolutePath());
		}
		
		/**
		 * Process the next face. Returns true when all faces are complete.
		 */
		boolean processNextFace(Minecraft minecraft) {
			if (currentFace >= FACE_COUNT) {
				return true;
			}
			
			try {
				// Show progress
				minecraft.gui.getChat().addMessage(
					Component.literal("Capturing panorama... (" + (currentFace + 1) + "/" + FACE_COUNT + ")")
						.withStyle(ChatFormatting.YELLOW)
				);
				
				// Get the face index for proper ordering
				int faceIndex = FACE_INDICES[currentFace];
				float[] rotation = FACE_ROTATIONS[faceIndex];
				
				// Set camera rotation
				minecraft.player.setYRot(savedYaw + rotation[0]);
				minecraft.player.setXRot(rotation[1]);
				minecraft.player.setYHeadRot(savedYaw + rotation[0]);
				
				// Render to offscreen target
				renderFace(minecraft);
				
				// Read pixels and save
				Screenshot.takeScreenshot(renderTarget, nativeImage -> {
					File outputFile = new File(outputFolder, "panorama_" + faceIndex + ".png");
					try {
						nativeImage.writeToFile(outputFile);
						LOGGER.info("Saved panorama face {}: {}", faceIndex, outputFile.getName());
					} catch (Exception e) {
						LOGGER.error("Failed to save panorama face " + faceIndex, e);
						throw new RuntimeException(e);
					} finally {
						nativeImage.close();
					}
				});
				
				currentFace++;
				
				// Check if complete
				if (currentFace >= FACE_COUNT) {
					Component folderComponent = Component.literal(outputFolder.getName())
						.withStyle(ChatFormatting.UNDERLINE)
						.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(outputFolder.getAbsoluteFile())));
					
					minecraft.gui.getChat().addMessage(
						Component.translatable("screenshot.success",
							Component.literal("Saved panorama to: ").append(folderComponent))
							.withStyle(ChatFormatting.GREEN)
					);
					return true;
				}
				
				return false;
			} catch (Exception e) {
				throw new RuntimeException("Failed to process face " + currentFace, e);
			}
		}
		
		/**
		 * Renders the current view to the offscreen render target.
		 */
		private void renderFace(Minecraft minecraft) {
			// Store main render target
			RenderTarget mainTarget = minecraft.getMainRenderTarget();
			
			try {
				// Bind offscreen target
				renderTarget.bindWrite(true);
				
				// Render the world
				GameRenderer gameRenderer = minecraft.gameRenderer;
				gameRenderer.renderLevel(minecraft.getTimer().getGameTimeDeltaTicks());
				
				// Restore main target
				mainTarget.bindWrite(true);
			} catch (Exception e) {
				mainTarget.bindWrite(true);
				throw e;
			}
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
				minecraft.options.fov().set((int)savedFov);
				minecraft.options.hideGui = savedHideGui;
				
				// Clean up render target
				if (renderTarget != null) {
					renderTarget.destroyBuffers();
				}
				
				LOGGER.info("Panorama capture cleanup complete");
			} catch (Exception e) {
				LOGGER.error("Error during panorama capture cleanup", e);
			}
		}
	}
}
