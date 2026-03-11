package com.seibel.distanthorizons.core.render.glObject.shader;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.system.MemoryStack;

import com.seibel.distanthorizons.core.util.math.Mat4f;
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
		this(VulkanicAPI.getCommandContext(), vert, frag, fragDataOutputName, attributes);
	}

	public ShaderProgram(CommandContext ctx, String vert, String frag, String fragDataOutputName, String[] attributes)
	{
		this(
				ctx,
				() -> Shader.loadFile(vert, false, new StringBuilder()).toString(),
				() -> Shader.loadFile(frag, false, new StringBuilder()).toString(),
				fragDataOutputName, attributes
		);
	}
	
	public ShaderProgram(Supplier<String> vert, Supplier<String> frag, String fragDataOutputName, String[] attributes)
	{
		this(VulkanicAPI.getCommandContext(), vert, frag, fragDataOutputName, attributes);
	}

	public ShaderProgram(CommandContext ctx, Supplier<String> vert, Supplier<String> frag, String fragDataOutputName, String[] attributes)
	{
		this(
				ctx,
				new ArrayList<>(Arrays.asList(vert)),
				new ArrayList<>(Arrays.asList(frag)),
				attributes
		);
	}
	
	
	public ShaderProgram(List<Supplier<String>> vertSupplierList, List<Supplier<String>> fragSupplierList, String[] attributes)
	{
		this(VulkanicAPI.getCommandContext(), vertSupplierList, fragSupplierList, attributes);
	}

	public ShaderProgram(CommandContext ctx, List<Supplier<String>> vertSupplierList, List<Supplier<String>> fragSupplierList, String[] attributes)
	{
		this.id = VulkanicAPI.createShaderProgram(ctx);
		
		for (Supplier<String> vertSupplier : vertSupplierList)
		{
			Shader vertShader = new Shader(ctx, VulkanicAPI.GL_VERTEX_SHADER, vertSupplier.get());
			VulkanicAPI.attachShader(ctx, this.id, vertShader.id);
			vertShader.free(ctx); // important!
		}
		
		for (Supplier<String> fragSupplier : fragSupplierList)
		{
			Shader fragShader = new Shader(ctx, VulkanicAPI.GL_FRAGMENT_SHADER, fragSupplier.get());
			VulkanicAPI.attachShader(ctx, this.id, fragShader.id);
			fragShader.free(ctx); // important!
		}
		
		for (int i = 0; i < attributes.length; i++)
		{
			VulkanicAPI.setAttributeLocation(ctx, this.id, i, attributes[i]);
		}
		VulkanicAPI.linkProgram(ctx, this.id);
		
		int status = VulkanicAPI.getProgramParameter(ctx, this.id, net.vulkanic.VulkanicProgramParameterName.LINK_STATUS);
		if (status != VulkanicAPI.GL_TRUE)
		{
			String message = "Shader Link Error. Details: " + VulkanicAPI.getProgramInfoLog(ctx, this.id);
			this.free(ctx); // important!
			throw new RuntimeException(message);
		}
		VulkanicAPI.bindShaderProgram(ctx, this.id); // This HAVE to be a direct call to prevent calling the overloaded version
	}
	
	
	
	
	public void bind() { this.bind(VulkanicAPI.getCommandContext()); }
	public void bind(CommandContext ctx) { VulkanicAPI.bindShaderProgram(ctx, this.id); }
	public void unbind() { this.unbind(VulkanicAPI.getCommandContext()); }
	public void unbind(CommandContext ctx) { VulkanicAPI.bindShaderProgram(ctx, 0); }
	
	public void free() { this.free(VulkanicAPI.getCommandContext()); }
	public void free(CommandContext ctx) { VulkanicAPI.deleteProgram(ctx, this.id); }
	
	
	
	
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
	{ return this.getAttributeLocation(VulkanicAPI.getCommandContext(), name); }

	public int getAttributeLocation(CommandContext ctx, CharSequence name)
	{
		int i = VulkanicAPI.getAttributeLocation(ctx, id, name);
		if (i == -1) throw new RuntimeException("Attribute name not found: " + name);
		return i;
	}
	/**
	 * Same as above but without throwing errors. <br>
	 * Returns -1 if the attribute doesn't exist or has been optimized out.
	 */
	public int tryGetAttributeLocation(CharSequence name)
	{ return this.tryGetAttributeLocation(VulkanicAPI.getCommandContext(), name); }

	public int tryGetAttributeLocation(CommandContext ctx, CharSequence name)
	{ return VulkanicAPI.getAttributeLocation(ctx, this.id, name); }
	
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
	{ return this.getUniformLocation(VulkanicAPI.getCommandContext(), name); }

	public int getUniformLocation(CommandContext ctx, CharSequence name) throws RuntimeException
	{
		int i = VulkanicAPI.getUniformLocation(ctx, id, name);
		if (i == -1)
		{
			throw new RuntimeException("Uniform name not found: " + name);
		}
		return i;
	}
	
	// Same as above but without throwing errors.
	// Return -1 if uniform doesn't exist or has been optimized out
	public int tryGetUniformLocation(CharSequence name)
	{ return this.tryGetUniformLocation(VulkanicAPI.getCommandContext(), name); }

	public int tryGetUniformLocation(CommandContext ctx, CharSequence name)
	{ return VulkanicAPI.getUniformLocation(ctx, this.id, name); }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, boolean value) { this.setUniform(VulkanicAPI.getCommandContext(), location, value); }
	public void setUniform(CommandContext ctx, int location, boolean value) { VulkanicAPI.setUniform1i(ctx, location, value ? 1 : 0); }
	/** @see ShaderProgram#setUniform(int, boolean) */
	public void trySetUniform(int location, boolean value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, boolean value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, int value) { this.setUniform(VulkanicAPI.getCommandContext(), location, value); }
	public void setUniform(CommandContext ctx, int location, int value) { VulkanicAPI.setUniform1i(ctx, location, value); }
	/** @see ShaderProgram#setUniform(int, int) */
	public void trySetUniform(int location, int value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, int value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, float value) { this.setUniform(VulkanicAPI.getCommandContext(), location, value); }
	public void setUniform(CommandContext ctx, int location, float value) { VulkanicAPI.setUniform1f(ctx, location, value); }
	/** @see ShaderProgram#setUniform(int, float) */
	public void trySetUniform(int location, float value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, float value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, Vec3f value) { this.setUniform(VulkanicAPI.getCommandContext(), location, value); }
	public void setUniform(CommandContext ctx, int location, Vec3f value) { VulkanicAPI.setUniform3f(ctx, location, value.x, value.y, value.z); }
	/** @see ShaderProgram#setUniform(int, Vec3f) */
	public void trySetUniform(int location, Vec3f value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, Vec3f value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, DhApiVec3i value) { this.setUniform(VulkanicAPI.getCommandContext(), location, value); }
	public void setUniform(CommandContext ctx, int location, DhApiVec3i value) { VulkanicAPI.setUniform3i(ctx, location, value.x, value.y, value.z); }
	/** @see ShaderProgram#setUniform(int, Mat4f) */
	public void trySetUniform(int location, DhApiVec3i value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, DhApiVec3i value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
	/** Requires a bound ShaderProgram. */
	public void setUniform(int location, Mat4f value)
	{ this.setUniform(VulkanicAPI.getCommandContext(), location, value); }

	public void setUniform(CommandContext ctx, int location, Mat4f value)
	{
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer buffer = stack.mallocFloat(4 * 4);
			value.store(buffer);
			VulkanicAPI.setUniformMatrix4fv(ctx, location, false, buffer);
		}
	}
	/** @see ShaderProgram#setUniform(int, Mat4f) */
	public void trySetUniform(int location, Mat4f value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, Mat4f value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
	/**
	 * Converts the color's RGBA values into values between 0 and 1. <br>
	 * Requires a bound ShaderProgram.
	 */
	public void setUniform(int location, Color value)
	{ this.setUniform(VulkanicAPI.getCommandContext(), location, value); }

	public void setUniform(CommandContext ctx, int location, Color value)
	{
		VulkanicAPI.setUniform4f(ctx, location, 
				value.getRed()   / 256.0f, 
				value.getGreen() / 256.0f, 
				value.getBlue()  / 256.0f, 
				value.getAlpha() / 256.0f);
	}
	/** @see ShaderProgram#setUniform(int, Color) */
	public void trySetUniform(int location, Color value) { if (location != -1) { this.setUniform(location, value); } }
	public void trySetUniform(CommandContext ctx, int location, Color value) { if (location != -1) { this.setUniform(ctx, location, value); } }
	
}
