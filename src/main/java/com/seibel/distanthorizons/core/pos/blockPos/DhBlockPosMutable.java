package com.seibel.distanthorizons.core.pos.blockPos;

import com.seibel.distanthorizons.core.enums.EDhDirection;

public class DhBlockPosMutable extends DhBlockPos
{
	/** Useful for methods that need a position passed in but won't actually be used */
	public static final DhBlockPosMutable ZERO = new DhBlockPosMutable(0, 0, 0);
	
	
	public void setX(int x) { this.x = x; }
	public void setY(int y) { this.y = y; }
	public void setZ(int z) { this.z = z; }
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhBlockPosMutable(int x, int y, int z) { super(x,y,z); }
	public DhBlockPosMutable() { super(0, 0, 0); }
	public DhBlockPosMutable(DhBlockPos pos) { super(pos); }
	
	public DhBlockPosMutable(DhBlockPos2D pos, int y) { super(pos.x, y, pos.z); }
	
	
	
	//========//
	// offset //
	//========//
	
	/** @see DhBlockPos#createOffset(EDhDirection)  */
	public DhBlockPosMutable createOffset(EDhDirection direction) { return new DhBlockPosMutable(super.mutateOrCreateOffset(direction.normal.x, direction.normal.y, direction.normal.z, null)); }
	/** @see DhBlockPos#createOffset(int, int, int)  */
	public DhBlockPosMutable createOffset(int x, int y, int z) { return new DhBlockPosMutable(this.mutateOrCreateOffset(x,y,z, null)); }
	
	
	
	//================//
	// chunk relative //
	//================//
	
	public DhBlockPosMutable createChunkRelativePos() { return new DhBlockPosMutable(this.mutateOrCreateChunkRelativePos(null)); }
	
	
}
