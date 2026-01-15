package net.distanthorizons.core.pos.blockPos;

import net.distanthorizons.core.pos.Pos2D;
import net.distanthorizons.coreapi.util.MathUtil;

/** immutable */
public class DhBlockPos2D
{
	public static final DhBlockPos2D ZERO = new DhBlockPos2D(0, 0);
	public final int x;
	public final int z;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhBlockPos2D(int x, int z)
	{
		this.x = x;
		this.z = z;
	}
	
	public DhBlockPos2D(DhBlockPos blockPos)
	{
		this.x = blockPos.getX();
		this.z = blockPos.getZ();
	}
	
	public static DhBlockPos2D fromPos2D(Pos2D pos) { return new DhBlockPos2D(pos.getX(), pos.getY()); }
	
	
	
	//==========//
	// mutators //
	//==========//
	
	public DhBlockPos2D add(DhBlockPos2D other) { return new DhBlockPos2D(this.x + other.x, this.z + other.z); }
	
	public DhBlockPos2D add(int offsetX, int offsetZ) { return new DhBlockPos2D(this.x + offsetX, this.z + offsetZ); }
	
	public DhBlockPos2D subtract(DhBlockPos2D other) { return new DhBlockPos2D(this.x - other.x, this.z - other.z); }
	
	public Pos2D toPos2D() { return new Pos2D(this.x, this.z); }
	
	
	
	//==============//
	// calculations //
	//==============//
	
	public double dist(DhBlockPos2D other) { return this.dist(other.x, other.z); }
	public double dist(int x, int z) { return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.z - z, 2)); }
	
	public long distSquared(DhBlockPos2D other) { return this.distSquared(other.x, other.z); }
	public long distSquared(int x, int z) { return MathUtil.pow2((long) this.x - x) + MathUtil.pow2((long) this.z - z); }
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public String toString() { return "(" + this.x + ", " + this.z + ")"; }
	
	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof DhBlockPos2D)
		{
			DhBlockPos2D other = (DhBlockPos2D) obj;
			return this.x == other.x && this.z == other.z;
		}
		
		return false;
	}
	
	@Override
	public int hashCode() { return Integer.hashCode(this.x) ^ Integer.hashCode(this.z); }
	
}
