package com.seibel.distanthorizons.core.render.glObject.shader;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.system.MemoryStack;

import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;


/**
 * This object holds the reference to a OpenGL shader program
 * and contains a few methods that can be used with OpenGL shader programs.
 * The reason for many of these simple wrapper methods is as reminders of what
 * can (and needs to be) done with a shader program.
 *
 * @author James Seibel
 * @version 11-26-2021
 */
public class ShaderProgram
{
	/** Stores the handle of the program. */
	public final int id;
	
	
	
	// TODO: A better way to set the fragData output name
	/**
	 * Creates a shader program.
	 * This will bind ShaderProgram
	 */
	public ShaderProgram(String vert, String frag, String fragDataOutputName, String[] attributes)
	{
		this(
				() -> Shader.loadFile(vert, false, new StringBuilder()).toString(),
				() -> Shader.loadFile(frag, false, new StringBuilder()).toString(),
				fragDataOutputName, attributes
		);
	}
	
	public ShaderProgram(Supplier<String> vert, Supplier<String> frag, String fragDataOutputName, String[] attributes)
	{
		this(
				new ArrayList<>(Arrays.asList(vert)),
				new ArrayList<>(Arrays.asList(frag)),
				attributes
		);
	}
	
	
	public ShaderProgram(List<Supplier<String>> vertSupplierList, List<Supplier<String>> fragSupplierList, String[] attributes)
	{
		this.id = VulkanicAPI.createShaderProgram(VulkanicAPI.getImmediateContext());
		
		for (Supplier<String> vertSupplier : vertSupplierList)
		{
			Shader vertShader = new Shader(VulkanicAPI.GL_VERTEX_SHADER, vertSupplier.get());
			VulkanicAPI.attachShader(VulkanicAPI.getImmediateContext(), this.id, vertShader.id);
			vertShader.free(); // important!
		}
		
		for (Supplier<String> fragSupplier : fragSupplierList)
		{
			Shader fragShader = new Shader(VulkanicAPI.GL_FRAGMENT_SHADER, fragSupplier.get());
			VulkanicAPI.attachShader(VulkanicAPI.getImmediateContext(), this.id, fragShader.id);
			fragShader.free(); // important!
		}
		
		for (int i = 0; i < attributes.length; i++)
		{
			VulkanicAPI.setAttributeLocation(VulkanicAPI.getImmediateContext(), this.id, i, attributes[i]);
		}
		VulkanicAPI.glLinkProgram(this.id);
		
		int status = VulkanicAPI.glGetProgrami(this.id, VulkanicAPI.GL_LINK_STATUS);
		if (status != VulkanicAPI.GL_TRUE)
		{
			String message = "Shader Link Error. Details: " + VulkanicAPI.glGetProgramInfoLog(this.id);
			this.free(); // important!
			throw new RuntimeException(message);
		}
		VulkanicAPI.bindShaderProgram(VulkanicAPI.getImmediateContext(), this.id); // This HAVE to be a direct call to prevent calling the overloaded version
	}
	
	
	
	
	public void bind() { VulkanicAPI.bindShaderProgram(VulkanicAPI.getImmediateContext(), this.id); }
	public void unbind() { VulkanicAPI.bindShaderProgram(VulkanicAPI.getImmediateContext(), 0); }
	
	public void free() { VulkanicAPI.glDeleteProgram(this.id); }
	
	
	
	
	/**
	 * WARNING: Slow native call! Cache it if possible!
	 * Gets the location of an attribute variable with specified name.
	 * Calls GL20.glGetAttribLocation(id, name)
	 *
	 * @param name Attribute name
	 * @return Location of the attribute
	 * @throws RuntimeException if attribute not found
	 */
	public int getAttributeLocation(CharSequence name)
	{
		int i = VulkanicAPI.getAttributeLocation(VulkanicAPI.getImmediateContext(), id, name);
		if (i == -1) throw new RuntimeException("Attribute name not found: " + name);
		return i;
	}
	/**
	 * Same as above but without throwing errors. <br>
	 * Returns -1 if the attribute doesn't exist or has been optimized out.
	 */
	public int tryGetAttributeLocation(CharSequence name)
	{ return VulkanicAPI.getAttributeLocation(VulkanicAPI.getImmediateContext(), this.id, name); }
	
	/**
	 * WARNING: Slow native call! Cache it if possible!
	 * Gets the location of a uniform variable with specified name.
	 * Calls GL20.glGetUniformLocation(id, name)
	 *
	 * @param name Uniform name
	 * @return Location of the Uniform
	 * @throws RuntimeException if uniform not found
	 */
	public int getUniformLocation(CharSequence name) throws RuntimeException
	{
		int i = VulkanicAPI.getUniformLocation(VulkanicAPI.getImmediateContext(), id, name);
		if (i == -1)
		{
			throw new RuntimeException("Uniform name not found: " + name);
		}
		return i;
	}
	
	// Same as above but without throwing errors.
	// Return -1 if uniform doesn't exist or has been optimized out
	public int tryGetUniformLocation(CharSequence name)
	{ return VulkanicAPI.getUniformLocation(VulkanicAPI.getImmediateContext(), this.id, name); }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, boolean value) { VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), location, value ? 1 : 0); }
	/** @see ShaderProgram#setUniform(int, boolean) */
	public void trySetUniform(int location, boolean value) { if (location != -1) { this.setUniform(location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, int value) { VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), location, value); }
	/** @see ShaderProgram#setUniform(int, int) */
	public void trySetUniform(int location, int value) { if (location != -1) { this.setUniform(location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, float value) { VulkanicAPI.setUniform1f(VulkanicAPI.getImmediateContext(), location, value); }
	/** @see ShaderProgram#setUniform(int, float) */
	public void trySetUniform(int location, float value) { if (location != -1) { this.setUniform(location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, Vec3f value) { VulkanicAPI.setUniform3f(VulkanicAPI.getImmediateContext(), location, value.x, value.y, value.z); }
	/** @see ShaderProgram#setUniform(int, Vec3f) */
	public void trySetUniform(int location, Vec3f value) { if (location != -1) { this.setUniform(location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, DhApiVec3i value) { VulkanicAPI.setUniform3i(VulkanicAPI.getImmediateContext(), location, value.x, value.y, value.z); }
	/** @see ShaderProgram#setUniform(int, Mat4f) */
	public void trySetUniform(int location, DhApiVec3i value) { if (location != -1) { this.setUniform(location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, Mat4f value)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer buffer = stack.mallocFloat(4 * 4);
			value.store(buffer);
			VulkanicAPI.setUniformMatrix4fv(VulkanicAPI.getImmediateContext(), location, false, buffer);
		}
	}
	/** @see ShaderProgram#setUniform(int, Mat4f) */
	public void trySetUniform(int location, Mat4f value) { if (location != -1) { this.setUniform(location, value); } }
	
	/**
	 * Converts the color's RGBA values into values between 0 and 1. <br>
	 * Requires a bound ShaderProgram.
	 */
	public void setUniform(int location, Color value)
	{
		VulkanicAPI.setUniform4f(VulkanicAPI.getImmediateContext(), location, 
				value.getRed()   / 256.0f, 
				value.getGreen() / 256.0f, 
				value.getBlue()  / 256.0f, 
				value.getAlpha() / 256.0f);
	}
	/** @see ShaderProgram#setUniform(int, Color) */
	public void trySetUniform(int location, Color value) { if (location != -1) { this.setUniform(location, value); } }
	
}
