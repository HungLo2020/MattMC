package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Test view of a native quad list as {@link BufferQuad} rows. */
final class NativeQuadRows {
    private NativeQuadRows() {}
    private static final EDhDirection[] DIRECTIONS = EDhDirection.values();

    static List<BufferQuad> of(NativeDhQuadBuilder owner, int layer, int direction) {
        var rows = ByteBuffer.wrap(owner.copyRows(layer, direction)).order(ByteOrder.nativeOrder());
        var quads = new ArrayList<BufferQuad>(rows.capacity() / 32);
        for (int p = 0; p < rows.capacity(); p += 32) {
            var quad = new BufferQuad(rows.getShort(p), rows.getShort(p+2), rows.getShort(p+4),
                (short)1, (short)1, rows.getInt(p+16), rows.get(p+11), rows.get(p+12), rows.get(p+13),
                DIRECTIONS[rows.get(p+10)], rows.getInt(p+20), rows.get(p+15), rows.getLong(p+24));
            quad.hasError = rows.get(p+14) != 0;
            // Frozen's compound short additions can wrap after a valid admission.
            quad.widthEastWest = rows.getShort(p+6);
            quad.widthNorthSouthOrUpDown = rows.getShort(p+8);
            quads.add(quad);
        }
        return quads;
    }

    static List<BufferQuad> of(LodQuadBuilder builder, int layer, int direction) throws ReflectiveOperationException {
        var field = LodQuadBuilder.class.getDeclaredField("nativeQuads"); field.setAccessible(true);
        return of((NativeDhQuadBuilder) field.get(builder), layer, direction);
    }
}
