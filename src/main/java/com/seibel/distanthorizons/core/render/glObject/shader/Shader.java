package com.seibel.distanthorizons.core.render.glObject.shader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeType;

/**
 * This object holds a OpenGL reference to a shader
 * and allows for reading in and compiling a shader file.
 */
public class Shader
{
	private static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererGLEventToFile)
			.chatLevelConfig(Config.Common.Logging.logRendererGLEventToChat)
			.build();
	
	
	/** OpenGL shader ID */
	public final int id;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/**
	 * Creates a shader with specified type.
	 *
	 * @param type Either GL_VERTEX_SHADER or GL_FRAGMENT_SHADER.
	 * @param path File path of the shader
	 * @param absoluteFilePath If false the file path is relative to the resource jar folder.
	 * @throws RuntimeException if the shader fails to compile
	 */
	public Shader(int type, String path, boolean absoluteFilePath)
	{
		this(VulkanicAPI.getCommandContext(), type, path, absoluteFilePath);
	}

	public Shader(VulkanicShaderStage stage, String path, boolean absoluteFilePath)
	{
		this(VulkanicAPI.getCommandContext(), stage, path, absoluteFilePath);
	}

	public Shader(CommandContext ctx, VulkanicShaderStage stage, String path, boolean absoluteFilePath)
	{
		this(ctx, stage.toLegacyGlShaderType(), path, absoluteFilePath);
	}

	public Shader(CommandContext ctx, int type, String path, boolean absoluteFilePath)
	{
		LOGGER.info("Loading shader at [" + path + "]");
		// Create an empty shader object
		this.id = VulkanicAPI.createShader(ctx, type);
		if (this.id == 0)
		{
			throw new IllegalArgumentException("Failed to create shader with type ["+type+"].");
		}
		
		StringBuilder source = loadFile(path, absoluteFilePath, new StringBuilder());
		safeShaderSource(ctx, this.id, source);
		
		VulkanicAPI.compileShader(ctx, this.id);
		// check if the shader compiled
		if (!VulkanicAPI.isShaderCompileSuccessful(ctx, this.id))
		{
			String message = "Shader compiler error. Details: ["+VulkanicAPI.getShaderInfoLog(ctx, this.id)+"].";
			this.free(ctx); // important!
			throw new RuntimeException(message);
		}
		LOGGER.info("Shader at " + path + " loaded successfully.");
	}
	
	public Shader(int type, String sourceString)
	{
		this(VulkanicAPI.getCommandContext(), type, sourceString);
	}

	public Shader(VulkanicShaderStage stage, String sourceString)
	{
		this(VulkanicAPI.getCommandContext(), stage, sourceString);
	}

	public Shader(CommandContext ctx, VulkanicShaderStage stage, String sourceString)
	{
		this(ctx, stage.toLegacyGlShaderType(), sourceString);
	}

	public Shader(CommandContext ctx, int type, String sourceString)
	{
		LOGGER.info("Loading shader with type: ["+type+"]");
		LOGGER.debug("Source: \n["+sourceString+"]");
		if (sourceString == null || sourceString.isEmpty())
		{
			throw new IllegalArgumentException("No shader source given.");
		}
		
		// Create an empty shader object
		this.id = VulkanicAPI.createShader(ctx, type);
		if (this.id == 0)
		{
			throw new IllegalArgumentException("Failed to create shader with type ["+type+"] and Source: \n["+sourceString+"].");
		}
		
		safeShaderSource(ctx, this.id, sourceString);
		VulkanicAPI.compileShader(ctx, this.id);
		// check if the shader compiled
		if (!VulkanicAPI.isShaderCompileSuccessful(ctx, this.id))
		{
			
			String message = "Shader compiler error. Details: [" + VulkanicAPI.getShaderInfoLog(ctx, this.id) + "]\n";
			message += "Source: \n[" + sourceString + "]";
			this.free(ctx); // important!
			throw new RuntimeException(message);
		}
		LOGGER.info("Shader loaded sucessfully.");
	}
	
	
	
	//=========//
	// helpers //
	//=========//
	
	/**
	 * Identical in function to glShaderSource but
	 * passes a null pointer for string length to force the driver to rely on the null
	 * terminator for string length.  This is a workaround for an apparent flaw with some
	 * AMD drivers that don't receive or interpret the length correctly, resulting in
	 * an access violation when the driver tries to read past the string memory.
	 *
	 * <p>Hat tip to fewizz for the find and the fix.
	 * 
	 * <p>Source: https://github.com/vram-guild/canvas/commit/820bf754092ccaf8d0c169620c2ff575722d7d96
	 */
	private static void safeShaderSource(CommandContext ctx, @NativeType("GLuint") int glId, @NativeType("GLchar const **") CharSequence source)
	{
		final MemoryStack stack = MemoryStack.stackGet();
		final int stackPointer = stack.getPointer();

		try
		{
			final ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
			final PointerBuffer pointers = stack.mallocPointer(1);
			pointers.put(sourceBuffer);

			VulkanicAPI.uploadShaderSource(ctx, glId, pointers.address0(), 1, 0);
			org.lwjgl.system.APIUtil.apiArrayFree(pointers.address0(), 1);
		}
		finally
		{
			stack.setPointer(stackPointer);
		}
	}
	
	public void free() { this.free(VulkanicAPI.getCommandContext()); }

	public void free(CommandContext ctx) { VulkanicAPI.deleteShader(ctx, this.id); }
	
	public static StringBuilder loadFile(String path, boolean absoluteFilePath, StringBuilder stringBuilder)
	{
		try
		{
			// open the file
			InputStream in;
			if (absoluteFilePath)
			{
				// Throws FileNotFoundException
				in = new FileInputStream(path); // Note: this should use OS path seperator
			}
			else
			{
				in = Shader.class.getClassLoader().getResourceAsStream(path); // Note: path seperator should be '/'
				if (in == null)
				{
					throw new FileNotFoundException("Shader file not found in resource: " + path);
				}
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			
			// read in the file
			String line;
			while ((line = reader.readLine()) != null)
			{
				stringBuilder.append(line).append("\n");
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Unable to load shader from file [" + path + "]. Error: " + e.getMessage());
		}
		return stringBuilder;
	}
	
	
	
}
