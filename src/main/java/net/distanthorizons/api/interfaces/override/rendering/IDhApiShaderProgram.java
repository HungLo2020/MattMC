package net.distanthorizons.api.interfaces.override.rendering;

import net.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import net.distanthorizons.api.objects.math.DhApiVec3f;

/**
 * @see IDhApiGenericObjectShaderProgram
 * 
 * @author James Seibel
 * @version 2024-1-24
 * @since API 2.0.0
 */
public interface IDhApiShaderProgram extends IDhApiOverrideable
{
	
	/**
	 * If this method is called that means this program has the highest priority as defined by {@link IDhApiOverrideable#getPriority()}
	 * and gets to decide if it wants to be used to render this frame or not. <br><br>
	 *
	 * If this method returns true then this program will be used for this frame. <br>
	 * If this returns false then the default DH {@link IDhApiShaderProgram} will be used instead.
	 */
	boolean overrideThisFrame();
	
	/** @return the OpenGL ID for this shader program */
	int getId();
	
	/** Free any OpenGL objects owned by this program. */
	void free();
	
	/** Runs any necessary binding this program needs so rendering can be done. */
	void bind();
	/** Runs any necessary unbinding this program needs so rendering can be done by another program. */
	void unbind();
	
	
	/** sets up the necessary uniforms for rendering */
	void fillUniformData(DhApiRenderParam renderParameters);
	
	/** sets the vec3 that all DH verticies should be offset by when rendering */
	void setModelOffsetPos(DhApiVec3f modelPos);
	
	/** Binds the given Vertex Buffer Object to this shader program for rendering. */
	void bindVertexBuffer(int vbo);
	
}
