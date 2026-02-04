package com.seibel.distanthorizons.core.util.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UnitBytesTest {

@Test
public void testByteToKB() {
assertEquals(1, UnitBytes.byteToKB(1024));
assertEquals(10, UnitBytes.byteToKB(10240));
assertEquals(0, UnitBytes.byteToKB(512));
}

@Test
public void testByteToMB() {
assertEquals(1, UnitBytes.byteToMB(1048576));
assertEquals(10, UnitBytes.byteToMB(10485760));
assertEquals(0, UnitBytes.byteToMB(500000));
}

@Test
public void testByteToGB() {
assertEquals(1, UnitBytes.byteToGB(1073741824L));
assertEquals(10, UnitBytes.byteToGB(10737418240L));
assertEquals(0, UnitBytes.byteToGB(500000000L));
}

@Test
public void testKBToByte() {
assertEquals(1024, UnitBytes.KBToByte(1));
assertEquals(10240, UnitBytes.KBToByte(10));
}

@Test
public void testMBToByte() {
assertEquals(1048576, UnitBytes.MBToByte(1));
assertEquals(10485760, UnitBytes.MBToByte(10));
}

@Test
public void testGBToByte() {
assertEquals(1073741824L, UnitBytes.GBToByte(1));
assertEquals(10737418240L, UnitBytes.GBToByte(10));
}

@Test
public void testRoundtrip() {
long bytes = 1073741824L; // 1 GB
assertEquals(bytes, UnitBytes.GBToByte(UnitBytes.byteToGB(bytes)));

bytes = 1048576L; // 1 MB
assertEquals(bytes, UnitBytes.MBToByte(UnitBytes.byteToMB(bytes)));

bytes = 1024L; // 1 KB
assertEquals(bytes, UnitBytes.KBToByte(UnitBytes.byteToKB(bytes)));
}
}
