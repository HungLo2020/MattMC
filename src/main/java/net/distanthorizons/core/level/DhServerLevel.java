package net.distanthorizons.core.level;

import net.distanthorizons.core.file.structure.ISaveStructure;
import net.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import net.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import net.distanthorizons.core.render.RenderBufferHandler;
import net.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import net.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class DhServerLevel extends AbstractDhServerLevel
{
	//=============//
	// constructor //
	//=============//
	
	public DhServerLevel(
		ISaveStructure saveStructure, 
		IServerLevelWrapper serverLevelWrapper, 
		ServerPlayerStateManager serverPlayerStateManager
		) throws SQLException, IOException
	{ super(saveStructure, serverLevelWrapper, serverPlayerStateManager); }
	
	
	
	//=======//
	// ticks //
	//=======//
	
	@Override
	public boolean shouldDoWorldGen()
	{
		return true; //todo;
	}
	@Override
	public @Nullable DhBlockPos2D getTargetPosForGeneration()
	{
		DhBlockPos2D targetPos = super.getTargetPosForGeneration();
		if (targetPos == null)
		{
			return DhBlockPos2D.ZERO;
		}
		return targetPos;
	}
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	public GenericObjectRenderer getGenericRenderer() 
	{ 
		// server-only levels don't support rendering
		return null; 
	}
	@Override
	public RenderBufferHandler getRenderBufferHandler()
	{ 
		// server-only levels don't support rendering
		return null; 
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		messageList.add("[" + this.serverLevelWrapper.getDhIdentifier() + "]");
		super.addDebugMenuStringsToList(messageList);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return "DhServerLevel{"+this.serverLevelWrapper.getKeyedLevelDimensionName()+"}"; }
	
	@Override
	public void close()
	{
		super.close();
		this.serverside.close();
	}
	
}
