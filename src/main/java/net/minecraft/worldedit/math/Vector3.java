package net.minecraft.worldedit.math;

import net.minecraft.world.phys.Vec3;
import java.util.Objects;

/**
 * An immutable 3-dimensional vector with double coordinates.
 */
public final class Vector3 {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 UNIT_X = new Vector3(1, 0, 0);
    public static final Vector3 UNIT_Y = new Vector3(0, 1, 0);
    public static final Vector3 UNIT_Z = new Vector3(0, 0, 1);
    public static final Vector3 ONE = new Vector3(1, 1, 1);

    private final double x;
    private final double y;
    private final double z;

    private Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vector3 at(double x, double y, double z) {
        return new Vector3(x, y, z);
    }

    public static Vector3 from(Vec3 vec) {
        return new Vector3(vec.x, vec.y, vec.z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 add(double x, double y, double z) {
        return new Vector3(this.x + x, this.y + y, this.z + z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 multiply(double n) {
        return new Vector3(x * n, y * n, z * n);
    }

    public Vector3 divide(double n) {
        return new Vector3(x / n, y / n, z / n);
    }

    public Vector3 abs() {
        return new Vector3(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double distance(Vector3 other) {
        return Math.sqrt(distanceSq(other));
    }

    public double distanceSq(Vector3 other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vector3 normalize() {
        double len = length();
        return len == 0 ? ZERO : divide(len);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public BlockVector3 toBlockPoint() {
        return BlockVector3.at(x, y, z);
    }

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector3 other)) return false;
        return Double.compare(other.x, x) == 0 
            && Double.compare(other.y, y) == 0 
            && Double.compare(other.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
