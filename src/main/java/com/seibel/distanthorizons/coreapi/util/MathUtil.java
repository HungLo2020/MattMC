package com.seibel.distanthorizons.coreapi.util;

public class MathUtil
{
	/**
	 * Clamps the given value between the min and max values.
	 * May behave strangely if min > max.
	 */
	public static int clamp(int min, int value, int max) { return Math.min(max, Math.max(value, min)); }
	
	/**
	 * Clamps the given value between the min and max values.
	 * May behave strangely if min > max.
	 */
	public static float clamp(float min, float value, float max) { return Math.min(max, Math.max(value, min)); }
	
	/**
	 * Clamps the given value between the min and max values.
	 * May behave strangely if min > max.
	 */
	public static double clamp(double min, double value, double max) { return Math.min(max, Math.max(value, min)); }
	
	/**
	 * Like Math.floorDiv, but reverse in that it is a ceilDiv
	 */
	public static int ceilDiv(int value, int divider) { return -Math.floorDiv(-value, divider); }
	
	// Why is this not in the standard library?! Come on Java!
	public static byte min(byte a, byte b) { return a < b ? a : b; }
	public static byte max(byte a, byte b) { return a > b ? a : b; }
	
	
	/** This is copied from Minecraft's MathHelper class */
	public static float fastInvSqrt(float numb)
	{
		float half = 0.5F * numb;
		int i = Float.floatToIntBits(numb);
		i = 1597463007 - (i >> 1);
		numb = Float.intBitsToFloat(i);
		return numb * (1.5F - half * numb * numb);
	}
	public static float pow2(float x) { return x * x; }
	public static double pow2(double x) { return x * x; }
	public static int pow2(int x) { return x * x; }
	
	public static long pow2(long x) { return x * x; }
	
	/** Equivalent to Log_2(numb) */
	public static int log2(int numb)
	{
		// properties of logs allow us to use the base Log_e() method
		return (int) (Math.log(numb) / Math.log(2));
	}
	
}
