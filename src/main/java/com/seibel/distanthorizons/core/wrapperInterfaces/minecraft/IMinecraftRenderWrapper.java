package com.seibel.distanthorizons.core.wrapperInterfaces.minecraft;

import java.awt.Color;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains everything related to
 * rendering in Minecraft.
 *
 * @author James Seibel
 * @version 3-5-2022
 */
public interface IMinecraftRenderWrapper extends IBindable
{
	Vec3f getLookAtVector();
	
	boolean playerHasBlindingEffect();
	
	Vec3d getCameraExactPosition();
	
	Color getFogColor(float partialTicks);
	
	/** Unless you really need to know if the player is blind, use {@link IMinecraftRenderWrapper#isFogStateSpecial()} instead */
	boolean isFogStateSpecial();
	
	Color getSkyColor();
	
	double getFov(float partialTicks);
	
	/** Measured in chunks */
	int getRenderDistance();
	
	boolean mcRendersToFrameBuffer();
	boolean runningLegacyOpenGL();
	
	/** @return -1 if no valid framebuffer is available yet */
	int getTargetFramebuffer(); // Note: Iris is now hooking onto this for DH + Iris compat, try not to change (unless we wanna deal with some annoyances)
								//          Iris commit: https://github.com/IrisShaders/Iris/commit/a76a240527e93780bbcba57c09bef377419d47a7#diff-7b9ded0c79bbcdb130010373387756a28ee8d3640d522c0a5b7acd0abbfc20aeR16
	/** @return -1 if no valid depth texture handle is available yet */
	int getDepthTextureId();
	/** @return -1 if no valid color texture handle is available yet */
	int getColorTextureId();
	int getTargetFramebufferViewportWidth();
	int getTargetFramebufferViewportHeight();
	
	/** 
	 * generally shouldn't be needed, the frame buffer should generally stay the same 
	 * but in case something goes wrong this allows for re-getting the buffer ID.
	 */
	void clearTargetFrameBuffer();
	
	/** Can return null if the given level hasn't had a light map assigned to it */
	@Nullable
	ILightMapWrapper getLightmapWrapper(@NotNull ILevelWrapper level);
	
	float getShade(EDhDirection lodDirection);
	
	
	
}
