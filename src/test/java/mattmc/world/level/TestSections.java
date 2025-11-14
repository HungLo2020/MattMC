package mattmc.world.level;

import mattmc.world.level.chunk.*;
import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.*;

public class TestSections {
@Test
public void test() {
LevelChunk chunk = new LevelChunk(0, 0);
chunk.setBlock(8, 64, 8, Blocks.STONE);

Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
System.out.println("NBT keys: " + nbt.keySet());

Object sectionsObj = nbt.get("sections");
if (sectionsObj instanceof List) {
List sections = (List) sectionsObj;
System.out.println("Number of sections: " + sections.size());

if (sections.isEmpty()) {
System.out.println("ERROR: No sections saved!");
}
} else {
System.out.println("ERROR: sections is not a List!");
}
}
}
