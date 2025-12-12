package frnsrc.sodium;

import net.minecraft.world.level.chunk.Palette;

public interface BitStorageExtension {
    <T> void sodium$unpack(T[] out, Palette<T> palette);
}
