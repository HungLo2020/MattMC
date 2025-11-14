package mattmc.world.level;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.*;
import org.junit.jupiter.api.Test;
import java.util.Map;

public class DebugSerializationTest {
@Test
public void debug() {
LevelChunk chunk = new LevelChunk(0, 0);
chunk.setBlock(8, 64, 8, Blocks.STONE);

System.out.println("Before save:");
System.out.println("  Block at (8,64,8): " + chunk.getBlock(8, 64, 8));
System.out.println("  Identifier: " + chunk.getBlock(8, 64, 8).getIdentifier());

Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
System.out.println("\nNBT sections:");
Object sectionsObj = nbt.get("sections");
if (sectionsObj instanceof java.util.List) {
java.util.List sections = (java.util.List) sectionsObj;
System.out.println("  Number of sections: " + sections.size());
for (Object sObj : sections) {
if (sObj instanceof Map) {
Map section = (Map) sObj;
System.out.println("  Section Y=" + section.get("Y") + " has Palette=" + (section.get("Palette") != null));
}
}
}

LevelChunk loaded = ChunkNBT.fromNBT(nbt);
System.out.println("\nAfter load:");
System.out.println("  Block at (8,64,8): " + loaded.getBlock(8, 64, 8));
System.out.println("  Identifier: " + loaded.getBlock(8, 64, 8).getIdentifier());
}
}
