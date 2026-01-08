package com.seibel.distanthorizons.core.sql.dto;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import io.netty.buffer.ByteBuf;

import java.awt.*;

/** handles storing {@link FullDataSourceV2}'s in the database. */
public class BeaconBeamDTO implements IBaseDTO<DhBlockPos>, INetworkObject
{
	public DhBlockPos blockPos;
	public Color color;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public BeaconBeamDTO(DhBlockPos blockPos, Color color)
	{
		this.blockPos = blockPos;
		this.color = color;
	}
	
	
	
	//=========//
	// network //
	//=========//
	
	@Override
	public void encode(ByteBuf out)
	{
		this.blockPos.encode(out);
		out.writeInt(this.color.getRGB());
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.blockPos = INetworkObject.decodeToInstance(new DhBlockPos(), in);
		this.color = new Color(in.readInt());
	}
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public DhBlockPos getKey() { return this.blockPos; }
	
	@Override
	public void close()
	{ /* no closing needed */ }
	
	
	
}
