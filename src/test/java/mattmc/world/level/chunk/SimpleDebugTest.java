package mattmc.world.level.chunk;

import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleDebugTest {
@Test
public void testSimpleRoundTrip() {
LevelChunk chunk = new LevelChunk(0, 0);
chunk.setBlock(8, 64, 8, Blocks.STONE);

Map<String, Object> nbt = ChunkNBT.toNBT(chunk);

// Check sections in NBT
Object sectionsObj = nbt.get("sections");
if (sectionsObj instanceof java.util.List) {
java.util.List sections = (java.util.List) sectionsObj;
System.out.println("Number of sections saved: " + sections.size());
for (Object sObj : sections) {
if (sObj instanceof Map) {
Map section = (Map) sObj;
Object yObj = section.get("Y");
Object paletteObj = section.get("Palette");
System.out.println("  Section Y=" + yObj + " has Palette=" + (paletteObj != null));
}
}
}

LevelChunk loaded = ChunkNBT.fromNBT(nbt);
Object block = loaded.getBlock(8, 64, 8);
System.out.println("Loaded block: " + block);
System.out.println("Is STONE: " + (block == Blocks.STONE));

assertSame(Blocks.STONE, block);
}
}
