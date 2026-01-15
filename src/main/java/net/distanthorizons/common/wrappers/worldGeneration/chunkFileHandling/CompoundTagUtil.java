package net.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

/**
 * these tag helpers are usedd to simplify tag accessing between MC versions
 */
public class CompoundTagUtil
{
	
	/** defaults to "false" if the tag isn't present */
	public static boolean getBoolean(CompoundTag tag, String key)
	{
		return tag.getBoolean(key).orElse(false);
	}
	
	/** defaults to "0" if the tag isn't present */
	public static byte getByte(CompoundTag tag, String key)
	{
		return tag.getByte(key).orElse((byte)0);
	}
	
	/** defaults to "0" if the tag isn't present */
	public static short getShort(ListTag tag, int index)
	{
		return tag.getShort(index).orElse((short)0);
	}
	
	/** defaults to "0" if the tag isn't present */
	public static int getInt(CompoundTag tag, String key)
	{
		return tag.getInt(key).orElse(0);
	}
	
	/** defaults to "0" if the tag isn't present */
	public static long getLong(CompoundTag tag, String key)
	{
		return tag.getLong(key).orElse(0L);
	}
	
	
	
	/** defaults to null if the tag isn't present */
	@Nullable
	public static String getString(CompoundTag tag, String key)
	{
		return tag.getString(key).orElse(null);
	}
	
	/** defaults to null if the tag isn't present */
	@Nullable
	public static byte[] getByteArray(CompoundTag tag, String key)
	{
		return tag.getByteArray(key).orElse(null);
	}
	
	
	
	/** defaults to null if the tag isn't present */
	@Nullable
	public static CompoundTag getCompoundTag(CompoundTag tag, String key)
	{
		return tag.getCompound(key).orElse(null);
	}
	/** defaults to null if the tag isn't present */
	@Nullable
	public static CompoundTag getCompoundTag(ListTag tag, int index)
	{
		return tag.getCompound(index).orElse(null);
	}
	
	/**
	 * defaults to null if the tag isn't present
	 * @param elementType unused after MC 1.21.5
	 */
	@Nullable
	public static ListTag getListTag(CompoundTag tag, String key, int elementType)
	{
		return tag.getList(key).orElse(null);
	}
	
	/** defaults to null if the tag isn't present */
	@Nullable
	public static ListTag getListTag(ListTag tag, int index)
	{
		return tag.getList(index).orElse(null);
	}
	
	
	
	public static boolean contains(CompoundTag tag, String key, int index)
	{
		return tag.contains(key);
	}
	
	
	
}
