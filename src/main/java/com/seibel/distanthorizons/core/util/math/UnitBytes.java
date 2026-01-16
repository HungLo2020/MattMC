package com.seibel.distanthorizons.core.util.math;

import java.util.Objects;

public class UnitBytes
{
	public final long value;
	public UnitBytes(long value)
	{
		this.value = value;
	}
	public long value() { return value; }
	
	public static long byteToGB(long v)
	{
		return v / 1073741824;
	}
	public static long byteToMB(long v)
	{
		return v / 1048576;
	}
	public static long byteToKB(long v)
	{
		return v / 1024;
	}
	public static long GBToByte(long v)
	{
		return v * 1073741824;
	}
	public static long MBToByte(long v)
	{
		return v * 1048576;
	}
	public static long KBToByte(long v)
	{
		return v * 1024;
	}
	
	@Override
	public String toString()
	{
		long v = value;
		StringBuilder str = new StringBuilder();
		long GB = byteToGB(v);
		if (GB != 0) str.append(GB).append("GB ");
		v -= GBToByte(GB);
		long MB = byteToMB(v);
		if (MB != 0) str.append(MB).append("MB ");
		v -= MBToByte(MB);
		long KB = byteToKB(v);
		if (KB != 0) str.append(KB).append("KB ");
		v -= KBToByte(KB);
		str.append(v).append("B");
		return str.toString();
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		UnitBytes unitBytes = (UnitBytes) o;
		return value == unitBytes.value;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(value);
	}
	
}
