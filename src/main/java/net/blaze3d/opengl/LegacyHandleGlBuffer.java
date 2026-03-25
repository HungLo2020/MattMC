package net.blaze3d.opengl;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

public final class LegacyHandleGlBuffer extends GlBuffer {
    public LegacyHandleGlBuffer(@Nullable Supplier<String> label, int usage, int size, int handle) {
        super(label, new DirectStateAccess.Emulated(), usage, size, handle, null);
    }

    @Override
    public void close() {
    }
}