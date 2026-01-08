package com.seibel.distanthorizons.common.wrappers;

import java.nio.FloatBuffer;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.math.Mat4f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * This class converts to and from Minecraft objects (Ex: Matrix4f)
 * and objects we created (Ex: Mat4f).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class McObjectConverter
{
	private static int bufferIndex(int x, int y)
	{
		return y * 4 + x;
	}
	
	
	/** 4x4 float matrix converter */
	@Deprecated
	public static Mat4f Convert(
			org.joml.Matrix4fc 
			mcMatrix)
	{
		FloatBuffer buffer = FloatBuffer.allocate(16);
		storeMatrix(mcMatrix, buffer);
		Mat4f matrix = new Mat4f(buffer);
		return matrix;
	}
	/** Taken from Minecraft's com.mojang.math.Matrix4f class from 1.18.2 */
	private static void storeMatrix(
			org.joml.Matrix4fc 
			matrix, 
			FloatBuffer buffer)
	{
		// Mojang starts to use joml's Matrix4f libary in 1.19.3 so we copy their store method and use it here if its newer than 1.19.3
		buffer.put(bufferIndex(0, 0), matrix.m00());
		buffer.put(bufferIndex(0, 1), matrix.m01());
		buffer.put(bufferIndex(0, 2), matrix.m02());
		buffer.put(bufferIndex(0, 3), matrix.m03());
		buffer.put(bufferIndex(1, 0), matrix.m10());
		buffer.put(bufferIndex(1, 1), matrix.m11());
		buffer.put(bufferIndex(1, 2), matrix.m12());
		buffer.put(bufferIndex(1, 3), matrix.m13());
		buffer.put(bufferIndex(2, 0), matrix.m20());
		buffer.put(bufferIndex(2, 1), matrix.m21());
		buffer.put(bufferIndex(2, 2), matrix.m22());
		buffer.put(bufferIndex(2, 3), matrix.m23());
		buffer.put(bufferIndex(3, 0), matrix.m30());
		buffer.put(bufferIndex(3, 1), matrix.m31());
		buffer.put(bufferIndex(3, 2), matrix.m32());
		buffer.put(bufferIndex(3, 3), matrix.m33());
	}
	
	
	static final Direction[] directions;
	static final EDhDirection[] lodDirections;
	static
	{
		EDhDirection[] lodDirs = EDhDirection.values();
		directions = new Direction[lodDirs.length];
		lodDirections = new EDhDirection[lodDirs.length];
		for (EDhDirection lodDir : lodDirs)
		{
			Direction dir;
			switch (lodDir.name().toUpperCase())
			{
				case "DOWN":
					dir = Direction.DOWN;
					break;
				case "UP":
					dir = Direction.UP;
					break;
				case "NORTH":
					dir = Direction.NORTH;
					break;
				case "SOUTH":
					dir = Direction.SOUTH;
					break;
				case "WEST":
					dir = Direction.WEST;
					break;
				case "EAST":
					dir = Direction.EAST;
					break;
				default:
					dir = null;
					break;
			}
			
			if (dir == null)
			{
				throw new IllegalArgumentException("Invalid direction on init mapping: " + lodDir);
			}
			directions[lodDir.ordinal()] = dir;
			lodDirections[dir.ordinal()] = lodDir;
		}
	}
	
	public static BlockPos Convert(DhBlockPos wrappedPos) { return new BlockPos(wrappedPos.getX(), wrappedPos.getY(), wrappedPos.getZ()); }
	public static ChunkPos Convert(DhChunkPos wrappedPos) { return new ChunkPos(wrappedPos.getX(), wrappedPos.getZ()); }
	
	public static Direction Convert(EDhDirection lodDirection) { return directions[lodDirection.ordinal()]; }
	public static EDhDirection Convert(Direction direction) { return lodDirections[direction.ordinal()]; }
	
}
