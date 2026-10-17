package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLightQueuesTest {
    @Test void growthWrappingAndSignedEntryBitsMatchThePreviousQueue() {
        var nativeQueues = new NativeLightQueues();
        var previous = new LongArrayFIFOQueue[]{new LongArrayFIFOQueue(), new LongArrayFIFOQueue()};
        var random = new Random(0x4c49474854574f52L);
        for (int i = 0; i < 20000; i++) {
            int side = random.nextInt(2);
            if (i < 4096 || previous[side].isEmpty() || random.nextBoolean()) {
                long position = random.nextLong(), entry = random.nextLong();
                previous[side].enqueue(position); previous[side].enqueue(entry);
                nativeQueues.enqueue(side, position, entry);
            } else {
                assertEquals(previous[side].dequeueLong(), nativeQueues.dequeue(side));
                assertEquals(previous[side].dequeueLong(), nativeQueues.dequeue(side));
            }
            assertEquals(previous[side].size(), nativeQueues.size(side));
            assertEquals(previous[side].isEmpty(), nativeQueues.isEmpty(side));
        }
        for (int side = 0; side < 2; side++) {
            while (!previous[side].isEmpty()) assertEquals(previous[side].dequeueLong(), nativeQueues.dequeue(side));
            int queueSide = side;
            assertTrue(nativeQueues.isEmpty(side));
            assertThrows(NoSuchElementException.class, () -> nativeQueues.dequeue(queueSide));
        }
    }
}
