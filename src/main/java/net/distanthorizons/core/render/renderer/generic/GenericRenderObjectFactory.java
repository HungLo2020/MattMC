package net.distanthorizons.core.render.renderer.generic;

import net.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import net.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import net.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import net.distanthorizons.api.objects.math.DhApiVec3d;
import net.distanthorizons.api.objects.render.DhApiRenderableBox;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.logging.DhLogger;

import java.util.List;
import java.util.*;

/**
 * Handles creating {@link DhApiRenderableBox}.
 * 
 * @see IDhApiCustomRenderRegister
 * @see DhApiRenderableBox
 */
public class GenericRenderObjectFactory implements IDhApiCustomRenderObjectFactory
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static final GenericRenderObjectFactory INSTANCE = new GenericRenderObjectFactory();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private GenericRenderObjectFactory() { }
	
	
	
	//================//
	// group creation //
	//================//
	
	@Override 
	public IDhApiRenderableBoxGroup createForSingleBox(String resourceLocation, DhApiRenderableBox box)
	{
		ArrayList<DhApiRenderableBox> list = new ArrayList<>();
		list.add(box);
		return this.createAbsolutePositionedGroup(resourceLocation, list);
	}
	
	@Override 
	public IDhApiRenderableBoxGroup createRelativePositionedGroup(String resourceLocation, DhApiVec3d originBlockPos, List<DhApiRenderableBox> boxList)
	{ return new RenderableBoxGroup(resourceLocation, new DhApiVec3d(originBlockPos.x, originBlockPos.y, originBlockPos.z), boxList, true); }
	
	@Override 
	public IDhApiRenderableBoxGroup createAbsolutePositionedGroup(String resourceLocation, List<DhApiRenderableBox> boxList)
	{ return new RenderableBoxGroup(resourceLocation, new DhApiVec3d(0, 0, 0), boxList, false); }
	
}
