package com.seibel.distanthorizons.core.render.glObject.texture;

import net.vulkanic.VulkanicAPI;
import org.joml.Vector2i;

import java.nio.ByteBuffer;

public class DhColorTexture
{

	private final EDhInternalTextureFormat internalFormat;
	private final EDhPixelFormat format;
	private final EDhPixelType type;
	private int width;
	private int height;

	private boolean isValid;
	/** AKA, the OpenGL name of this texture */
	private final int id;

	private static final ByteBuffer NULL_BUFFER = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhColorTexture(Builder builder)
	{
		this.isValid = true;
		
		this.internalFormat = builder.internalFormat;
		this.format = builder.format;
		this.type = builder.type;
		
		this.width = builder.width;
		this.height = builder.height;
		
		this.id = VulkanicAPI.createTexture2D(VulkanicAPI.getImmediateContext());
		
		boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
		this.setupTexture(this.id, builder.width, builder.height, !isPixelFormatInteger); // this binds the texture
		
		// Clean up after ourselves
		// This is strictly defensive to ensure that other buggy code doesn't tamper with our textures
		VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, 0);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	private void setupTexture(int id, int width, int height, boolean allowsLinear)
	{
		this.resizeTexture(id, width, height);
		
		VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, allowsLinear ? VulkanicAPI.GL_LINEAR : VulkanicAPI.GL_NEAREST);
		VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, allowsLinear ? VulkanicAPI.GL_LINEAR : VulkanicAPI.GL_NEAREST);
		VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);
		VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_CLAMP_TO_EDGE);
		
		// disable mip-mapping since DH is just going to draw straight to the screen
		VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_BASE_LEVEL, 0);
		VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0);
	}
	
	private void resizeTexture(int texture, int width, int height)
	{
		VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, texture);
		VulkanicAPI.uploadTexture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, 0, this.internalFormat.getGlFormat(), width, height, 0, this.format.getGlFormat(), this.type.getGlFormat(), NULL_BUFFER);
	}
	
	void resize(Vector2i textureScaleOverride) { this.resize(textureScaleOverride.x, textureScaleOverride.y); }
	
	// Package private, call CompositeRenderTargets#resizeIfNeeded instead.
	public void resize(int width, int height)
	{
		this.throwIfInvalid();
		
		this.width = width;
		this.height = height;
		
		this.resizeTexture(this.id, width, height);
	}
	
	public EDhInternalTextureFormat getInternalFormat() { return this.internalFormat; }
	
	public int getTextureId()
	{
		this.throwIfInvalid();
		return this.id;
	}
	
	public int getWidth() { return this.width; }
	
	public int getHeight() { return this.height; }
	
	public void destroy()
	{
		this.throwIfInvalid();
		this.isValid = false;
		
		VulkanicAPI.deleteTexture(VulkanicAPI.getImmediateContext(), this.id);
	}
	
	/** @throws IllegalStateException if the texture isn't valid */
	private void throwIfInvalid()
	{
		if (!this.isValid)
		{
			throw new IllegalStateException("Attempted to use a deleted composite render target");
		}
	}
	
	public static Builder builder() { return new Builder(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class Builder
	{
		private EDhInternalTextureFormat internalFormat = EDhInternalTextureFormat.RGBA8;
		private int width = 0;
		private int height = 0;
		private EDhPixelFormat format = EDhPixelFormat.RGBA;
		private EDhPixelType type = EDhPixelType.UNSIGNED_BYTE;
		
		private Builder()
		{
			// No-op
		}
		
		public Builder setInternalFormat(EDhInternalTextureFormat format)
		{
			this.internalFormat = format;
			return this;
		}
		
		public Builder setDimensions(int width, int height)
		{
			if (width <= 0)
			{
				throw new IllegalArgumentException("Width must be greater than zero");
			}
			
			if (height <= 0)
			{
				throw new IllegalArgumentException("Height must be greater than zero");
			}
			
			this.width = width;
			this.height = height;
			
			return this;
		}
		
		public Builder setPixelFormat(EDhPixelFormat pixelFormat)
		{
			this.format = pixelFormat;
			return this;
		}
		
		public Builder setPixelType(EDhPixelType pixelType)
		{
			this.type = pixelType;
			return this;
		}
		
		public DhColorTexture build() { return new DhColorTexture(this); }
		
	}
}
