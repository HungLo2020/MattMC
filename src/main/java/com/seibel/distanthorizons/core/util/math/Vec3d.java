package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * This is closer to MC's implementation of a
 * 3 element float vector than a 3 element double
 * vector. Hopefully that shouldn't cause any issues.
 *
 * Static distance calculation methods now use Rust FFM for performance.
 *
 * @author James Seibel
 * @version 2024-02-04 (Rust FFM migration for static distance methods)
 */
public class Vec3d extends DhApiVec3d
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle getManhattanDistanceHandle;
	private static final MethodHandle getDistanceHandle;
	private static final MethodHandle getSquaredDistanceHandle;
	private static final MethodHandle getHorizontalDistanceHandle;
	
	static {
		try {
			// Determine the platform-specific library name
			String osName = System.getProperty("os.name").toLowerCase();
			String libraryName;
			
			if (osName.contains("win")) {
				libraryName = "mattmc_native.dll";
			} else if (osName.contains("mac")) {
				libraryName = "libmattmc_native.dylib";
			} else {
				libraryName = "libmattmc_native.so";
			}
			
			// Load library from the JAR's native resources
			Path libraryPath = NativeLibraryLoader.loadLibraryFromJar(libraryName);
			
			// Load the library
			SymbolLookup lib = SymbolLookup.libraryLookup(libraryPath, Arena.global());
			LIBRARY = lib;
			
			// Initialize function handles
			getManhattanDistanceHandle = LINKER.downcallHandle(
				findFunction("vec3d_get_manhattan_distance"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, 
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			getDistanceHandle = LINKER.downcallHandle(
				findFunction("vec3d_get_distance"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, 
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			getSquaredDistanceHandle = LINKER.downcallHandle(
				findFunction("vec3d_get_squared_distance"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, 
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			getHorizontalDistanceHandle = LINKER.downcallHandle(
				findFunction("vec3d_get_horizontal_distance"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, 
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for Vec3d: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for Vec3d!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	public static Vec3d XNeg = new Vec3d(-1.0F, 0.0F, 0.0F);
	public static Vec3d XPos = new Vec3d(1.0F, 0.0F, 0.0F);
	public static Vec3d YNeg = new Vec3d(0.0F, -1.0F, 0.0F);
	public static Vec3d YPos = new Vec3d(0.0F, 1.0F, 0.0F);
	public static Vec3d ZNeg = new Vec3d(0.0F, 0.0F, -1.0F);
	public static Vec3d ZPos = new Vec3d(0.0F, 0.0F, 1.0F);
	
	public static final Vec3d ZERO_VECTOR = new Vec3d(0.0D, 0.0D, 0.0D);
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public Vec3d() { }
	
	public Vec3d(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vec3d(DhApiVec3d that)
	{
		this.x = that.x;
		this.y = that.y;
		this.z = that.z;
	}
	
	public Vec3d(double[] values) { this.set(values); }
	
	
	public Vec3d copy() { return new Vec3d(this); }
	
	
	
	//=========//
	// methods //
	//=========//
	
	public void multiply(double scalar)
	{
		this.x *= scalar;
		this.y *= scalar;
		this.z *= scalar;
	}
	
	public void multiply(double x, double y, double z)
	{
		this.x *= x;
		this.y *= y;
		this.z *= z;
	}
	
	public void clamp(double min, double max)
	{
		this.x = MathUtil.clamp(min, this.x, max);
		this.y = MathUtil.clamp(min, this.y, max);
		this.z = MathUtil.clamp(min, this.z, max);
	}
	
	public void set(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public void add(double x, double y, double z)
	{
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public void add(Vec3d vector)
	{
		this.x += vector.x;
		this.y += vector.y;
		this.z += vector.z;
	}
	
	public void subtract(Vec3d vector)
	{
		this.x -= vector.x;
		this.y -= vector.y;
		this.z -= vector.z;
	}
	
	public double dotProduct(Vec3d vector) { return this.x * vector.x + this.y * vector.y + this.z * vector.z; }
	
	public Vec3d normalize()
	{
		double value = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
		return value < 1.0E-4D ? ZERO_VECTOR : new Vec3d(this.x / value, this.y / value, this.z / value);
	}
	
	public void crossProduct(Vec3d vector)
	{
		double f = this.x;
		double f1 = this.y;
		double f2 = this.z;
		double f3 = vector.x;
		double f4 = vector.y;
		double f5 = vector.z;
		this.x = f1 * f5 - f2 * f4;
		this.y = f2 * f3 - f * f5;
		this.z = f * f4 - f1 * f3;
	}
	
	public void set(double[] values)
	{
		this.x = values[0];
		this.y = values[1];
		this.z = values[2];
	}
	
	public double getManhattanDistance(DhApiVec3d other) { return getManhattanDistance(this, other); }
	public static double getManhattanDistance(DhApiVec3d a, DhApiVec3d b)
	{
		try {
			return (double) getManhattanDistanceHandle.invokeExact(a.x, a.y, a.z, b.x, b.y, b.z);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getManhattanDistance", e);
		}
	}
	
	public double getDistance(DhApiVec3d other) { return getDistance(this, other); }
	public static double getDistance(DhApiVec3d a, DhApiVec3d b)
	{
		try {
			return (double) getDistanceHandle.invokeExact(a.x, a.y, a.z, b.x, b.y, b.z);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getDistance", e);
		}
	}
	
	/** @see Vec3d#getSquaredDistance(DhApiVec3d, DhApiVec3d)  */
	public double getSquaredDistance(DhApiVec3d other) { return getSquaredDistance(this, other); }
	/** slightly faster version of {@link Vec3d#getDistance} */
	public static double getSquaredDistance(DhApiVec3d a, DhApiVec3d b)
	{
		try {
			return (double) getSquaredDistanceHandle.invokeExact(a.x, a.y, a.z, b.x, b.y, b.z);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getSquaredDistance", e);
		}
	}
	
	/** @see Vec3d#getHorizontalDistance(DhApiVec3d, DhApiVec3d)  */
	public double getHorizontalDistance(DhApiVec3d other) { return getHorizontalDistance(this, other); }
	/** Gets the distance between points A and B, ignoring Y height. */
	public static double getHorizontalDistance(DhApiVec3d a, DhApiVec3d b)
	{
		try {
			return (double) getHorizontalDistanceHandle.invokeExact(a.x, a.y, a.z, b.x, b.y, b.z);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getHorizontalDistance", e);
		}
	}
	
}
