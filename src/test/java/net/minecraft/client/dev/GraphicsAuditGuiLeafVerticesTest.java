package net.minecraft.client.dev;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

class GraphicsAuditGuiLeafVerticesTest {
    @Test void disabledOrUnscopedNormalTraceDoesNotTouchVertices() {
        String key="mattmc.dev.guiLeafNormalTrace";
        String old=System.getProperty(key);
        try {
            System.clearProperty(key);
            assertDoesNotThrow(() -> GraphicsAuditGuiLeafVertices.recordNormal(null,0,0,0,0,"test"));
            System.setProperty(key,"true");
            GraphicsAuditGuiFoilTiming.endItem();
            assertDoesNotThrow(() -> GraphicsAuditGuiLeafVertices.recordNormal(null,0,0,0,0,"test"));
        } finally {
            if(old==null) System.clearProperty(key); else System.setProperty(key,old);
        }
    }
    @Test void disabledOrUnscopedTraceDoesNotTouchVertices() {
        String key="mattmc.dev.guiLeafVertexTrace";
        String old=System.getProperty(key);
        try {
            System.clearProperty(key);
            assertDoesNotThrow(() -> GraphicsAuditGuiLeafVertices.record(null,0,null,0,0));
            System.setProperty(key,"true");
            GraphicsAuditGuiFoilTiming.endItem();
            assertDoesNotThrow(() -> GraphicsAuditGuiLeafVertices.record(null,0,null,0,0));
        } finally {
            if(old==null) System.clearProperty(key); else System.setProperty(key,old);
        }
    }
}
