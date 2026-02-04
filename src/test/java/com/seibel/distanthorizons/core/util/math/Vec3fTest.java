package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Vec3fTest {

@Test
public void testGetManhattanDistance() {
DhApiVec3f a = new Vec3f(0, 0, 0);
DhApiVec3f b = new Vec3f(1, 1, 1);
assertEquals(3.0f, Vec3f.getManhattanDistance(a, b), 0.001f);
}

@Test
public void testGetManhattanDistanceNegative() {
DhApiVec3f a = new Vec3f(-1, -1, -1);
DhApiVec3f b = new Vec3f(1, 1, 1);
assertEquals(6.0f, Vec3f.getManhattanDistance(a, b), 0.001f);
}

@Test
public void testGetDistance() {
DhApiVec3f a = new Vec3f(0, 0, 0);
DhApiVec3f b = new Vec3f(3, 4, 0);
assertEquals(5.0, Vec3f.getDistance(a, b), 0.001);
}

@Test
public void testGetDistanceIdentical() {
DhApiVec3f a = new Vec3f(5, 5, 5);
DhApiVec3f b = new Vec3f(5, 5, 5);
assertEquals(0.0, Vec3f.getDistance(a, b), 0.001);
}
}
