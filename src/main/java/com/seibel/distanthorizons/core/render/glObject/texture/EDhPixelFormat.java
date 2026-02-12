package com.seibel.distanthorizons.core.render.glObject.texture;

import net.vulkanic.VulkanicAPI;

import java.util.Locale;
import java.util.Optional;

public enum EDhPixelFormat
{
	RED(VulkanicAPI.GL_RED, EGlVersion.GL_11, false),
	RG(VulkanicAPI.GL_RG, EGlVersion.GL_30, false),
	RGB(VulkanicAPI.GL_RGB, EGlVersion.GL_11, false),
	BGR(VulkanicAPI.GL_BGR, EGlVersion.GL_12, false),
	RGBA(VulkanicAPI.GL_RGBA, EGlVersion.GL_11, false),
	BGRA(VulkanicAPI.GL_BGRA, EGlVersion.GL_12, false),
	RED_INTEGER(VulkanicAPI.GL_RED_INTEGER, EGlVersion.GL_30, true),
	RG_INTEGER(VulkanicAPI.GL_RG_INTEGER, EGlVersion.GL_30, true),
	RGB_INTEGER(VulkanicAPI.GL_RGB_INTEGER, EGlVersion.GL_30, true),
	BGR_INTEGER(VulkanicAPI.GL_BGR_INTEGER, EGlVersion.GL_30, true),
	RGBA_INTEGER(VulkanicAPI.GL_RGBA_INTEGER, EGlVersion.GL_30, true),
	BGRA_INTEGER(VulkanicAPI.GL_BGRA_INTEGER, EGlVersion.GL_30, true);
	
	
	
	private final int glFormat;
	private final EGlVersion minimumGlVersion;
	private final boolean isInteger;
	
	
	
	EDhPixelFormat(int glFormat, EGlVersion minimumGlVersion, boolean isInteger)
	{
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.isInteger = isInteger;
	}
	
	
	
	public static Optional<EDhPixelFormat> fromString(String name)
	{
		try
		{
			return Optional.of(EDhPixelFormat.valueOf(name.toUpperCase(Locale.US)));
		}
		catch (IllegalArgumentException e)
		{
			return Optional.empty();
		}
	}
	
	public int getGlFormat() { return glFormat; }
	
	public EGlVersion getMinimumGlVersion() { return minimumGlVersion; }
	
	public boolean isInteger() { return isInteger; }
	
}
