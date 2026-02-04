package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Vec3dTest {

@Test
public void testGetManhattanDistance() {
DhApiVec3d a = new Vec3d(0, 0, 0);
DhApiVec3d b = new Vec3d(1, 1, 1);
assertEquals(3.0, Vec3d.getManhattanDistance(a, b), 0.001);
}

@Test
public void testGetDistance() {
DhApiVec3d a = new Vec3d(0, 0, 0);
DhApiVec3d b = new Vec3d(3, 4, 0);
assertEquals(5.0, Vec3d.getDistance(a, b), 0.001);
}

@Test
public void testGetSquaredDistance() {
DhApiVec3d a = new Vec3d(0, 0, 0);
DhApiVec3d b = new Vec3d(3, 4, 0);
assertEquals(25.0, Vec3d.getSquaredDistance(a, b), 0.001);
}

@Test
public void testGetHorizontalDistance() {
DhApiVec3d a = new Vec3d(0, 100, 0);
DhApiVec3d b = new Vec3d(3, 200, 4);
assertEquals(5.0, Vec3d.getHorizontalDistance(a, b), 0.001);
}

@Test
public void testInstanceMethods() {
Vec3d a = new Vec3d(0, 0, 0);
Vec3d b = new Vec3d(1, 1, 1);
assertEquals(3.0, a.getManhattanDistance(b), 0.001);
}
}
