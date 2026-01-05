package net.minecraft.worldedit.math;

import net.minecraft.core.BlockPos;
import java.util.Objects;

/**
 * An immutable 3-dimensional vector with integer coordinates.
 * Used for block positions in WorldEdit.
 */
public final class BlockVector3 {
    public static final BlockVector3 ZERO = new BlockVector3(0, 0, 0);
    public static final BlockVector3 UNIT_X = new BlockVector3(1, 0, 0);
    public static final BlockVector3 UNIT_Y = new BlockVector3(0, 1, 0);
    public static final BlockVector3 UNIT_Z = new BlockVector3(0, 0, 1);
    public static final BlockVector3 ONE = new BlockVector3(1, 1, 1);

    private final int x;
    private final int y;
    private final int z;

    private BlockVector3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockVector3 at(int x, int y, int z) {
        return new BlockVector3(x, y, z);
    }

    public static BlockVector3 at(double x, double y, double z) {
        return new BlockVector3((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public static BlockVector3 from(BlockPos pos) {
        return new BlockVector3(pos.getX(), pos.getY(), pos.getZ());
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getBlockX() {
        return x;
    }

    public int getBlockY() {
        return y;
    }

    public int getBlockZ() {
        return z;
    }

    public BlockVector3 add(BlockVector3 other) {
        return new BlockVector3(x + other.x, y + other.y, z + other.z);
    }

    public BlockVector3 add(int x, int y, int z) {
        return new BlockVector3(this.x + x, this.y + y, this.z + z);
    }

    public BlockVector3 subtract(BlockVector3 other) {
        return new BlockVector3(x - other.x, y - other.y, z - other.z);
    }

    public BlockVector3 subtract(int x, int y, int z) {
        return new BlockVector3(this.x - x, this.y - y, this.z - z);
    }

    public BlockVector3 multiply(int n) {
        return new BlockVector3(x * n, y * n, z * n);
    }

    public BlockVector3 divide(int n) {
        return new BlockVector3(x / n, y / n, z / n);
    }

    public BlockVector3 abs() {
        return new BlockVector3(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    public BlockVector3 withX(int x) {
        return new BlockVector3(x, y, z);
    }

    public BlockVector3 withY(int y) {
        return new BlockVector3(x, y, z);
    }

    public BlockVector3 withZ(int z) {
        return new BlockVector3(x, y, z);
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public int lengthSq() {
        return x * x + y * y + z * z;
    }

    public double distance(BlockVector3 other) {
        return Math.sqrt(distanceSq(other));
    }

    public int distanceSq(BlockVector3 other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int dot(BlockVector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public BlockVector3 cross(BlockVector3 other) {
        return new BlockVector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public BlockVector3 getMinimum(BlockVector3 other) {
        return new BlockVector3(
            Math.min(x, other.x),
            Math.min(y, other.y),
            Math.min(z, other.z)
        );
    }

    public BlockVector3 getMaximum(BlockVector3 other) {
        return new BlockVector3(
            Math.max(x, other.x),
            Math.max(y, other.y),
            Math.max(z, other.z)
        );
    }

    public BlockVector3 clampY(int min, int max) {
        return new BlockVector3(x, Math.max(min, Math.min(max, y)), z);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockVector3 other)) return false;
        return x == other.x && y == other.y && z == other.z;
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
