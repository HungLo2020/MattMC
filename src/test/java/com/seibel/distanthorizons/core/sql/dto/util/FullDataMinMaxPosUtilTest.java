package com.seibel.distanthorizons.core.sql.dto.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of FullDataMinMaxPosUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class FullDataMinMaxPosUtilTest {

@Test
public void testEncodeDecodeBasic() {
short minX = 0;
short maxX = 64;
short minZ = 0;
short maxZ = 64;

long encoded = FullDataMinMaxPosUtil.encodeAdjMinMaxPos(minX, maxX, minZ, maxZ);

assertEquals(minX, FullDataMinMaxPosUtil.getAdjMinX(encoded), "MinX should decode correctly");
assertEquals(maxX, FullDataMinMaxPosUtil.getAdjMaxX(encoded), "MaxX should decode correctly");
assertEquals(minZ, FullDataMinMaxPosUtil.getAdjMinZ(encoded), "MinZ should decode correctly");
assertEquals(maxZ, FullDataMinMaxPosUtil.getAdjMaxZ(encoded), "MaxZ should decode correctly");
}

@Test
public void testEncodeDecodeZeros() {
short minX = 0;
short maxX = 0;
short minZ = 0;
short maxZ = 0;

long encoded = FullDataMinMaxPosUtil.encodeAdjMinMaxPos(minX, maxX, minZ, maxZ);

assertEquals(0L, encoded, "All zeros should encode to 0");
assertEquals(minX, FullDataMinMaxPosUtil.getAdjMinX(encoded));
assertEquals(maxX, FullDataMinMaxPosUtil.getAdjMaxX(encoded));
assertEquals(minZ, FullDataMinMaxPosUtil.getAdjMinZ(encoded));
assertEquals(maxZ, FullDataMinMaxPosUtil.getAdjMaxZ(encoded));
}

@Test
public void testEncodeDecodeMaxValues() {
short minX = Short.MAX_VALUE;
short maxX = Short.MAX_VALUE;
short minZ = Short.MAX_VALUE;
short maxZ = Short.MAX_VALUE;

long encoded = FullDataMinMaxPosUtil.encodeAdjMinMaxPos(minX, maxX, minZ, maxZ);

assertEquals(minX, FullDataMinMaxPosUtil.getAdjMinX(encoded));
assertEquals(maxX, FullDataMinMaxPosUtil.getAdjMaxX(encoded));
assertEquals(minZ, FullDataMinMaxPosUtil.getAdjMinZ(encoded));
assertEquals(maxZ, FullDataMinMaxPosUtil.getAdjMaxZ(encoded));
}

@Test
public void testEncodeDecodeMixedValues() {
short minX = 10;
short maxX = 100;
short minZ = 20;
short maxZ = 200;

long encoded = FullDataMinMaxPosUtil.encodeAdjMinMaxPos(minX, maxX, minZ, maxZ);

assertEquals(minX, FullDataMinMaxPosUtil.getAdjMinX(encoded));
assertEquals(maxX, FullDataMinMaxPosUtil.getAdjMaxX(encoded));
assertEquals(minZ, FullDataMinMaxPosUtil.getAdjMinZ(encoded));
assertEquals(maxZ, FullDataMinMaxPosUtil.getAdjMaxZ(encoded));
}

@Test
public void testEncodeDecodeEdgeCases() {
// Test with 1 values
short minX = 1;
short maxX = 1;
short minZ = 1;
short maxZ = 1;

long encoded = FullDataMinMaxPosUtil.encodeAdjMinMaxPos(minX, maxX, minZ, maxZ);

assertEquals(minX, FullDataMinMaxPosUtil.getAdjMinX(encoded));
assertEquals(maxX, FullDataMinMaxPosUtil.getAdjMaxX(encoded));
assertEquals(minZ, FullDataMinMaxPosUtil.getAdjMinZ(encoded));
assertEquals(maxZ, FullDataMinMaxPosUtil.getAdjMaxZ(encoded));
}

@Test
public void testRoundtrip() {
// Test multiple roundtrips with different values
short[][] testValues = {
{0, 16, 0, 16},
{5, 10, 15, 20},
{100, 200, 300, 400},
{1000, 2000, 3000, 4000},
{Short.MAX_VALUE - 1, Short.MAX_VALUE, Short.MAX_VALUE - 1, Short.MAX_VALUE}
};

for (short[] values : testValues) {
long encoded = FullDataMinMaxPosUtil.encodeAdjMinMaxPos(values[0], values[1], values[2], values[3]);

assertEquals(values[0], FullDataMinMaxPosUtil.getAdjMinX(encoded), "MinX roundtrip failed");
assertEquals(values[1], FullDataMinMaxPosUtil.getAdjMaxX(encoded), "MaxX roundtrip failed");
assertEquals(values[2], FullDataMinMaxPosUtil.getAdjMinZ(encoded), "MinZ roundtrip failed");
assertEquals(values[3], FullDataMinMaxPosUtil.getAdjMaxZ(encoded), "MaxZ roundtrip failed");
}
}
}
