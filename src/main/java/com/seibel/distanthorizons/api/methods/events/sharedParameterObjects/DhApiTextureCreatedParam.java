package com.seibel.distanthorizons.api.methods.events.sharedParameterObjects;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/**
 * Contains information relevant to when Distant Horizons (re)creates
 * depth/color textures for rendering.
 *
 * @author James Seibel
 * @version 2025-6-9
 * @since API 4.1.0
 */
public class DhApiTextureCreatedParam implements IDhApiEventParam
{
	/** Measured in pixels */
	public final int previousWidth;
	/** Measured in pixels */
	public final int previousHeight;
	
	/** Measured in pixels */
	public final int newWidth;
	/** Measured in pixels */
	public final int newHeight;
	
	
	public DhApiTextureCreatedParam(
			int previousWidth, int previousHeight,
			int newWidth, int newHeight)
	{
		this.previousWidth = previousWidth;
		this.previousHeight = previousHeight;
		
		this.newWidth = newWidth;
		this.newHeight = newHeight;
		
	}
	
	
	@Override
	public DhApiTextureCreatedParam copy()
	{
		return new DhApiTextureCreatedParam(
				this.previousWidth, this.previousHeight,
				this.newWidth, this.newHeight
		);
	}
}
