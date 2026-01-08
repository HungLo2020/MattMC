package com.seibel.distanthorizons.api.interfaces.override.rendering;

import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;

/**
 * @see IDhApiShaderProgram
 * 
 * @author James Seibel
 * @version 2024-7-11
 * @since API 3.0.0
 */
public interface IDhApiGenericObjectShaderProgram extends IDhApiOverrideable
{
	
	/**
	 * If this method is called that means this program has the highest priority as defined by {@link IDhApiOverrideable#getPriority()}
	 * and gets to decide if it wants to be used to render this frame or not. <br><br>
	 *
	 * If this method returns true then this program will be used for this frame. <br>
	 * If this returns false then the default DH {@link IDhApiGenericObjectShaderProgram} will be used instead.
	 */
	boolean overrideThisFrame();
	
	/** @return the OpenGL ID for this shader program */
	int getId();
	
	/** Free any OpenGL objects owned by this program. */
	void free();
	
	/** Runs any necessary binding this program needs so rendering can be done. */
	void bind(DhApiRenderParam renderEventParam);
	/** Runs any necessary unbinding this program needs so rendering can be done by another program. */
	void unbind();
	
	/** Binds the given Vertex Buffer Object to this shader program for rendering. */
	void bindVertexBuffer(int vbo);
	
	
	/** sets up the necessary uniforms for rendering */
	void fillIndirectUniformData(
			DhApiRenderParam renderParameters,
			DhApiRenderableBoxGroupShading shading, IDhApiRenderableBoxGroup boxGroup, 
			DhApiVec3d camPos);
	
	/** sets up the necessary uniforms for rendering */
	void fillSharedDirectUniformData(
			DhApiRenderParam renderParameters,
			DhApiRenderableBoxGroupShading shading, IDhApiRenderableBoxGroup boxGroup, 
			DhApiVec3d camPos);
	void fillDirectUniformData(
			DhApiRenderParam renderParameters,
			IDhApiRenderableBoxGroup boxGroup, DhApiRenderableBox box,
			DhApiVec3d camPos);
	
	
}
